package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.ActorMessage;
import io.github.koosty.xmpp.actor.message.GenericActorMessage;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Actor supervision system for fault tolerance and lifecycle management.
 * Implements supervision strategies for different actor types with health monitoring.
 */
public class ActorSupervision extends AbstractActor {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorSupervision.class);
    
    private final Map<String, SupervisedActor> supervisedActors = new ConcurrentHashMap<>();
    private final Map<String, SupervisionStrategy> strategies = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failureCounters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService healthCheckScheduler;
    
    // Configuration
    private final Duration healthCheckInterval = Duration.ofSeconds(30);
    private final int maxFailureCount = 3;
    private final Duration failureResetInterval = Duration.ofMinutes(5);
    
    public ActorSupervision(String actorId) {
        super(actorId);
        this.healthCheckScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ActorSupervision-HealthCheck");
            t.setDaemon(true);
            return t;
        });
    }
    
    @Override
    public void start() {
        super.start();
        startHealthCheckScheduler();
    }
    
    @Override
    public void stop() {
        super.stop();
        healthCheckScheduler.shutdown();
        try {
            if (!healthCheckScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                healthCheckScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthCheckScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    protected Mono<ActorMessage> processMessage(ActorMessage message) {
        return switch (message.getType()) {
            case ACTOR_SUPERVISION -> handleSupervisionRequest((GenericActorMessage) message);
            case ACTOR_START -> handleActorStart((GenericActorMessage) message);
            case ACTOR_STOP -> handleActorStop((GenericActorMessage) message);
            case GET_CONNECTION_COUNT -> handleGetSupervisionStats();
            default -> Mono.error(new IllegalArgumentException("Unknown message type: " + message.getType()));
        };
    }
    
    /**
     * Start health check scheduler
     */
    private void startHealthCheckScheduler() {
        healthCheckScheduler.scheduleAtFixedRate(
            this::performHealthChecks,
            healthCheckInterval.toSeconds(),
            healthCheckInterval.toSeconds(),
            TimeUnit.SECONDS
        );
        
        healthCheckScheduler.scheduleAtFixedRate(
            this::resetFailureCounters,
            failureResetInterval.toMinutes(),
            failureResetInterval.toMinutes(),
            TimeUnit.MINUTES
        );
        
        logger.info("Actor supervision health checks started");
    }
    
    /**
     * Handle supervision request
     */
    private Mono<ActorMessage> handleSupervisionRequest(GenericActorMessage message) {
        String actorId = message.getPayload("actorId", String.class);
        String actorType = message.getPayload("actorType", String.class);
        AbstractActor actor = message.getPayload("actor", AbstractActor.class);
        String action = message.getPayload("action", String.class);
        
        if (actorId == null) {
            return Mono.just(createResponse("supervision-response", false, "No actor ID provided"));
        }
        
        return Mono.fromCallable(() -> {
            switch (action) {
                case "supervise" -> {
                    return superviseActor(actorId, actorType, actor);
                }
                case "unsupervise" -> {
                    return unsuperviseActor(actorId);
                }
                case "restart" -> {
                    return restartActor(actorId);
                }
                case "escalate" -> {
                    return escalateFailure(actorId);
                }
                default -> {
                    return createResponse("supervision-response", false, "Unknown action: " + action);
                }
            }
        });
    }
    
    /**
     * Start supervising an actor
     */
    private ActorMessage superviseActor(String actorId, String actorType, AbstractActor actor) {
        if (actor == null) {
            return createResponse("supervision-response", false, "No actor provided");
        }
        
        SupervisionStrategy strategy = determineStrategy(actorType, actor);
        SupervisedActor supervisedActor = new SupervisedActor(actorId, actorType, actor, Instant.now());
        
        supervisedActors.put(actorId, supervisedActor);
        strategies.put(actorId, strategy);
        failureCounters.put(actorId, new AtomicInteger(0));
        
        logger.info("Started supervising actor: {} (type: {}, strategy: {})", 
            actorId, actorType, strategy);
        
        return createResponse("supervision-response", true, "Actor supervision started");
    }
    
    /**
     * Stop supervising an actor
     */
    private ActorMessage unsuperviseActor(String actorId) {
        SupervisedActor removed = supervisedActors.remove(actorId);
        strategies.remove(actorId);
        failureCounters.remove(actorId);
        
        if (removed != null) {
            logger.info("Stopped supervising actor: {}", actorId);
            return createResponse("supervision-response", true, "Actor supervision stopped");
        } else {
            return createResponse("supervision-response", false, "Actor not supervised: " + actorId);
        }
    }
    
    /**
     * Restart an actor
     */
    private ActorMessage restartActor(String actorId) {
        SupervisedActor supervisedActor = supervisedActors.get(actorId);
        if (supervisedActor == null) {
            return createResponse("supervision-response", false, "Actor not found: " + actorId);
        }
        
        AbstractActor actor = supervisedActor.actor();
        
        try {
            logger.info("Restarting actor: {}", actorId);
            
            // Stop the actor
            actor.stop();
            
            // Wait briefly for cleanup
            Thread.sleep(100);
            
            // Restart the actor
            actor.start();
            
            // Update supervision record
            SupervisedActor updatedActor = new SupervisedActor(
                actorId, 
                supervisedActor.actorType(), 
                actor, 
                Instant.now()
            );
            supervisedActors.put(actorId, updatedActor);
            
            logger.info("Successfully restarted actor: {}", actorId);
            return createResponse("supervision-response", true, "Actor restarted");
            
        } catch (Exception e) {
            logger.error("Failed to restart actor {}: {}", actorId, e.getMessage());
            return createResponse("supervision-response", false, "Restart failed: " + e.getMessage());
        }
    }
    
    /**
     * Escalate failure to parent supervisor
     */
    private ActorMessage escalateFailure(String actorId) {
        logger.warn("Escalating failure for actor: {}", actorId);
        
        // In a full implementation, this would escalate to a parent supervisor
        // For now, we'll just log and potentially stop the actor
        
        SupervisedActor supervisedActor = supervisedActors.get(actorId);
        if (supervisedActor != null) {
            AbstractActor actor = supervisedActor.actor();
            actor.stop();
            
            logger.warn("Stopped failing actor due to escalation: {}", actorId);
            return createResponse("supervision-response", true, "Actor stopped due to escalation");
        }
        
        return createResponse("supervision-response", false, "Actor not found for escalation");
    }
    
    /**
     * Handle actor start request
     */
    private Mono<ActorMessage> handleActorStart(GenericActorMessage message) {
        String actorId = message.getPayload("actorId", String.class);
        
        if (actorId == null) {
            return Mono.just(createResponse("actor-start-response", false, "No actor ID provided"));
        }
        
        SupervisedActor supervisedActor = supervisedActors.get(actorId);
        if (supervisedActor == null) {
            return Mono.just(createResponse("actor-start-response", false, "Actor not supervised"));
        }
        
        try {
            supervisedActor.actor().start();
            logger.info("Started supervised actor: {}", actorId);
            return Mono.just(createResponse("actor-start-response", true, "Actor started"));
        } catch (Exception e) {
            logger.error("Failed to start actor {}: {}", actorId, e.getMessage());
            return Mono.just(createResponse("actor-start-response", false, "Start failed: " + e.getMessage()));
        }
    }
    
    /**
     * Handle actor stop request
     */
    private Mono<ActorMessage> handleActorStop(GenericActorMessage message) {
        String actorId = message.getPayload("actorId", String.class);
        
        if (actorId == null) {
            return Mono.just(createResponse("actor-stop-response", false, "No actor ID provided"));
        }
        
        SupervisedActor supervisedActor = supervisedActors.get(actorId);
        if (supervisedActor == null) {
            return Mono.just(createResponse("actor-stop-response", false, "Actor not supervised"));
        }
        
        try {
            supervisedActor.actor().stop();
            logger.info("Stopped supervised actor: {}", actorId);
            return Mono.just(createResponse("actor-stop-response", true, "Actor stopped"));
        } catch (Exception e) {
            logger.error("Failed to stop actor {}: {}", actorId, e.getMessage());
            return Mono.just(createResponse("actor-stop-response", false, "Stop failed: " + e.getMessage()));
        }
    }
    
    /**
     * Get supervision statistics
     */
    private Mono<ActorMessage> handleGetSupervisionStats() {
        Map<String, Object> stats = Map.of(
            "supervisedActors", supervisedActors.size(),
            "totalFailures", failureCounters.values().stream().mapToInt(AtomicInteger::get).sum(),
            "strategies", strategies.size()
        );
        
        return Mono.just(createResponse("supervision-stats-response", true, 
            "Supervision statistics retrieved", stats));
    }
    
    /**
     * Perform health checks on all supervised actors
     */
    private void performHealthChecks() {
        try {
            supervisedActors.forEach((actorId, supervisedActor) -> {
                try {
                    AbstractActor actor = supervisedActor.actor();
                    boolean healthy = actor.isHealthy();
                    
                    if (!healthy) {
                        handleActorFailure(actorId);
                    }
                } catch (Exception e) {
                    logger.error("Health check failed for actor {}: {}", actorId, e.getMessage());
                    handleActorFailure(actorId);
                }
            });
        } catch (Exception e) {
            logger.error("Error during health checks: {}", e.getMessage());
        }
    }
    
    /**
     * Handle actor failure
     */
    private void handleActorFailure(String actorId) {
        AtomicInteger failureCount = failureCounters.get(actorId);
        SupervisionStrategy strategy = strategies.get(actorId);
        
        if (failureCount == null || strategy == null) {
            logger.warn("No supervision data for failed actor: {}", actorId);
            return;
        }
        
        int failures = failureCount.incrementAndGet();
        logger.warn("Actor {} failed (failure count: {})", actorId, failures);
        
        if (failures >= maxFailureCount) {
            logger.error("Actor {} exceeded max failures ({}), applying strategy: {}", 
                actorId, maxFailureCount, strategy);
            
            switch (strategy) {
                case RESTART -> restartActor(actorId);
                case ESCALATE -> escalateFailure(actorId);
                case STOP -> {
                    SupervisedActor supervisedActor = supervisedActors.get(actorId);
                    if (supervisedActor != null) {
                        supervisedActor.actor().stop();
                        logger.info("Stopped failing actor: {}", actorId);
                    }
                }
                case IGNORE -> logger.info("Ignoring failure for actor: {}", actorId);
            }
            
            // Reset failure count after applying strategy
            failureCount.set(0);
        } else {
            // Try restart for immediate recovery
            if (strategy == SupervisionStrategy.RESTART) {
                restartActor(actorId);
            }
        }
    }
    
    /**
     * Reset failure counters periodically
     */
    private void resetFailureCounters() {
        try {
            failureCounters.values().forEach(counter -> counter.set(0));
            logger.debug("Reset failure counters for {} actors", failureCounters.size());
        } catch (Exception e) {
            logger.error("Error resetting failure counters: {}", e.getMessage());
        }
    }
    
    /**
     * Determine supervision strategy based on actor type
     */
    private SupervisionStrategy determineStrategy(String actorType, AbstractActor actor) {
        if (actorType == null) {
            return SupervisionStrategy.RESTART; // Default strategy
        }
        
        return switch (actorType) {
            case "ConnectionActor" -> SupervisionStrategy.RESTART; // Restart failed connections
            case "SessionActor" -> SupervisionStrategy.RESTART; // Restart failed sessions
            case "MessageRoutingActor" -> SupervisionStrategy.ESCALATE; // Critical component
            case "PresenceActor" -> SupervisionStrategy.ESCALATE; // Critical component
            case "IqProcessingActor" -> SupervisionStrategy.RESTART; // Restart IQ processor
            case "TlsNegotiationActor" -> SupervisionStrategy.RESTART; // Restart TLS handler
            case "SaslAuthenticationActor" -> SupervisionStrategy.RESTART; // Restart SASL handler
            case "ResourceBindingActor" -> SupervisionStrategy.RESTART; // Restart binding handler
            default -> SupervisionStrategy.RESTART; // Default to restart
        };
    }
    
    /**
     * Supervision strategy enumeration
     */
    public enum SupervisionStrategy {
        RESTART,   // Restart the failed actor
        ESCALATE,  // Escalate to parent supervisor
        STOP,      // Stop the failed actor
        IGNORE     // Ignore the failure
    }
    
    /**
     * Record representing a supervised actor
     */
    private record SupervisedActor(
        String actorId,
        String actorType,
        AbstractActor actor,
        Instant startTime
    ) {}
}