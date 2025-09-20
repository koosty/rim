package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.ActorMessage;
import io.github.koosty.xmpp.actor.message.RouteMessageRequest;
import io.github.koosty.xmpp.actor.message.RouteMessageResponse;
import io.github.koosty.xmpp.actor.message.GenericActorMessage;
import io.github.koosty.xmpp.stanza.MessageStanza;
import io.github.koosty.xmpp.connection.XmppConnection;
import io.github.koosty.xmpp.jid.JidValidator;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Actor responsible for routing message stanzas between connections.
 * Implements inter-connection message routing with addressing logic.
 */
public class MessageRoutingActor extends AbstractActor {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageRoutingActor.class);
    
    private final Map<String, XmppConnection> connectionsByJid = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> resourcesByBareJid = new ConcurrentHashMap<>();
    private final JidValidator jidValidator;
    
    public MessageRoutingActor(String actorId, JidValidator jidValidator) {
        super(actorId);
        this.jidValidator = jidValidator;
    }
    
    @Override
    protected Mono<ActorMessage> processMessage(ActorMessage message) {
        return switch (message.getType()) {
            case ROUTE_MESSAGE -> handleRouteMessage((RouteMessageRequest) message);
            case REGISTER_CONNECTION -> handleRegisterConnection(message);
            case UNREGISTER_CONNECTION -> handleUnregisterConnection(message);
            case GET_CONNECTION_COUNT -> handleGetConnectionCount();
            default -> Mono.error(new IllegalArgumentException("Unknown message type: " + message.getType()));
        };
    }
    
    /**
     * Route message stanza to target connection(s)
     */
    private Mono<ActorMessage> handleRouteMessage(RouteMessageRequest request) {
        MessageStanza stanza = request.stanza();
        String targetJid = stanza.to();
        
        if (targetJid == null || targetJid.isEmpty()) {
            logger.warn("Message routing failed: no target JID specified");
            return Mono.just(new RouteMessageResponse(false, "No target JID specified", null));
        }
        
        return validateAndRouteMessage(stanza, targetJid)
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(Duration.ofSeconds(5))
            .onErrorResume(error -> {
                logger.error("Message routing error: {}", error.getMessage());
                return Mono.just(new RouteMessageResponse(false, error.getMessage(), null));
            });
    }
    
    /**
     * Validate JID and route message
     */
    private Mono<ActorMessage> validateAndRouteMessage(MessageStanza stanza, String targetJid) {
        return Mono.fromCallable(() -> jidValidator.parseJid(targetJid).isPresent())
            .filter(isValid -> isValid)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid target JID: " + targetJid)))
            .flatMap(valid -> routeToConnection(stanza, targetJid));
    }
    
    /**
     * Route message to specific connection or connections
     */
    private Mono<ActorMessage> routeToConnection(MessageStanza stanza, String targetJid) {
        // Try exact JID match first (full JID with resource)
        XmppConnection exactConnection = connectionsByJid.get(targetJid);
        if (exactConnection != null) {
            return deliverToConnection(stanza, exactConnection, targetJid);
        }
        
        // If no exact match, try bare JID routing (without resource)
        String bareJid = extractBareJid(targetJid);
        Set<String> resources = resourcesByBareJid.get(bareJid);
        
        if (resources == null || resources.isEmpty()) {
            logger.warn("No connection found for JID: {}", targetJid);
            return Mono.just(new RouteMessageResponse(false, "No connection found", targetJid));
        }
        
        // Route to highest priority resource or all resources based on message type
        if (stanza.isChatMessage()) {
            return routeToHighestPriorityResource(stanza, bareJid, resources);
        } else {
            return routeToAllResources(stanza, bareJid, resources);
        }
    }
    
    /**
     * Route to highest priority resource for chat messages
     */
    private Mono<ActorMessage> routeToHighestPriorityResource(MessageStanza stanza, String bareJid, Set<String> resources) {
        // For simplicity, route to first available resource
        // In production, this would consider presence priority
        String firstResource = resources.iterator().next();
        String fullJid = bareJid + "/" + firstResource;
        XmppConnection connection = connectionsByJid.get(fullJid);
        
        if (connection != null) {
            return deliverToConnection(stanza, connection, fullJid);
        } else {
            return Mono.just(new RouteMessageResponse(false, "Connection not found for resource", fullJid));
        }
    }
    
    /**
     * Route to all resources for broadcast messages
     */
    private Mono<ActorMessage> routeToAllResources(MessageStanza stanza, String bareJid, Set<String> resources) {
        return Flux.fromIterable(resources)
            .map(resource -> bareJid + "/" + resource)
            .mapNotNull(connectionsByJid::get)
            .flatMap(connection -> deliverToConnectionFlux(stanza, connection))
            .count()
            .map(deliveredCount -> new RouteMessageResponse(true, 
                "Delivered to " + deliveredCount + " resources", bareJid));
    }
    
    /**
     * Deliver message to specific connection
     */
    private Mono<ActorMessage> deliverToConnection(MessageStanza stanza, XmppConnection connection, String jid) {
        return connection.sendStanza(stanza)
            .map(sent -> {
                if (sent) {
                    logger.debug("Message delivered to {}", jid);
                    return (ActorMessage) new RouteMessageResponse(true, "Message delivered", jid);
                } else {
                    logger.warn("Failed to deliver message to {}", jid);
                    return (ActorMessage) new RouteMessageResponse(false, "Delivery failed", jid);
                }
            })
            .onErrorResume(error -> {
                logger.error("Error delivering message to {}: {}", jid, error.getMessage());
                return Mono.just((ActorMessage) new RouteMessageResponse(false, "Delivery error: " + error.getMessage(), jid));
            });
    }
    
    /**
     * Helper for Flux-based delivery
     */
    private Mono<Boolean> deliverToConnectionFlux(MessageStanza stanza, XmppConnection connection) {
        return connection.sendStanza(stanza);
    }
    
    /**
     * Register connection for message routing
     */
    private Mono<ActorMessage> handleRegisterConnection(ActorMessage message) {
        GenericActorMessage genericMessage = (GenericActorMessage) message;
        String jid = genericMessage.getPayload("jid", String.class);
        XmppConnection connection = genericMessage.getPayload("connection", XmppConnection.class);
        
        if (jid == null || connection == null) {
            return Mono.just(createResponse("register-connection-response", false, "Invalid parameters"));
        }
        
        connectionsByJid.put(jid, connection);
        
        // Update resource tracking
        String bareJid = extractBareJid(jid);
        String resource = extractResource(jid);
        if (resource != null) {
            resourcesByBareJid.computeIfAbsent(bareJid, k -> new ConcurrentSkipListSet<>()).add(resource);
        }
        
        logger.info("Registered connection for JID: {}", jid);
        return Mono.just(createResponse("register-connection-response", true, "Connection registered"));
    }
    
    /**
     * Unregister connection from message routing
     */
    private Mono<ActorMessage> handleUnregisterConnection(ActorMessage message) {
        GenericActorMessage genericMessage = (GenericActorMessage) message;
        String jid = genericMessage.getPayload("jid", String.class);
        
        if (jid == null) {
            return Mono.just(createResponse("unregister-connection-response", false, "No JID specified"));
        }
        
        XmppConnection removed = connectionsByJid.remove(jid);
        
        // Update resource tracking
        String bareJid = extractBareJid(jid);
        String resource = extractResource(jid);
        if (resource != null) {
            Set<String> resources = resourcesByBareJid.get(bareJid);
            if (resources != null) {
                resources.remove(resource);
                if (resources.isEmpty()) {
                    resourcesByBareJid.remove(bareJid);
                }
            }
        }
        
        logger.info("Unregistered connection for JID: {}", jid);
        return Mono.just(createResponse("unregister-connection-response", removed != null, 
            removed != null ? "Connection unregistered" : "Connection not found"));
    }
    
    /**
     * Get connection count for monitoring
     */
    private Mono<ActorMessage> handleGetConnectionCount() {
        int count = connectionsByJid.size();
        return Mono.just(createResponse("connection-count-response", true, 
            "Active connections: " + count, Map.of("count", count)));
    }
    
    /**
     * Extract bare JID (without resource)
     */
    private String extractBareJid(String fullJid) {
        int resourceIndex = fullJid.indexOf('/');
        return resourceIndex > 0 ? fullJid.substring(0, resourceIndex) : fullJid;
    }
    
    /**
     * Extract resource from full JID
     */
    private String extractResource(String fullJid) {
        int resourceIndex = fullJid.indexOf('/');
        return resourceIndex > 0 && resourceIndex < fullJid.length() - 1 ? 
            fullJid.substring(resourceIndex + 1) : null;
    }
}