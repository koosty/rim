package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.ActorMessage;
import io.github.koosty.xmpp.actor.message.OutgoingStanzaMessage;
import io.github.koosty.xmpp.config.XmppSecurityProperties;
import io.github.koosty.xmpp.stream.XmlStreamProcessor;
import io.github.koosty.xmpp.features.StreamFeaturesManager;
import io.github.koosty.xmpp.service.TlsNegotiationService;
import io.github.koosty.xmpp.service.SaslAuthenticationService;
import io.github.koosty.xmpp.service.ResourceBindingService;
import io.github.koosty.xmpp.service.IqProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.netty.NettyOutbound;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Actor system for managing XMPP connection actors and message routing.
 * Provides actor lifecycle management and inter-actor communication.
 */
@Component
public class ActorSystem {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorSystem.class);
    
    private final ConcurrentMap<String, ConnectionActor> connectionActors = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, NettyOutbound> outboundConnections = new ConcurrentHashMap<>();
    private final XmlStreamProcessor xmlProcessor;
    private final StreamFeaturesManager featuresManager;
    private final XmppSecurityProperties securityProperties;
    
    // Injected services for actor creation
    private final TlsNegotiationService tlsNegotiationService;
    private final SaslAuthenticationService saslAuthenticationService;
    private final ResourceBindingService resourceBindingService;
    private final IqProcessingService iqProcessingService;
    
    public ActorSystem(XmlStreamProcessor xmlProcessor, StreamFeaturesManager featuresManager,
                      XmppSecurityProperties securityProperties,
                      TlsNegotiationService tlsNegotiationService,
                      SaslAuthenticationService saslAuthenticationService,
                      ResourceBindingService resourceBindingService,
                      IqProcessingService iqProcessingService) {
        this.xmlProcessor = xmlProcessor;
        this.featuresManager = featuresManager;
        this.securityProperties = securityProperties;
        this.tlsNegotiationService = tlsNegotiationService;
        this.saslAuthenticationService = saslAuthenticationService;
        this.resourceBindingService = resourceBindingService;
        this.iqProcessingService = iqProcessingService;
        logger.info("ActorSystem initialized with service composition");
    }
    
    /**
     * Create a new connection actor for handling an XMPP client connection
     */
    public ConnectionActor createConnectionActor(String connectionId, NettyOutbound outbound) {
        logger.info("Creating ConnectionActor for connection {}", connectionId);
        
        // Store outbound connection for sending data
        outboundConnections.put(connectionId, outbound);
        
        // Create outbound sender function
        Consumer<OutgoingStanzaMessage> outboundSender = message -> {
            NettyOutbound connection = outboundConnections.get(message.connectionId());
            if (connection != null) {
                logger.debug("Sending message {} to connection {}: {}", message.getType(), connectionId, message.xmlData());
                connection.sendString(reactor.core.publisher.Mono.just(message.xmlData()))
                    .then()
                    .subscribe(
                        null,
                        error -> logger.error("Error sending data to connection {}: {}", 
                                             message.connectionId(), error.getMessage())
                    );
            } else {
                logger.warn("No outbound connection found for {}", message.connectionId());
            }
        };
        
        // Create and start the actor with service dependencies
        ConnectionActor actor = new ConnectionActor(connectionId, xmlProcessor, outboundSender, 
                                                   featuresManager, this, securityProperties,
                                                   tlsNegotiationService, saslAuthenticationService,
                                                   resourceBindingService, iqProcessingService);
        connectionActors.put(connectionId, actor);
        
        // Set the NettyOutbound for the actor (used by TLS and SASL actors)
        actor.setNettyOutbound(outbound);
        actor.start();
        
        return actor;
    }
    
    /**
     * Get an existing connection actor
     */
    public ConnectionActor getConnectionActor(String connectionId) {
        return connectionActors.get(connectionId);
    }
    
    /**
     * Remove and stop a connection actor
     */
    public void removeConnectionActor(String connectionId) {
        logger.info("Removing ConnectionActor for connection {}", connectionId);
        
        ConnectionActor actor = connectionActors.remove(connectionId);
        if (actor != null) {
            actor.stop();
        }
        
        // Clean up outbound connection
        outboundConnections.remove(connectionId);
    }
    
    /**
     * Send message to a specific connection actor
     */
    public void tellConnectionActor(String connectionId, ActorMessage message) {
        ConnectionActor actor = connectionActors.get(connectionId);
        if (actor != null) {
            actor.tell(message);
        } else {
            logger.warn("Connection actor {} not found for message {}", connectionId, message.getType());
        }
    }
    
    /**
     * Broadcast message to all connection actors
     */
    public void broadcastToAllConnections(ActorMessage message) {
        logger.debug("Broadcasting message {} to {} connections", 
                    message.getType(), connectionActors.size());
        
        connectionActors.values().forEach(actor -> actor.tell(message));
    }
    
    /**
     * Get count of active connection actors
     */
    public int getActiveConnectionCount() {
        return connectionActors.size();
    }
    
    /**
     * Get count of healthy connection actors
     */
    public long getHealthyConnectionCount() {
        return connectionActors.values().stream()
            .mapToLong(actor -> actor.isHealthy() ? 1 : 0)
            .sum();
    }
    
    /**
     * Perform health check on all actors and clean up unhealthy ones
     */
    public void performHealthCheck() {
        logger.debug("Performing health check on {} connection actors", connectionActors.size());
        
        connectionActors.entrySet().removeIf(entry -> {
            String connectionId = entry.getKey();
            ConnectionActor actor = entry.getValue();
            
            if (!actor.isHealthy()) {
                logger.warn("Removing unhealthy connection actor {}", connectionId);
                actor.stop();
                outboundConnections.remove(connectionId);
                return true;
            }
            return false;
        });
    }
    
    /**
     * Shutdown all actors gracefully
     */
    public void shutdown() {
        logger.info("Shutting down ActorSystem with {} active connections", connectionActors.size());
        
        connectionActors.values().forEach(ConnectionActor::stop);
        connectionActors.clear();
        outboundConnections.clear();
        
        logger.info("ActorSystem shutdown complete");
    }
    
    /**
     * Get system statistics for monitoring
     */
    public ActorSystemStats getStats() {
        return new ActorSystemStats(
            connectionActors.size(),
            getHealthyConnectionCount(),
            outboundConnections.size()
        );
    }
    
    /**
     * Statistics record for monitoring
     */
    public record ActorSystemStats(
        int totalConnections,
        long healthyConnections,
        int activeOutboundConnections
    ) {}
}