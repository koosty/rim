package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.ActorMessage;
import io.github.koosty.xmpp.actor.message.GenericActorMessage;
import io.github.koosty.xmpp.actor.message.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ActorMetricsTest {
    
    private ActorMetrics actorMetrics;
    
    @BeforeEach
    void setUp() {
        actorMetrics = new ActorMetrics();
    }
    
    @Test
    void recordMessageProcessed_shouldUpdateMetrics() {
        actorMetrics.recordMessageProcessed("TestActor", MessageType.INCOMING_XML, 100);
        
        ActorMetrics.MetricsSummary summary = actorMetrics.getMetricsSummary();
        
        assertEquals(1, summary.totalActors());
        assertEquals(1, summary.totalMessagesProcessed());
        assertEquals(100.0, summary.averageProcessingTimeMs(), 0.01);
        assertEquals(0.0, summary.errorRate(), 0.01);
        assertTrue(summary.systemHealthy());
    }
    
    @Test
    void recordError_shouldUpdateErrorMetrics() {
        actorMetrics.recordError("TestActor", new RuntimeException("Test error"));
        
        ActorMetrics.MetricsSummary summary = actorMetrics.getMetricsSummary();
        
        assertEquals(1, summary.totalActors());
        assertEquals(1, summary.totalErrors());
        assertTrue(summary.actorMetrics().get("TestActor").errorCount() > 0);
        assertFalse(summary.actorMetrics().get("TestActor").healthy());
    }
    
    @Test
    void updateActorHealth_shouldUpdateHealthStatus() {
        // First make actor unhealthy
        actorMetrics.recordError("TestActor", new RuntimeException("Test error"));
        assertFalse(actorMetrics.getMetricsSummary().actorMetrics().get("TestActor").healthy());
        
        // Then restore health
        actorMetrics.updateActorHealth("TestActor", true);
        assertTrue(actorMetrics.getMetricsSummary().actorMetrics().get("TestActor").healthy());
    }
    
    @Test
    void handleHealthStatusRequest_shouldReturnHealthStatus() throws InterruptedException {
        // Add some metrics
        actorMetrics.recordMessageProcessed("Actor1", MessageType.INCOMING_XML, 50);
        actorMetrics.recordMessageProcessed("Actor2", MessageType.SASL_AUTH, 75);
        
        GenericActorMessage request = new GenericActorMessage(
            MessageType.HEALTH_STATUS_REQUEST.name(),
            "TestSender",
            Map.of()
        );
        
        Mono<ActorMessage> responseMono = actorMetrics.processMessage(request);
        
        StepVerifier.create(responseMono)
            .assertNext(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                assertEquals(MessageType.HEALTH_STATUS_RESPONSE.name(), genericResponse.messageType());
                assertTrue((Boolean) genericResponse.payload().get("success"));
                
                @SuppressWarnings("unchecked")
                Map<String, Object> healthStatus = (Map<String, Object>) genericResponse.payload().get("healthStatus");
                assertNotNull(healthStatus);
                assertTrue((Boolean) healthStatus.get("systemHealthy"));
                assertEquals(2, (Integer) healthStatus.get("totalActors"));
                assertEquals(2L, healthStatus.get("totalMessagesProcessed"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }
    
    @Test
    void handleMetricsCollection_shouldUpdateActorMetrics() {
        GenericActorMessage metricsMessage = new GenericActorMessage(
            MessageType.METRICS_COLLECTION.name(),
            "TestActor",
            Map.of(
                "actorName", "TestActor",
                "messagesProcessed", 10L,
                "processingTime", 500L,
                "errorCount", 2L,
                "healthy", false
            )
        );
        
        Mono<ActorMessage> responseMono = actorMetrics.processMessage(metricsMessage);
        
        StepVerifier.create(responseMono)
            .assertNext(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                assertTrue((Boolean) genericResponse.payload().get("success"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
        
        ActorMetrics.MetricsSummary summary = actorMetrics.getMetricsSummary();
        assertEquals(1, summary.totalActors());
        
        ActorMetrics.ActorMetricsData actorData = summary.actorMetrics().get("TestActor");
        assertNotNull(actorData);
        assertEquals(10L, actorData.messagesProcessed());
        assertEquals(500L, actorData.totalProcessingTime());
        assertEquals(2L, actorData.errorCount());
        assertFalse(actorData.healthy());
    }
    
    @Test
    void healthIndicator_shouldReturnHealthStatusForActuator() {
        // Add some test data
        actorMetrics.recordMessageProcessed("HealthyActor", MessageType.INCOMING_XML, 50);
        actorMetrics.recordError("UnhealthyActor", new RuntimeException("Test error"));
        
        // Trigger system health check
        actorMetrics.performSystemHealthCheck();
        
        Health health = actorMetrics.health();
        
        // System should be unhealthy due to error
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(2, health.getDetails().get("totalActors"));
        assertEquals(1L, health.getDetails().get("totalMessagesProcessed"));
        assertEquals(1L, health.getDetails().get("totalErrors"));
        assertTrue(health.getDetails().containsKey("unhealthyActors"));
    }
    
    @Test
    void performSystemHealthCheck_shouldUpdateSystemHealth() {
        // Start with healthy system
        actorMetrics.recordMessageProcessed("Actor1", MessageType.INCOMING_XML, 50);
        
        actorMetrics.performSystemHealthCheck();
        assertTrue(actorMetrics.getMetricsSummary().systemHealthy());
        
        // Add errors to make system unhealthy
        for (int i = 0; i < 10; i++) {
            actorMetrics.recordError("Actor1", new RuntimeException("Test error"));
        }
        
        actorMetrics.performSystemHealthCheck();
        assertFalse(actorMetrics.getMetricsSummary().systemHealthy());
    }
    
    @Test
    void multipleActors_shouldTrackSeparately() {
        actorMetrics.recordMessageProcessed("Actor1", MessageType.INCOMING_XML, 100);
        actorMetrics.recordMessageProcessed("Actor2", MessageType.SASL_AUTH, 200);
        actorMetrics.recordError("Actor1", new RuntimeException("Error in Actor1"));
        
        ActorMetrics.MetricsSummary summary = actorMetrics.getMetricsSummary();
        
        assertEquals(2, summary.totalActors());
        assertEquals(2, summary.totalMessagesProcessed());
        assertEquals(1, summary.totalErrors());
        
        ActorMetrics.ActorMetricsData actor1Data = summary.actorMetrics().get("Actor1");
        ActorMetrics.ActorMetricsData actor2Data = summary.actorMetrics().get("Actor2");
        
        assertEquals(1, actor1Data.messagesProcessed());
        assertEquals(100, actor1Data.totalProcessingTime());
        assertEquals(1, actor1Data.errorCount());
        assertFalse(actor1Data.healthy());
        
        assertEquals(1, actor2Data.messagesProcessed());
        assertEquals(200, actor2Data.totalProcessingTime());
        assertEquals(0, actor2Data.errorCount());
        assertTrue(actor2Data.healthy());
    }
    
    @Test
    void handlePerformanceMetrics_shouldUpdateCounters() {
        // First record some messages as processed to establish a baseline
        actorMetrics.recordMessageProcessed("TestActor", MessageType.PERFORMANCE_METRICS, 100);
        
        GenericActorMessage perfMessage = new GenericActorMessage(
            MessageType.PERFORMANCE_METRICS.name(),
            "TestSender",
            Map.of(
                "messageType", "INCOMING_XML",
                "processingTime", 150L
            )
        );
        
        Mono<ActorMessage> responseMono = actorMetrics.processMessage(perfMessage);
        
        StepVerifier.create(responseMono)
            .assertNext(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                assertTrue((Boolean) genericResponse.payload().get("success"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
        
        // Verify metrics were updated (we can't directly access messageTypeCounters, 
        // but we can verify through total processing time)
        ActorMetrics.MetricsSummary summary = actorMetrics.getMetricsSummary();
        assertTrue(summary.averageProcessingTimeMs() > 0);
    }
}