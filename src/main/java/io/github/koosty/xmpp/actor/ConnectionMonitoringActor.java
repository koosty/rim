package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.ActorMessage;
import io.github.koosty.xmpp.actor.message.GenericActorMessage;
import io.github.koosty.xmpp.actor.message.MessageType;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Actor responsible for monitoring and managing client connections.
 * Handles connection health checks, timeout detection, and cleanup.
 */
@Component
public class ConnectionMonitoringActor extends AbstractActor {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionMonitoringActor.class);
    
    // Connection timeout in seconds
    private static final long CONNECTION_TIMEOUT_SECONDS = 300; // 5 minutes
    private static final long HEALTH_CHECK_INTERVAL_SECONDS = 30; // 30 seconds
    
    private final Map<String, ConnectionInfo> activeConnections = new ConcurrentHashMap<>();
    private final AtomicInteger totalConnectionCount = new AtomicInteger(0);
    private final AtomicLong totalConnectionsCreated = new AtomicLong(0);
    private final AtomicLong totalConnectionsClosed = new AtomicLong(0);
    private final AtomicLong totalTimeouts = new AtomicLong(0);
    
    public ConnectionMonitoringActor() {
        super("ConnectionMonitoringActor");
        logger.info("ConnectionMonitoringActor initialized");
    }
    
    @Override
    protected Mono<ActorMessage> processMessage(ActorMessage message) {
        try {
            MessageType messageType = message.getType();
            
            if (MessageType.CONNECTION_OPENED.equals(messageType)) {
                return handleConnectionOpened(message);
            } else if (MessageType.CONNECTION_CLOSED.equals(messageType)) {
                return handleConnectionClosed(message);
            } else if (MessageType.CONNECTION_HEALTH_CHECK.equals(messageType)) {
                return handleConnectionHealthCheck(message);
            } else if (MessageType.CONNECTION_METRICS_REQUEST.equals(messageType)) {
                return handleConnectionMetricsRequest(message);
            } else if (MessageType.CONNECTION_CLEANUP.equals(messageType)) {
                return handleConnectionCleanup(message);
            } else if (MessageType.CONNECTION_TIMEOUT.equals(messageType)) {
                return handleConnectionTimeout(message);
            } else {
                logger.warn("Unknown message type: {}", messageType);
                return Mono.just(createErrorResponse("Unknown message type: " + messageType));
            }
        } catch (Exception e) {
            logger.error("Error handling message: {}", message, e);
            return Mono.just(createErrorResponse("Error processing message: " + e.getMessage()));
        }
    }
    
    private Mono<ActorMessage> handleConnectionOpened(ActorMessage message) {
        if (!(message instanceof GenericActorMessage genericMessage)) {
            return Mono.just(createErrorResponse("Invalid message type for connection opened"));
        }
        
        String connectionId = (String) genericMessage.payload().get("connectionId");
        String remoteAddress = (String) genericMessage.payload().get("remoteAddress");
        String userJid = (String) genericMessage.payload().get("userJid"); // May be null initially
        
        ConnectionInfo info = new ConnectionInfo(
            connectionId,
            remoteAddress,
            userJid,
            Instant.now(),
            Instant.now()
        );
        
        activeConnections.put(connectionId, info);
        totalConnectionCount.incrementAndGet();
        totalConnectionsCreated.incrementAndGet();
        
        logger.info("Connection opened: {} from {} (total active: {})", 
            connectionId, remoteAddress, activeConnections.size());
            
        return Mono.just(createResponse("CONNECTION_OPENED_RESPONSE", true, "Connection opened successfully"));
    }
    
    private Mono<ActorMessage> handleConnectionClosed(ActorMessage message) {
        if (!(message instanceof GenericActorMessage genericMessage)) {
            return Mono.just(createErrorResponse("Invalid message type for connection closed"));
        }
        
        String connectionId = (String) genericMessage.payload().get("connectionId");
        String reason = (String) genericMessage.payload().get("reason");
        
        ConnectionInfo info = activeConnections.remove(connectionId);
        if (info != null) {
            totalConnectionCount.decrementAndGet();
            totalConnectionsClosed.incrementAndGet();
            
            long connectionDuration = ChronoUnit.SECONDS.between(info.connectedAt(), Instant.now());
            
            logger.info("Connection closed: {} (duration: {}s, reason: {}, total active: {})", 
                connectionId, connectionDuration, reason, activeConnections.size());
                
            return Mono.just(createResponse("CONNECTION_CLOSED_RESPONSE", true, "Connection closed successfully"));
        } else {
            logger.warn("Attempted to close unknown connection: {}", connectionId);
            return Mono.just(createResponse("CONNECTION_CLOSED_RESPONSE", false, "Unknown connection ID"));
        }
    }
    
    private Mono<ActorMessage> handleConnectionHealthCheck(ActorMessage message) {
        if (!(message instanceof GenericActorMessage genericMessage)) {
            return Mono.just(createErrorResponse("Invalid message type for connection health check"));
        }
        
        String connectionId = (String) genericMessage.payload().get("connectionId");
        
        ConnectionInfo info = activeConnections.get(connectionId);
        if (info != null) {
            // Update last activity timestamp
            ConnectionInfo updatedInfo = info.withLastActivity(Instant.now());
            activeConnections.put(connectionId, updatedInfo);
            
            // Return health check response
            return Mono.just(createResponse(
                MessageType.CONNECTION_HEALTH_CHECK.name(), 
                true, 
                "Connection health check successful",
                Map.of(
                    "connectionId", connectionId,
                    "healthy", true,
                    "lastActivity", updatedInfo.lastActivity()
                )
            ));
        } else {
            logger.warn("Health check for unknown connection: {}", connectionId);
            return Mono.just(createResponse("CONNECTION_HEALTH_CHECK_RESPONSE", false, "Unknown connection ID"));
        }
    }
    
    private Mono<ActorMessage> handleConnectionMetricsRequest(ActorMessage message) {
        Map<String, Object> metrics = Map.of(
            "activeConnections", activeConnections.size(),
            "totalConnectionsCreated", totalConnectionsCreated.get(),
            "totalConnectionsClosed", totalConnectionsClosed.get(),
            "totalTimeouts", totalTimeouts.get(),
            "connections", activeConnections.values().stream()
                .map(this::connectionInfoToMap)
                .toList()
        );
        
        logger.debug("Sent connection metrics: {} active connections", activeConnections.size());
        return Mono.just(createResponse(
            MessageType.CONNECTION_METRICS_RESPONSE.name(), 
            true, 
            "Connection metrics retrieved successfully",
            Map.of("metrics", metrics)
        ));
    }
    
    private Mono<ActorMessage> handleConnectionCleanup(ActorMessage message) {
        Instant cutoffTime = Instant.now().minus(CONNECTION_TIMEOUT_SECONDS, ChronoUnit.SECONDS);
        int cleanedUpCount = 0;
        
        activeConnections.entrySet().removeIf(entry -> {
            ConnectionInfo info = entry.getValue();
            if (info.lastActivity().isBefore(cutoffTime)) {
                totalTimeouts.incrementAndGet();
                totalConnectionCount.decrementAndGet();
                
                logger.info("Cleaned up inactive connection: {} (last activity: {})", 
                    entry.getKey(), info.lastActivity());
                
                return true;
            }
            return false;
        });
        
        return Mono.just(createResponse("CONNECTION_CLEANUP_RESPONSE", true, 
            "Connection cleanup completed, cleaned up " + cleanedUpCount + " connections"));
    }
    
    private Mono<ActorMessage> handleConnectionTimeout(ActorMessage message) {
        if (!(message instanceof GenericActorMessage genericMessage)) {
            return Mono.just(createErrorResponse("Invalid message type for connection timeout"));
        }
        
        String connectionId = (String) genericMessage.payload().get("connectionId");
        String reason = (String) genericMessage.payload().get("reason");
        
        ConnectionInfo info = activeConnections.remove(connectionId);
        if (info != null) {
            totalTimeouts.incrementAndGet();
            totalConnectionCount.decrementAndGet();
            
            logger.warn("Connection timeout: {} (reason: {}, total active: {})", 
                connectionId, reason, activeConnections.size());
                
            return Mono.just(createResponse("CONNECTION_TIMEOUT_RESPONSE", true, "Connection timeout handled"));
        } else {
            return Mono.just(createResponse("CONNECTION_TIMEOUT_RESPONSE", false, "Unknown connection ID"));
        }
    }
    
    private ActorMessage createErrorResponse(String errorMessage) {
        return createResponse("ERROR_RESPONSE", false, errorMessage);
    }
    
    /**
     * Scheduled health check for all connections
     */
    @Scheduled(fixedDelayString = "#{${xmpp.monitoring.health-check-interval:30} * 1000}")
    public void performScheduledHealthCheck() {
        try {
            // Create a cleanup message and process it
            GenericActorMessage cleanupMessage = new GenericActorMessage(
                MessageType.CONNECTION_CLEANUP.name(), 
                actorId, 
                Map.of()
            );
            handleConnectionCleanup(cleanupMessage).subscribe();
            
            if (logger.isDebugEnabled()) {
                logger.debug("Health check completed: {} active connections", activeConnections.size());
            }
        } catch (Exception e) {
            logger.error("Error during scheduled health check", e);
        }
    }
    
    /**
     * Get current connection statistics
     */
    public ConnectionStatistics getConnectionStatistics() {
        return new ConnectionStatistics(
            activeConnections.size(),
            totalConnectionsCreated.get(),
            totalConnectionsClosed.get(),
            totalTimeouts.get()
        );
    }
    
    /**
     * Check if a connection is active
     */
    public boolean isConnectionActive(String connectionId) {
        return activeConnections.containsKey(connectionId);
    }
    
    /**
     * Get connection information
     */
    public ConnectionInfo getConnectionInfo(String connectionId) {
        return activeConnections.get(connectionId);
    }
    
    /**
     * Update connection user JID after authentication
     */
    public void updateConnectionUser(String connectionId, String userJid) {
        ConnectionInfo info = activeConnections.get(connectionId);
        if (info != null) {
            ConnectionInfo updatedInfo = info.withUserJid(userJid);
            activeConnections.put(connectionId, updatedInfo);
            logger.debug("Updated connection {} with user JID: {}", connectionId, userJid);
        }
    }
    
    private Map<String, Object> connectionInfoToMap(ConnectionInfo info) {
        return Map.of(
            "connectionId", info.connectionId(),
            "remoteAddress", info.remoteAddress(),
            "userJid", info.userJid() != null ? info.userJid() : "unknown",
            "connectedAt", info.connectedAt().toString(),
            "lastActivity", info.lastActivity().toString(),
            "durationSeconds", ChronoUnit.SECONDS.between(info.connectedAt(), Instant.now())
        );
    }
    
    /**
     * Connection information record
     */
    public record ConnectionInfo(
        String connectionId,
        String remoteAddress,
        String userJid,
        Instant connectedAt,
        Instant lastActivity
    ) {
        public ConnectionInfo withLastActivity(Instant newLastActivity) {
            return new ConnectionInfo(connectionId, remoteAddress, userJid, connectedAt, newLastActivity);
        }
        
        public ConnectionInfo withUserJid(String newUserJid) {
            return new ConnectionInfo(connectionId, remoteAddress, newUserJid, connectedAt, lastActivity);
        }
    }
    
    /**
     * Connection statistics record
     */
    public record ConnectionStatistics(
        int activeConnections,
        long totalConnectionsCreated,
        long totalConnectionsClosed,
        long totalTimeouts
    ) {}
}