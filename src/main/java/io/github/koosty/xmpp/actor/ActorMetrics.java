package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.ActorMessage;
import io.github.koosty.xmpp.actor.message.GenericActorMessage;
import io.github.koosty.xmpp.actor.message.MessageType;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Actor metrics collection and health monitoring for the XMPP server.
 * Integrates with Spring Boot Actuator for operational monitoring.
 */
@Component
public class ActorMetrics extends AbstractActor implements HealthIndicator {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorMetrics.class);
    
    private final Map<String, ActorMetricsData> actorMetrics = new ConcurrentHashMap<>();
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
    private final LongAdder totalProcessingTime = new LongAdder();
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final Map<String, AtomicLong> messageTypeCounters = new ConcurrentHashMap<>();
    
    private volatile boolean systemHealthy = true;
    private volatile Instant lastHealthCheck = Instant.now();
    
    public ActorMetrics() {
        super("ActorMetrics");
        logger.info("ActorMetrics initialized");
        
        // Initialize message type counters
        for (MessageType messageType : MessageType.values()) {
            messageTypeCounters.put(messageType.name(), new AtomicLong(0));
        }
    }
    
    @Override
    protected Mono<ActorMessage> processMessage(ActorMessage message) {
        try {
            MessageType messageType = message.getType();
            
            if (MessageType.METRICS_COLLECTION.equals(messageType)) {
                return handleMetricsCollection(message);
            } else if (MessageType.HEALTH_STATUS_REQUEST.equals(messageType)) {
                return handleHealthStatusRequest(message);
            } else if (MessageType.PERFORMANCE_METRICS.equals(messageType)) {
                return handlePerformanceMetrics(message);
            } else {
                logger.warn("Unknown message type: {}", messageType);
                return Mono.just(createErrorResponse("Unknown message type: " + messageType));
            }
        } catch (Exception e) {
            logger.error("Error handling metrics message: {}", message, e);
            totalErrors.incrementAndGet();
            return Mono.just(createErrorResponse("Error processing message: " + e.getMessage()));
        }
    }
    
    private Mono<ActorMessage> handleMetricsCollection(ActorMessage message) {
        if (!(message instanceof GenericActorMessage genericMessage)) {
            return Mono.just(createErrorResponse("Invalid message type for metrics collection"));
        }
        
        String actorName = (String) genericMessage.payload().get("actorName");
        Long messagesProcessed = (Long) genericMessage.payload().get("messagesProcessed");
        Long processingTime = (Long) genericMessage.payload().get("processingTime");
        Long errorCount = (Long) genericMessage.payload().get("errorCount");
        Boolean healthy = (Boolean) genericMessage.payload().get("healthy");
        
        // Store actor-specific metrics
        if (actorName != null) {
            ActorMetricsData actorData = new ActorMetricsData(
                actorName,
                messagesProcessed != null ? messagesProcessed : 0L,
                processingTime != null ? processingTime : 0L,
                errorCount != null ? errorCount : 0L,
                healthy != null ? healthy : true,
                Instant.now()
            );
            actorMetrics.put(actorName, actorData);
        }
        
        // Update global counters
        if (messagesProcessed != null) {
            totalMessagesProcessed.addAndGet(messagesProcessed);
        }
        if (processingTime != null) {
            totalProcessingTime.add(processingTime);
        }
        if (errorCount != null) {
            totalErrors.addAndGet(errorCount);
        }
        
        logger.debug("Updated metrics for actor: {}", actorName);
        return Mono.just(createResponse("METRICS_COLLECTION_RESPONSE", true, "Metrics collected successfully"));
    }
    
    private Mono<ActorMessage> handleHealthStatusRequest(ActorMessage message) {
        Map<String, Object> healthStatus = Map.of(
            "systemHealthy", systemHealthy,
            "lastHealthCheck", lastHealthCheck.toString(),
            "totalActors", actorMetrics.size(),
            "totalMessagesProcessed", totalMessagesProcessed.get(),
            "totalErrors", totalErrors.get(),
            "averageProcessingTime", calculateAverageProcessingTime(),
            "actorHealth", actorMetrics.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> Map.of(
                        "healthy", entry.getValue().healthy(),
                        "messagesProcessed", entry.getValue().messagesProcessed(),
                        "errorCount", entry.getValue().errorCount(),
                        "lastUpdate", entry.getValue().lastUpdate().toString()
                    )
                ))
        );
        
        logger.debug("Sent health status response");
        return Mono.just(createResponse(
            MessageType.HEALTH_STATUS_RESPONSE.name(), 
            true, 
            "Health status retrieved successfully", 
            Map.of("healthStatus", healthStatus)
        ));
    }
    
    private Mono<ActorMessage> handlePerformanceMetrics(ActorMessage message) {
        if (!(message instanceof GenericActorMessage genericMessage)) {
            return Mono.just(createErrorResponse("Invalid message type for performance metrics"));
        }
        
        String messageTypeName = (String) genericMessage.payload().get("messageType");
        Long processingTime = (Long) genericMessage.payload().get("processingTime");
        
        if (messageTypeName != null) {
            messageTypeCounters.computeIfAbsent(messageTypeName, k -> new AtomicLong(0))
                .incrementAndGet();
        }
        
        if (processingTime != null) {
            totalProcessingTime.add(processingTime);
        }
        
        return Mono.just(createResponse("PERFORMANCE_METRICS_RESPONSE", true, "Performance metrics recorded successfully"));
    }
    
    private ActorMessage createErrorResponse(String errorMessage) {
        return createResponse("ERROR_RESPONSE", false, errorMessage);
    }
    
    /**
     * Record message processing metrics
     */
    public void recordMessageProcessed(String actorName, MessageType messageType, long processingTimeMs) {
        messageTypeCounters.computeIfAbsent(messageType.name(), k -> new AtomicLong(0))
            .incrementAndGet();
        
        totalMessagesProcessed.incrementAndGet();
        totalProcessingTime.add(processingTimeMs);
        
        updateActorMetrics(actorName, 1, processingTimeMs, 0, true);
    }
    
    /**
     * Record actor error
     */
    public void recordError(String actorName, Exception error) {
        totalErrors.incrementAndGet();
        updateActorMetrics(actorName, 0, 0, 1, false);
        
        logger.warn("Recorded error for actor {}: {}", actorName, error.getMessage());
    }
    
    /**
     * Update actor health status
     */
    public void updateActorHealth(String actorName, boolean healthy) {
        updateActorMetrics(actorName, 0, 0, 0, healthy);
    }
    
    private void updateActorMetrics(String actorName, long messagesIncrement, long processingTime, 
                                   long errorIncrement, boolean healthy) {
        actorMetrics.compute(actorName, (key, existing) -> {
            if (existing == null) {
                return new ActorMetricsData(
                    actorName,
                    messagesIncrement,
                    processingTime,
                    errorIncrement,
                    healthy,
                    Instant.now()
                );
            } else {
                return new ActorMetricsData(
                    actorName,
                    existing.messagesProcessed() + messagesIncrement,
                    existing.totalProcessingTime() + processingTime,
                    existing.errorCount() + errorIncrement,
                    healthy,
                    Instant.now()
                );
            }
        });
    }
    
    /**
     * Scheduled health check for all actors
     */
    @Scheduled(fixedDelayString = "#{${xmpp.monitoring.health-check-interval:30} * 1000}")
    public void performSystemHealthCheck() {
        try {
            lastHealthCheck = Instant.now();
            
            // Check if any actors are unhealthy
            boolean allActorsHealthy = actorMetrics.values().stream()
                .allMatch(ActorMetricsData::healthy);
            
            // Check error rate
            double errorRate = calculateErrorRate();
            boolean errorRateAcceptable = errorRate < 0.05; // 5% error rate threshold
            
            systemHealthy = allActorsHealthy && errorRateAcceptable;
            
            if (!systemHealthy) {
                logger.warn("System health check failed - allActorsHealthy: {}, errorRate: {}", 
                    allActorsHealthy, errorRate);
            } else {
                logger.debug("System health check passed - {} actors monitored", actorMetrics.size());
            }
            
        } catch (Exception e) {
            logger.error("Error during system health check", e);
            systemHealthy = false;
        }
    }
    
    /**
     * Get comprehensive metrics summary
     */
    public MetricsSummary getMetricsSummary() {
        return new MetricsSummary(
            actorMetrics.size(),
            totalMessagesProcessed.get(),
            totalErrors.get(),
            calculateAverageProcessingTime(),
            calculateErrorRate(),
            systemHealthy,
            lastHealthCheck,
            Map.copyOf(actorMetrics)
        );
    }
    
    /**
     * Spring Boot Actuator health indicator
     */
    @Override
    public Health health() {
        Health.Builder builder = systemHealthy ? Health.up() : Health.down();
        
        builder.withDetail("totalActors", actorMetrics.size())
               .withDetail("totalMessagesProcessed", totalMessagesProcessed.get())
               .withDetail("totalErrors", totalErrors.get())
               .withDetail("averageProcessingTimeMs", calculateAverageProcessingTime())
               .withDetail("errorRate", calculateErrorRate())
               .withDetail("lastHealthCheck", lastHealthCheck.toString());
        
        // Add unhealthy actors
        Map<String, Object> unhealthyActors = actorMetrics.entrySet().stream()
            .filter(entry -> !entry.getValue().healthy())
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> Map.of(
                    "errorCount", entry.getValue().errorCount(),
                    "lastUpdate", entry.getValue().lastUpdate().toString()
                )
            ));
        
        if (!unhealthyActors.isEmpty()) {
            builder.withDetail("unhealthyActors", unhealthyActors);
        }
        
        return builder.build();
    }
    
    private double calculateAverageProcessingTime() {
        long total = totalMessagesProcessed.get();
        return total > 0 ? (double) totalProcessingTime.sum() / total : 0.0;
    }
    
    private double calculateErrorRate() {
        long total = totalMessagesProcessed.get();
        return total > 0 ? (double) totalErrors.get() / total : 0.0;
    }
    
    /**
     * Actor metrics data record
     */
    public record ActorMetricsData(
        String actorName,
        long messagesProcessed,
        long totalProcessingTime,
        long errorCount,
        boolean healthy,
        Instant lastUpdate
    ) {}
    
    /**
     * Comprehensive metrics summary
     */
    public record MetricsSummary(
        int totalActors,
        long totalMessagesProcessed,
        long totalErrors,
        double averageProcessingTimeMs,
        double errorRate,
        boolean systemHealthy,
        Instant lastHealthCheck,
        Map<String, ActorMetricsData> actorMetrics
    ) {}
}