package io.github.koosty.xmpp.integration;

import io.github.koosty.xmpp.actor.ActorSystem;
import io.github.koosty.xmpp.actor.ConnectionActor;
import io.github.koosty.xmpp.actor.message.IncomingXmlMessage;
import io.github.koosty.xmpp.actor.message.GenericActorMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.netty.NettyOutbound;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.*;

/**
 * Integration tests for Actor supervision and fault tolerance
 * Tests the hybrid architecture's resilience to failures
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Actor System Integration Tests")
class ActorSystemIntegrationTest {

    @Autowired
    private ActorSystem actorSystem;

    private NettyOutbound mockOutbound;

    @BeforeEach
    void setUp() {
        mockOutbound = mock(NettyOutbound.class);
        
        // Configure mock to return a Mono for sendString calls
        when(mockOutbound.sendString(any())).thenReturn(mockOutbound);
        when(mockOutbound.then()).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Actor system creates and manages connection actors")
    void testConnectionActorManagement() {
        String connectionId = "test-connection-1";
        
        // Create connection actor through actor system
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        assertNotNull(actor);
        assertEquals(connectionId, actor.getConnectionId());
        
        // Verify actor is accessible through actor system
        ConnectionActor retrievedActor = actorSystem.getConnectionActor(connectionId);
        assertEquals(actor, retrievedActor);
        
        // Verify actor is healthy and running
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);
        
        assertTrue(actor.isHealthy());
        
        // Clean up
        actorSystem.removeConnectionActor(connectionId);
        
        // Wait for cleanup
        await().atMost(Duration.ofSeconds(2))
               .until(() -> !actor.isHealthy());
        
        assertFalse(actor.isHealthy());
    }

    @Test
    @DisplayName("Actor system handles multiple concurrent connections")
    void testMultipleConnectionActors() {
        String connectionId1 = "concurrent-conn-1";
        String connectionId2 = "concurrent-conn-2";
        String connectionId3 = "concurrent-conn-3";
        
        NettyOutbound mockOutbound1 = mock(NettyOutbound.class);
        NettyOutbound mockOutbound2 = mock(NettyOutbound.class);
        NettyOutbound mockOutbound3 = mock(NettyOutbound.class);
        
        // Configure mocks
        when(mockOutbound1.sendString(any())).thenReturn(mockOutbound1);
        when(mockOutbound1.then()).thenReturn(Mono.empty());
        when(mockOutbound2.sendString(any())).thenReturn(mockOutbound2);
        when(mockOutbound2.then()).thenReturn(Mono.empty());
        when(mockOutbound3.sendString(any())).thenReturn(mockOutbound3);
        when(mockOutbound3.then()).thenReturn(Mono.empty());
        
        // Create multiple connection actors
        ConnectionActor actor1 = actorSystem.createConnectionActor(connectionId1, mockOutbound1);
        ConnectionActor actor2 = actorSystem.createConnectionActor(connectionId2, mockOutbound2);
        ConnectionActor actor3 = actorSystem.createConnectionActor(connectionId3, mockOutbound3);
        
        // Wait for all actors to be healthy
        await().atMost(Duration.ofSeconds(5))
               .until(() -> actor1.isHealthy() && actor2.isHealthy() && actor3.isHealthy());
        
        // Verify all actors are managed by the system
        assertEquals(actor1, actorSystem.getConnectionActor(connectionId1));
        assertEquals(actor2, actorSystem.getConnectionActor(connectionId2));
        assertEquals(actor3, actorSystem.getConnectionActor(connectionId3));
        
        // Verify active connection count
        assertEquals(3, actorSystem.getActiveConnectionCount());
        
        // Clean up
        actorSystem.removeConnectionActor(connectionId1);
        actorSystem.removeConnectionActor(connectionId2);
        actorSystem.removeConnectionActor(connectionId3);
        
        assertEquals(0, actorSystem.getActiveConnectionCount());
    }

    @Test
    @DisplayName("Actor system supports message routing between actors")
    void testMessageRouting() {
        String connectionId = "routing-test-connection";
        
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);
        
        // Send message through actor system
        IncomingXmlMessage message = IncomingXmlMessage.of(connectionId, "<stream:stream>");
        
        actorSystem.tellConnectionActor(connectionId, message);
        
        // Allow time for message processing
        await().atMost(Duration.ofSeconds(2))
               .pollDelay(Duration.ofMillis(100))
               .until(() -> true); // Simple wait for processing
        
        // Verify actor is still healthy after message processing
        assertTrue(actor.isHealthy());
        
        actorSystem.removeConnectionActor(connectionId);
    }

    @Test
    @DisplayName("Actor system handles broadcast messages to all connections")
    void testBroadcastMessaging() {
        String connectionId1 = "broadcast-conn-1";
        String connectionId2 = "broadcast-conn-2";
        
        NettyOutbound mockOutbound1 = mock(NettyOutbound.class);
        NettyOutbound mockOutbound2 = mock(NettyOutbound.class);
        
        when(mockOutbound1.sendString(any())).thenReturn(mockOutbound1);
        when(mockOutbound1.then()).thenReturn(Mono.empty());
        when(mockOutbound2.sendString(any())).thenReturn(mockOutbound2);
        when(mockOutbound2.then()).thenReturn(Mono.empty());
        
        ConnectionActor actor1 = actorSystem.createConnectionActor(connectionId1, mockOutbound1);
        ConnectionActor actor2 = actorSystem.createConnectionActor(connectionId2, mockOutbound2);
        
        await().atMost(Duration.ofSeconds(5))
               .until(() -> actor1.isHealthy() && actor2.isHealthy());
        
        // Broadcast message to all connections
        GenericActorMessage broadcastMessage = new GenericActorMessage("broadcast", "system", Map.of("data", "test-broadcast"));
        
        actorSystem.broadcastToAllConnections(broadcastMessage);
        
        // Allow time for message processing
        await().atMost(Duration.ofSeconds(2))
               .pollDelay(Duration.ofMillis(100))
               .until(() -> true);
        
        // Both actors should still be healthy after broadcast
        assertTrue(actor1.isHealthy());
        assertTrue(actor2.isHealthy());
        
        actorSystem.removeConnectionActor(connectionId1);
        actorSystem.removeConnectionActor(connectionId2);
    }

    @Test
    @DisplayName("Actor system handles connection actor failures gracefully")
    void testActorFailureHandling() {
        String connectionId = "failure-test-connection";
        
        // Create a NettyOutbound that will cause errors
        NettyOutbound faultyOutbound = mock(NettyOutbound.class);
        when(faultyOutbound.sendString(any())).thenThrow(new RuntimeException("Connection error"));
        
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, faultyOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);
        
        // Try to send a message that will fail
        actorSystem.tellConnectionActor(connectionId, 
            IncomingXmlMessage.of(connectionId, "<test/>"));
        
        // Allow time for failure processing
        await().atMost(Duration.ofSeconds(2))
               .pollDelay(Duration.ofMillis(100))
               .until(() -> true);
        
        // Actor should still be tracked by the system even after internal failures
        assertNotNull(actorSystem.getConnectionActor(connectionId));
        assertEquals(1, actorSystem.getActiveConnectionCount());
        
        actorSystem.removeConnectionActor(connectionId);
    }

    @Test
    @DisplayName("Actor system handles high load scenarios")
    void testHighLoadScenarios() {
        String connectionId = "load-test-connection";
        
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);
        
        // Send many messages rapidly
        int messageCount = 100;
        for (int i = 0; i < messageCount; i++) {
            IncomingXmlMessage message = IncomingXmlMessage.of(connectionId, "<msg id='" + i + "'/>");
            actorSystem.tellConnectionActor(connectionId, message);
        }
        
        // Allow time for all messages to be processed
        await().atMost(Duration.ofSeconds(10))
               .pollDelay(Duration.ofMillis(50))
               .until(() -> true);
        
        // Actor should still be healthy after processing many messages
        assertTrue(actor.isHealthy());
        assertEquals(1, actorSystem.getActiveConnectionCount());
        
        actorSystem.removeConnectionActor(connectionId);
    }
}