package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.ActorMessage;
import io.github.koosty.xmpp.actor.message.GenericActorMessage;
import io.github.koosty.xmpp.stanza.PresenceStanza;
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
 * Actor responsible for presence management and subscription handling.
 * Manages presence state isolation and subscription workflows per RFC6120.
 */
public class PresenceActor extends AbstractActor {
    
    private static final Logger logger = LoggerFactory.getLogger(PresenceActor.class);
    
    private final Map<String, PresenceStanza> presenceCache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, XmppConnection> connectionsByJid = new ConcurrentHashMap<>();
    private final JidValidator jidValidator;
    
    public PresenceActor(String actorId, JidValidator jidValidator) {
        super(actorId);
        this.jidValidator = jidValidator;
    }
    
    @Override
    protected Mono<ActorMessage> processMessage(ActorMessage message) {
        return switch (message.getType()) {
            case PRESENCE_UPDATE -> handlePresenceUpdate(message);
            case ROUTE_MESSAGE -> handlePresenceStanza((GenericActorMessage) message);
            case REGISTER_CONNECTION -> handleRegisterConnection(message);
            case UNREGISTER_CONNECTION -> handleUnregisterConnection(message);
            case GET_CONNECTION_COUNT -> handleGetPresenceInfo();
            default -> Mono.error(new IllegalArgumentException("Unknown message type: " + message.getType()));
        };
    }
    
    /**
     * Handle presence update from connection
     */
    private Mono<ActorMessage> handlePresenceUpdate(ActorMessage message) {
        GenericActorMessage genericMessage = (GenericActorMessage) message;
        String jid = genericMessage.getPayload("jid", String.class);
        PresenceStanza presence = genericMessage.getPayload("presence", PresenceStanza.class);
        
        if (jid == null || presence == null) {
            logger.warn("Invalid presence update: missing JID or presence data");
            return Mono.just(createResponse("presence-update-response", false, "Invalid parameters"));
        }
        
        return processPresenceUpdate(jid, presence)
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(Duration.ofSeconds(5))
            .onErrorResume(error -> {
                logger.error("Presence update error for {}: {}", jid, error.getMessage());
                return Mono.just(createResponse("presence-update-response", false, error.getMessage()));
            });
    }
    
    /**
     * Process presence stanza routing
     */
    private Mono<ActorMessage> handlePresenceStanza(GenericActorMessage message) {
        PresenceStanza stanza = message.getPayload("stanza", PresenceStanza.class);
        String sourceJid = message.getPayload("sourceJid", String.class);
        
        if (stanza == null) {
            return Mono.just(createResponse("presence-stanza-response", false, "No presence stanza provided"));
        }
        
        return routePresenceStanza(stanza, sourceJid);
    }
    
    /**
     * Process individual presence update
     */
    private Mono<ActorMessage> processPresenceUpdate(String jid, PresenceStanza presence) {
        return Mono.fromCallable(() -> {
            String bareJid = extractBareJid(jid);
            
            if (presence.isAvailable() || presence.isUnavailable()) {
                // Update presence cache
                if (presence.isAvailable()) {
                    presenceCache.put(jid, presence);
                } else {
                    presenceCache.remove(jid);
                }
                
                // Broadcast to subscribers
                return broadcastPresenceToSubscribers(bareJid, presence);
            } else if (presence.isSubscribe()) {
                return handleSubscriptionRequest(bareJid, presence.from());
            } else if (presence.isUnsubscribe()) {
                return handleUnsubscriptionRequest(bareJid, presence.from());
            } else if (presence.isSubscribed()) {
                return handleSubscriptionApproval(bareJid, presence.from());
            } else if (presence.isUnsubscribed()) {
                return handleSubscriptionDenial(bareJid, presence.from());
            } else if (presence.isProbe()) {
                return handlePresenceProbe(presence.from(), bareJid);
            }
            
            return createResponse("presence-update-response", true, "Presence processed");
        })
        .flatMap(result -> Mono.just(result));
    }
    
    /**
     * Route presence stanza to appropriate handler
     */
    private Mono<ActorMessage> routePresenceStanza(PresenceStanza stanza, String sourceJid) {
        if (stanza.to() == null) {
            // Broadcast presence (no specific target)
            return broadcastPresence(sourceJid, stanza);
        } else {
            // Directed presence
            return deliverDirectedPresence(stanza, sourceJid);
        }
    }
    
    /**
     * Broadcast presence to all subscribers
     */
    private Mono<ActorMessage> broadcastPresence(String fromJid, PresenceStanza presence) {
        String bareFromJid = extractBareJid(fromJid);
        Set<String> subscriberSet = subscribers.get(bareFromJid);
        
        if (subscriberSet == null || subscriberSet.isEmpty()) {
            logger.debug("No subscribers for presence broadcast from {}", bareFromJid);
            return Mono.just(createResponse("presence-broadcast-response", true, "No subscribers"));
        }
        
        return Flux.fromIterable(subscriberSet)
            .flatMap(subscriberJid -> deliverPresenceToSubscriber(subscriberJid, presence))
            .count()
            .map(deliveredCount -> createResponse("presence-broadcast-response", true,
                "Broadcasted to " + deliveredCount + " subscribers"));
    }
    
    /**
     * Deliver directed presence to specific JID
     */
    private Mono<ActorMessage> deliverDirectedPresence(PresenceStanza stanza, String sourceJid) {
        String targetJid = stanza.to();
        XmppConnection connection = connectionsByJid.get(targetJid);
        
        if (connection == null) {
            // Try bare JID if full JID not found
            String bareTargetJid = extractBareJid(targetJid);
            connection = findConnectionForBareJid(bareTargetJid);
        }
        
        if (connection == null) {
            logger.warn("No connection found for directed presence to {}", targetJid);
            return Mono.just(createResponse("directed-presence-response", false, "No connection found"));
        }
        
        return connection.sendStanza(stanza)
            .map(sent -> createResponse("directed-presence-response", sent,
                sent ? "Directed presence delivered" : "Delivery failed"));
    }
    
    /**
     * Handle subscription request (subscribe)
     */
    private ActorMessage handleSubscriptionRequest(String toJid, String fromJid) {
        if (fromJid == null) {
            return createResponse("subscription-response", false, "Invalid from JID");
        }
        
        String bareFromJid = extractBareJid(fromJid);
        String bareToJid = extractBareJid(toJid);
        
        // Add to pending subscriptions (in real implementation, would check roster)
        subscriptions.computeIfAbsent(bareFromJid, k -> new ConcurrentSkipListSet<>()).add(bareToJid);
        
        // Forward subscription request to target
        XmppConnection targetConnection = findConnectionForBareJid(bareToJid);
        if (targetConnection != null) {
            PresenceStanza subscribeStanza = new PresenceStanza(null, bareFromJid, bareToJid, "subscribe", null, null, 0, null);
            targetConnection.sendStanza(subscribeStanza).subscribe();
        }
        
        logger.info("Subscription request from {} to {}", bareFromJid, bareToJid);
        return createResponse("subscription-response", true, "Subscription request processed");
    }
    
    /**
     * Handle subscription approval (subscribed)
     */
    private ActorMessage handleSubscriptionApproval(String toJid, String fromJid) {
        if (fromJid == null) {
            return createResponse("subscription-response", false, "Invalid from JID");
        }
        
        String bareFromJid = extractBareJid(fromJid);
        String bareToJid = extractBareJid(toJid);
        
        // Add to subscribers list
        subscribers.computeIfAbsent(bareToJid, k -> new ConcurrentSkipListSet<>()).add(bareFromJid);
        
        // Send current presence to new subscriber
        PresenceStanza currentPresence = findCurrentPresence(bareToJid);
        if (currentPresence != null) {
            XmppConnection subscriberConnection = findConnectionForBareJid(bareFromJid);
            if (subscriberConnection != null) {
                subscriberConnection.sendStanza(currentPresence).subscribe();
            }
        }
        
        logger.info("Subscription approved: {} subscribed to {}", bareFromJid, bareToJid);
        return createResponse("subscription-response", true, "Subscription approved");
    }
    
    /**
     * Handle unsubscription request (unsubscribe)
     */
    private ActorMessage handleUnsubscriptionRequest(String toJid, String fromJid) {
        if (fromJid == null) {
            return createResponse("subscription-response", false, "Invalid from JID");
        }
        
        String bareFromJid = extractBareJid(fromJid);
        String bareToJid = extractBareJid(toJid);
        
        // Remove subscription
        Set<String> userSubscriptions = subscriptions.get(bareFromJid);
        if (userSubscriptions != null) {
            userSubscriptions.remove(bareToJid);
            if (userSubscriptions.isEmpty()) {
                subscriptions.remove(bareFromJid);
            }
        }
        
        logger.info("Unsubscription request from {} to {}", bareFromJid, bareToJid);
        return createResponse("subscription-response", true, "Unsubscription processed");
    }
    
    /**
     * Handle subscription denial (unsubscribed)
     */
    private ActorMessage handleSubscriptionDenial(String toJid, String fromJid) {
        if (fromJid == null) {
            return createResponse("subscription-response", false, "Invalid from JID");
        }
        
        String bareFromJid = extractBareJid(fromJid);
        String bareToJid = extractBareJid(toJid);
        
        // Remove from subscribers
        Set<String> userSubscribers = subscribers.get(bareToJid);
        if (userSubscribers != null) {
            userSubscribers.remove(bareFromJid);
            if (userSubscribers.isEmpty()) {
                subscribers.remove(bareToJid);
            }
        }
        
        logger.info("Subscription denied: {} unsubscribed from {}", bareFromJid, bareToJid);
        return createResponse("subscription-response", true, "Subscription denied");
    }
    
    /**
     * Handle presence probe
     */
    private ActorMessage handlePresenceProbe(String fromJid, String toJid) {
        if (fromJid == null) {
            return createResponse("presence-probe-response", false, "Invalid from JID");
        }
        
        String bareFromJid = extractBareJid(fromJid);
        String bareToJid = extractBareJid(toJid);
        
        // Check if probe is authorized (subscriber)
        Set<String> userSubscribers = subscribers.get(bareToJid);
        if (userSubscribers == null || !userSubscribers.contains(bareFromJid)) {
            logger.warn("Unauthorized presence probe from {} to {}", bareFromJid, bareToJid);
            return createResponse("presence-probe-response", false, "Unauthorized probe");
        }
        
        // Send current presence
        PresenceStanza currentPresence = findCurrentPresence(bareToJid);
        if (currentPresence != null) {
            XmppConnection proberConnection = findConnectionForBareJid(bareFromJid);
            if (proberConnection != null) {
                proberConnection.sendStanza(currentPresence).subscribe();
                return createResponse("presence-probe-response", true, "Presence sent");
            }
        }
        
        return createResponse("presence-probe-response", false, "No presence available");
    }
    
    /**
     * Broadcast presence to subscribers
     */
    private ActorMessage broadcastPresenceToSubscribers(String bareJid, PresenceStanza presence) {
        Set<String> subscriberSet = subscribers.get(bareJid);
        
        if (subscriberSet == null || subscriberSet.isEmpty()) {
            return createResponse("presence-broadcast-response", true, "No subscribers");
        }
        
        int delivered = 0;
        for (String subscriberJid : subscriberSet) {
            XmppConnection connection = findConnectionForBareJid(subscriberJid);
            if (connection != null) {
                connection.sendStanza(presence).subscribe();
                delivered++;
            }
        }
        
        logger.debug("Broadcasted presence from {} to {} subscribers", bareJid, delivered);
        return createResponse("presence-broadcast-response", true, "Broadcasted to " + delivered + " subscribers");
    }
    
    /**
     * Deliver presence to specific subscriber
     */
    private Mono<Boolean> deliverPresenceToSubscriber(String subscriberJid, PresenceStanza presence) {
        XmppConnection connection = findConnectionForBareJid(subscriberJid);
        if (connection != null) {
            return connection.sendStanza(presence);
        }
        return Mono.just(false);
    }
    
    /**
     * Register connection for presence delivery
     */
    private Mono<ActorMessage> handleRegisterConnection(ActorMessage message) {
        GenericActorMessage genericMessage = (GenericActorMessage) message;
        String jid = genericMessage.getPayload("jid", String.class);
        XmppConnection connection = genericMessage.getPayload("connection", XmppConnection.class);
        
        if (jid == null || connection == null) {
            return Mono.just(createResponse("register-connection-response", false, "Invalid parameters"));
        }
        
        connectionsByJid.put(jid, connection);
        logger.info("Registered connection for presence delivery: {}", jid);
        return Mono.just(createResponse("register-connection-response", true, "Connection registered"));
    }
    
    /**
     * Unregister connection from presence delivery
     */
    private Mono<ActorMessage> handleUnregisterConnection(ActorMessage message) {
        GenericActorMessage genericMessage = (GenericActorMessage) message;
        String jid = genericMessage.getPayload("jid", String.class);
        
        if (jid == null) {
            return Mono.just(createResponse("unregister-connection-response", false, "No JID specified"));
        }
        
        connectionsByJid.remove(jid);
        presenceCache.remove(jid);
        logger.info("Unregistered connection for presence: {}", jid);
        return Mono.just(createResponse("unregister-connection-response", true, "Connection unregistered"));
    }
    
    /**
     * Get presence information for monitoring
     */
    private Mono<ActorMessage> handleGetPresenceInfo() {
        Map<String, Object> info = Map.of(
            "activePresences", presenceCache.size(),
            "subscriptions", subscriptions.size(),
            "subscribers", subscribers.size(),
            "connections", connectionsByJid.size()
        );
        
        return Mono.just(createResponse("presence-info-response", true, 
            "Presence info retrieved", info));
    }
    
    /**
     * Find connection for bare JID (any resource)
     */
    private XmppConnection findConnectionForBareJid(String bareJid) {
        return connectionsByJid.entrySet().stream()
            .filter(entry -> extractBareJid(entry.getKey()).equals(bareJid))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Find current presence for JID
     */
    private PresenceStanza findCurrentPresence(String bareJid) {
        return presenceCache.entrySet().stream()
            .filter(entry -> extractBareJid(entry.getKey()).equals(bareJid))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Extract bare JID (without resource)
     */
    private String extractBareJid(String fullJid) {
        if (fullJid == null) return null;
        int resourceIndex = fullJid.indexOf('/');
        return resourceIndex > 0 ? fullJid.substring(0, resourceIndex) : fullJid;
    }
}