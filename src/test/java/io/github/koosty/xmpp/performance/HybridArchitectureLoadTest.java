package io.github.koosty.xmpp.performance;

import io.github.koosty.xmpp.actor.ActorSystem;
import io.github.koosty.xmpp.actor.ConnectionActor;
import io.github.koosty.xmpp.actor.message.IncomingXmlMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.netty.NettyOutbound;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.*;

/**
 * Performance testing for hybrid Reactor-Actor architecture
 * Tests throughput, latency, and scalability characteristics
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Hybrid Architecture Performance Tests")
class HybridArchitectureLoadTest {

    @Autowired
    private ActorSystem actorSystem;

    private NettyOutbound mockOutbound;
    private AtomicInteger processedMessages;
    private AtomicLong totalProcessingTime;

    @BeforeEach
    void setUp() {
        mockOutbound = mock(NettyOutbound.class);
        processedMessages = new AtomicInteger(0);
        totalProcessingTime = new AtomicLong(0);
        
        // Configure mock to simulate fast network responses
        when(mockOutbound.sendString(any())).thenReturn(mockOutbound);
        when(mockOutbound.then()).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Single connection high message throughput test")
    void testSingleConnectionThroughput() {
        String connectionId = "throughput-test-conn";
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);

        int messageCount = 10000;
        Instant startTime = Instant.now();
        
        // Send messages rapidly
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            String xmlContent = "<message id='" + i + "'><body>Test message " + i + "</body></message>";
            IncomingXmlMessage message = IncomingXmlMessage.of(connectionId, xmlContent);
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                actorSystem.tellConnectionActor(connectionId, message);
            });
            futures.add(future);
        }
        
        // Wait for all messages to be sent
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Allow time for processing
        await().atMost(Duration.ofSeconds(10))
               .pollDelay(Duration.ofMillis(100))
               .until(() -> true); // Give time for async processing
        
        Instant endTime = Instant.now();
        long totalTimeMs = Duration.between(startTime, endTime).toMillis();
        double messagesPerSecond = (messageCount * 1000.0) / totalTimeMs;
        
        System.out.printf("Processed %d messages in %d ms (%.2f msg/sec)%n", 
                         messageCount, totalTimeMs, messagesPerSecond);
        
        // Performance assertion: should handle at least 1000 messages per second
        assertTrue(messagesPerSecond > 1000, 
                  String.format("Throughput too low: %.2f msg/sec", messagesPerSecond));
        
        assertTrue(actor.isHealthy(), "Actor should remain healthy under load");
        
        actorSystem.removeConnectionActor(connectionId);
    }

    @Test
    @DisplayName("Multiple concurrent connections scalability test")
    void testMultipleConnectionsScalability() {
        int connectionCount = 100;
        int messagesPerConnection = 100;
        List<String> connectionIds = new ArrayList<>();
        List<ConnectionActor> actors = new ArrayList<>();
        
        // Create multiple connections
        for (int i = 0; i < connectionCount; i++) {
            String connectionId = "scale-test-conn-" + i;
            NettyOutbound outbound = mock(NettyOutbound.class);
            when(outbound.sendString(any())).thenReturn(outbound);
            when(outbound.then()).thenReturn(Mono.empty());
            
            ConnectionActor actor = actorSystem.createConnectionActor(connectionId, outbound);
            connectionIds.add(connectionId);
            actors.add(actor);
        }
        
        // Wait for all actors to be healthy
        await().atMost(Duration.ofSeconds(10))
               .until(() -> actors.stream().allMatch(ConnectionActor::isHealthy));
        
        assertEquals(connectionCount, actorSystem.getActiveConnectionCount());
        
        Instant startTime = Instant.now();
        
        // Send messages to all connections concurrently
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int connIndex = 0; connIndex < connectionCount; connIndex++) {
            final String connectionId = connectionIds.get(connIndex);
            for (int msgIndex = 0; msgIndex < messagesPerConnection; msgIndex++) {
                final int messageId = msgIndex;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    String xmlContent = "<test id='" + messageId + "'>Content for " + connectionId + "</test>";
                    IncomingXmlMessage message = IncomingXmlMessage.of(connectionId, xmlContent);
                    actorSystem.tellConnectionActor(connectionId, message);
                }, executor);
                futures.add(future);
            }
        }
        
        // Wait for all messages to be sent
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        
        // Allow time for processing
        await().atMost(Duration.ofSeconds(15))
               .pollDelay(Duration.ofMillis(200))
               .until(() -> true);
        
        Instant endTime = Instant.now();
        long totalTimeMs = Duration.between(startTime, endTime).toMillis();
        int totalMessages = connectionCount * messagesPerConnection;
        double messagesPerSecond = (totalMessages * 1000.0) / totalTimeMs;
        
        System.out.printf("Processed %d messages across %d connections in %d ms (%.2f msg/sec)%n", 
                         totalMessages, connectionCount, totalTimeMs, messagesPerSecond);
        
        // All actors should still be healthy after concurrent load
        assertTrue(actors.stream().allMatch(ConnectionActor::isHealthy), 
                  "All actors should remain healthy under concurrent load");
        
        // Performance assertion: should maintain reasonable throughput even with many connections
        assertTrue(messagesPerSecond > 500, 
                  String.format("Multi-connection throughput too low: %.2f msg/sec", messagesPerSecond));
        
        // Cleanup
        connectionIds.forEach(actorSystem::removeConnectionActor);
    }

    @Test
    @DisplayName("Actor system memory usage under load")
    void testMemoryUsageUnderLoad() {
        Runtime runtime = Runtime.getRuntime();
        
        // Force garbage collection and measure baseline
        System.gc();
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        
        int connectionCount = 50;
        List<String> connectionIds = new ArrayList<>();
        
        // Create many connections
        for (int i = 0; i < connectionCount; i++) {
            String connectionId = "memory-test-conn-" + i;
            NettyOutbound outbound = mock(NettyOutbound.class);
            when(outbound.sendString(any())).thenReturn(outbound);
            when(outbound.then()).thenReturn(Mono.empty());
            
            ConnectionActor actor = actorSystem.createConnectionActor(connectionId, outbound);
            connectionIds.add(connectionId);
        }
        
        await().atMost(Duration.ofSeconds(10))
               .until(() -> actorSystem.getActiveConnectionCount() == connectionCount);
        
        // Send messages to create internal state
        connectionIds.forEach(connectionId -> {
            for (int i = 0; i < 10; i++) {
                IncomingXmlMessage message = IncomingXmlMessage.of(connectionId, "<test>Memory test " + i + "</test>");
                actorSystem.tellConnectionActor(connectionId, message);
            }
        });
        
        // Allow processing and measure memory
        await().atMost(Duration.ofSeconds(5))
               .pollDelay(Duration.ofMillis(100))
               .until(() -> true);
        
        long loadMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = loadMemory - baselineMemory;
        long memoryPerConnection = memoryIncrease / connectionCount;
        
        System.out.printf("Memory usage: baseline=%d bytes, under load=%d bytes, increase=%d bytes%n", 
                         baselineMemory, loadMemory, memoryIncrease);
        System.out.printf("Memory per connection: %d bytes%n", memoryPerConnection);
        
        // Memory usage should be reasonable (less than 1MB per connection)
        assertTrue(memoryPerConnection < 1024 * 1024, 
                  String.format("Memory usage per connection too high: %d bytes", memoryPerConnection));
        
        // Cleanup and verify memory recovery
        connectionIds.forEach(actorSystem::removeConnectionActor);
        
        await().atMost(Duration.ofSeconds(5))
               .until(() -> actorSystem.getActiveConnectionCount() == 0);
        
        System.gc();
        await().atMost(Duration.ofSeconds(2))
               .pollDelay(Duration.ofMillis(100))
               .until(() -> true);
        
        long cleanupMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryRecovered = loadMemory - cleanupMemory;
        
        System.out.printf("Memory after cleanup: %d bytes, recovered: %d bytes%n", 
                         cleanupMemory, memoryRecovered);
        
        // Should recover most memory (at least 80%)
        assertTrue(memoryRecovered > memoryIncrease * 0.8, 
                  "Should recover most allocated memory after cleanup");
    }

    @Test
    @DisplayName("Actor response latency under various loads")
    void testActorResponseLatency() {
        String connectionId = "latency-test-conn";
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);
        
        // Test latency with different message sizes and frequencies
        int[] messageSizes = {100, 1000, 10000}; // bytes
        int messagesPerSize = 100;
        
        for (int messageSize : messageSizes) {
            String xmlContent = "<test>" + "x".repeat(messageSize - 13) + "</test>";
            List<Long> latencies = new ArrayList<>();
            
            for (int i = 0; i < messagesPerSize; i++) {
                long startNanos = System.nanoTime();
                
                IncomingXmlMessage message = IncomingXmlMessage.of(connectionId, xmlContent);
                actorSystem.tellConnectionActor(connectionId, message);
                
                // For this test, we measure the time to queue the message
                // (actual processing latency would require more complex instrumentation)
                long endNanos = System.nanoTime();
                latencies.add(endNanos - startNanos);
            }
            
            long avgLatencyNanos = latencies.stream().mapToLong(Long::longValue).sum() / latencies.size();
            long maxLatencyNanos = latencies.stream().mapToLong(Long::longValue).max().orElse(0);
            
            double avgLatencyMs = avgLatencyNanos / 1_000_000.0;
            double maxLatencyMs = maxLatencyNanos / 1_000_000.0;
            
            System.out.printf("Message size %d bytes: avg latency %.3f ms, max latency %.3f ms%n", 
                             messageSize, avgLatencyMs, maxLatencyMs);
            
            // Latency assertions: should be very low for message queuing
            assertTrue(avgLatencyMs < 1.0, 
                      String.format("Average latency too high for size %d: %.3f ms", messageSize, avgLatencyMs));
            assertTrue(maxLatencyMs < 10.0, 
                      String.format("Max latency too high for size %d: %.3f ms", messageSize, maxLatencyMs));
        }
        
        actorSystem.removeConnectionActor(connectionId);
    }

    @Test
    @DisplayName("System stability under sustained load")
    void testSustainedLoadStability() {
        String connectionId = "stability-test-conn";
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);
        
        // Run sustained load for a period of time
        Duration testDuration = Duration.ofSeconds(30);
        Instant testStart = Instant.now();
        AtomicInteger messagesSent = new AtomicInteger(0);
        
        // Create a sustained load generator
        Flux.interval(Duration.ofMillis(10)) // 100 messages per second
            .takeUntilOther(Mono.delay(testDuration))
            .subscribe(tick -> {
                String xmlContent = "<sustained>Message " + tick + "</sustained>";
                IncomingXmlMessage message = IncomingXmlMessage.of(connectionId, xmlContent);
                actorSystem.tellConnectionActor(connectionId, message);
                messagesSent.incrementAndGet();
            });
        
        // Wait for test completion
        await().atMost(testDuration.plusSeconds(5))
               .until(() -> Duration.between(testStart, Instant.now()).compareTo(testDuration) >= 0);
        
        int totalMessages = messagesSent.get();
        double actualRate = totalMessages / (double) testDuration.getSeconds();
        
        System.out.printf("Sustained load test: %d messages over %d seconds (%.2f msg/sec)%n", 
                         totalMessages, testDuration.getSeconds(), actualRate);
        
        // Actor should remain healthy throughout sustained load
        assertTrue(actor.isHealthy(), "Actor should remain healthy under sustained load");
        
        // Should have processed most of the intended messages
        assertTrue(totalMessages > testDuration.getSeconds() * 80, 
                  "Should maintain reasonable message rate under sustained load");
        
        actorSystem.removeConnectionActor(connectionId);
    }

    @Test
    @DisplayName("Actor system recovery after overload")
    void testOverloadRecovery() {
        String connectionId = "overload-test-conn";
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);
        
        // Create intentional overload by sending messages very rapidly
        int overloadMessages = 10000;
        for (int i = 0; i < overloadMessages; i++) {
            String xmlContent = "<overload>Message " + i + "</overload>";
            IncomingXmlMessage message = IncomingXmlMessage.of(connectionId, xmlContent);
            actorSystem.tellConnectionActor(connectionId, message);
        }
        
        // Allow system to process the overload
        await().atMost(Duration.ofSeconds(15))
               .pollDelay(Duration.ofMillis(500))
               .until(() -> true);
        
        // Actor should still be healthy after overload
        assertTrue(actor.isHealthy(), "Actor should survive and recover from overload");
        
        // Test normal operation after overload
        String normalXml = "<normal>Recovery test message</normal>";
        IncomingXmlMessage normalMessage = IncomingXmlMessage.of(connectionId, normalXml);
        
        // Should be able to process normal messages after overload
        assertDoesNotThrow(() -> {
            actorSystem.tellConnectionActor(connectionId, normalMessage);
        }, "Should handle normal messages after overload recovery");
        
        actorSystem.removeConnectionActor(connectionId);
    }
}