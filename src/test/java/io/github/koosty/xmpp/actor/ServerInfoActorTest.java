package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.ActorMessage;
import io.github.koosty.xmpp.actor.message.GenericActorMessage;
import io.github.koosty.xmpp.actor.message.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServerInfoActorTest {
    
    private ServerInfoActor serverInfoActor;
    
    @BeforeEach
    void setUp() {
        serverInfoActor = new ServerInfoActor("testserver.com", "1.0.0");
    }
    
    @Test
    void handleDiscoInfoRequest_shouldReturnDiscoInfo() {
        GenericActorMessage request = new GenericActorMessage(
            MessageType.DISCO_INFO_REQUEST.name(),
            "TestConnection",
            new HashMap<>(Map.of(
                "requestId", "disco-123",
                "fromJid", "client@example.com/resource"
            )) {{
                put("node", null);
            }}
        );
        
        Mono<ActorMessage> responseMono = serverInfoActor.processMessage(request);
        
        StepVerifier.create(responseMono)
            .assertNext(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                assertEquals("DISCO_INFO_RESPONSE", genericResponse.messageType());
                assertTrue((Boolean) genericResponse.payload().get("success"));
                
                String discoInfo = (String) genericResponse.payload().get("discoInfo");
                assertNotNull(discoInfo);
                assertTrue(discoInfo.contains("http://jabber.org/protocol/disco#info"));
                assertTrue(discoInfo.contains("testserver.com"));
                assertTrue(discoInfo.contains("identity"));
                assertTrue(discoInfo.contains("feature"));
                assertTrue(discoInfo.contains("urn:xmpp:ping"));
                assertTrue(discoInfo.contains("jabber:iq:version"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }
    
    @Test
    void handleDiscoInfoRequestWithNode_shouldIncludeNode() {
        GenericActorMessage request = new GenericActorMessage(
            MessageType.DISCO_INFO_REQUEST.name(),
            "TestConnection",
            Map.of(
                "requestId", "disco-456",
                "fromJid", "client@example.com/resource",
                "node", "test-node"
            )
        );
        
        Mono<ActorMessage> responseMono = serverInfoActor.processMessage(request);
        
        StepVerifier.create(responseMono)
            .assertNext(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                assertEquals("DISCO_INFO_RESPONSE", genericResponse.messageType());
                assertTrue((Boolean) genericResponse.payload().get("success"));
                
                String discoInfo = (String) genericResponse.payload().get("discoInfo");
                assertNotNull(discoInfo);
                assertTrue(discoInfo.contains("node=\"test-node\""));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }
    
    @Test
    void handleDiscoItemsRequest_shouldReturnItems() {
        GenericActorMessage request = new GenericActorMessage(
            MessageType.DISCO_ITEMS_REQUEST.name(),
            "TestConnection",
            new HashMap<>(Map.of(
                "requestId", "items-123",
                "fromJid", "client@example.com/resource"
            )) {{
                put("node", null);
            }}
        );
        
        Mono<ActorMessage> responseMono = serverInfoActor.processMessage(request);
        
        StepVerifier.create(responseMono)
            .assertNext(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                assertEquals("DISCO_ITEMS_RESPONSE", genericResponse.messageType());
                assertTrue((Boolean) genericResponse.payload().get("success"));
                
                String discoItems = (String) genericResponse.payload().get("discoItems");
                assertNotNull(discoItems);
                assertTrue(discoItems.contains("http://jabber.org/protocol/disco#items"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }
    
    @Test
    void handleDiscoItemsRequestWithNode_shouldIncludeNode() {
        GenericActorMessage request = new GenericActorMessage(
            MessageType.DISCO_ITEMS_REQUEST.name(),
            "TestConnection",
            Map.of(
                "requestId", "items-456",
                "fromJid", "client@example.com/resource",
                "node", "test-node"
            )
        );
        
        Mono<ActorMessage> responseMono = serverInfoActor.processMessage(request);
        
        StepVerifier.create(responseMono)
            .assertNext(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                assertEquals("DISCO_ITEMS_RESPONSE", genericResponse.messageType());
                assertTrue((Boolean) genericResponse.payload().get("success"));
                
                String discoItems = (String) genericResponse.payload().get("discoItems");
                assertNotNull(discoItems);
                assertTrue(discoItems.contains("node=\"test-node\""));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }
    
    @Test
    void handleUnsupportedMessage_shouldReturnError() {
        GenericActorMessage request = new GenericActorMessage(
            "UNSUPPORTED_MESSAGE",
            "TestConnection",
            Map.of("test", "data")
        );
        
        Mono<ActorMessage> responseMono = serverInfoActor.processMessage(request);
        
        StepVerifier.create(responseMono)
            .expectError(IllegalArgumentException.class)
            .verify(Duration.ofSeconds(1));
    }
    
    @Test
    void handleMultipleRequests_shouldHandleSequentially() {
        GenericActorMessage request1 = new GenericActorMessage(
            MessageType.DISCO_INFO_REQUEST.name(),
            "TestConnection1",
            new HashMap<>(Map.of(
                "requestId", "disco-1",
                "fromJid", "client1@example.com/resource"
            )) {{
                put("node", null);
            }}
        );
        
        GenericActorMessage request2 = new GenericActorMessage(
            MessageType.DISCO_ITEMS_REQUEST.name(),
            "TestConnection2",
            new HashMap<>(Map.of(
                "requestId", "disco-2",
                "fromJid", "client2@example.com/resource"
            )) {{
                put("node", null);
            }}
        );
        
        Mono<ActorMessage> response1 = serverInfoActor.processMessage(request1);
        Mono<ActorMessage> response2 = serverInfoActor.processMessage(request2);
        
        // Verify first response
        StepVerifier.create(response1)
            .assertNext(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                assertEquals("DISCO_INFO_RESPONSE", genericResponse.messageType());
                assertTrue((Boolean) genericResponse.payload().get("success"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
        
        // Verify second response
        StepVerifier.create(response2)
            .assertNext(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                assertEquals("DISCO_ITEMS_RESPONSE", genericResponse.messageType());
                assertTrue((Boolean) genericResponse.payload().get("success"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }
    
    @Test
    void handleMalformedRequest_shouldReturnError() {
        GenericActorMessage request = new GenericActorMessage(
            MessageType.DISCO_INFO_REQUEST.name(),
            "TestConnection",
            Map.of() // Empty payload - missing required fields
        );
        
        Mono<ActorMessage> responseMono = serverInfoActor.processMessage(request);
        
        StepVerifier.create(responseMono)
            .assertNext(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                assertEquals("ERROR_RESPONSE", genericResponse.messageType());
                assertFalse((Boolean) genericResponse.payload().get("success"));
                String message = (String) genericResponse.payload().get("message");
                assertTrue(message.contains("Failed to process server info request"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }
    
    @Test
    void getSupportedFeatures_shouldReturnFeatureSet() {
        var features = serverInfoActor.getSupportedFeatures();
        
        assertNotNull(features);
        assertFalse(features.isEmpty());
        assertTrue(features.contains("urn:xmpp:ping"));
        assertTrue(features.contains("jabber:iq:version"));
        assertTrue(features.contains("http://jabber.org/protocol/disco#info"));
        assertTrue(features.contains("urn:ietf:params:xml:ns:xmpp-sasl"));
    }
    
    @Test
    void getServerIdentities_shouldReturnIdentityMap() {
        var identities = serverInfoActor.getServerIdentities();
        
        assertNotNull(identities);
        assertFalse(identities.isEmpty());
        assertTrue(identities.containsKey("server/im"));
        assertEquals("testserver.com XMPP Server", identities.get("server/im"));
    }
    
    @Test
    void addSupportedFeature_shouldUpdateFeatureList() {
        String newFeature = "custom:feature";
        
        // Feature should not be supported initially
        assertFalse(serverInfoActor.getSupportedFeatures().contains(newFeature));
        
        // Add the feature
        serverInfoActor.addSupportedFeature(newFeature);
        
        // Feature should now be supported
        assertTrue(serverInfoActor.getSupportedFeatures().contains(newFeature));
    }
    
    @Test
    void removeSupportedFeature_shouldUpdateFeatureList() {
        String feature = "urn:xmpp:ping";
        
        // Feature should be supported initially
        assertTrue(serverInfoActor.getSupportedFeatures().contains(feature));
        
        // Remove the feature
        serverInfoActor.removeSupportedFeature(feature);
        
        // Feature should no longer be supported
        assertFalse(serverInfoActor.getSupportedFeatures().contains(feature));
    }
    
    @Test
    void addServerIdentity_shouldUpdateIdentityList() {
        serverInfoActor.addServerIdentity("gateway", "sms", "SMS Gateway");
        
        var identities = serverInfoActor.getServerIdentities();
        assertTrue(identities.containsKey("gateway/sms"));
        assertEquals("SMS Gateway", identities.get("gateway/sms"));
    }
    
    @Test
    void handleServerInfoRequest_shouldReturnServerInfo() {
        GenericActorMessage request = new GenericActorMessage(
            MessageType.SERVER_INFO_REQUEST.name(),
            "TestConnection",
            Map.of()
        );
        
        Mono<ActorMessage> responseMono = serverInfoActor.processMessage(request);
        
        StepVerifier.create(responseMono)
            .assertNext(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                assertEquals("SERVER_INFO_RESPONSE", genericResponse.messageType());
                assertTrue((Boolean) genericResponse.payload().get("success"));
                
                @SuppressWarnings("unchecked")
                Map<String, Object> serverInfo = (Map<String, Object>) genericResponse.payload().get("serverInfo");
                assertNotNull(serverInfo);
                assertEquals("testserver.com", serverInfo.get("name"));
                assertEquals("1.0.0", serverInfo.get("version"));
                
                @SuppressWarnings("unchecked")
                var features = (java.util.ArrayList<String>) serverInfo.get("features");
                assertNotNull(features);
                assertTrue(features.contains("urn:xmpp:ping"));
                assertTrue(features.contains("jabber:iq:version"));
                assertTrue(features.contains("http://jabber.org/protocol/disco#info"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }
    
    @Test
    void handleFeatureQuery_shouldReturnFeatureSupport() {
        GenericActorMessage request = new GenericActorMessage(
            MessageType.FEATURE_QUERY.name(),
            "TestConnection",
            Map.of("feature", "urn:xmpp:ping")
        );
        
        Mono<ActorMessage> responseMono = serverInfoActor.processMessage(request);
        
        StepVerifier.create(responseMono)
            .assertNext(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                assertEquals("FEATURE_QUERY_RESPONSE", genericResponse.messageType());
                assertTrue((Boolean) genericResponse.payload().get("success"));
                
                assertEquals("urn:xmpp:ping", genericResponse.payload().get("feature"));
                assertTrue((Boolean) genericResponse.payload().get("supported"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }
    
    @Test
    void handleFeatureQuery_unsupportedFeature_shouldReturnFalse() {
        GenericActorMessage request = new GenericActorMessage(
            MessageType.FEATURE_QUERY.name(),
            "TestConnection",
            Map.of("feature", "unsupported:feature")
        );
        
        Mono<ActorMessage> responseMono = serverInfoActor.processMessage(request);
        
        StepVerifier.create(responseMono)
            .assertNext(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                assertEquals("FEATURE_QUERY_RESPONSE", genericResponse.messageType());
                assertTrue((Boolean) genericResponse.payload().get("success"));
                
                assertEquals("unsupported:feature", genericResponse.payload().get("feature"));
                assertFalse((Boolean) genericResponse.payload().get("supported"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }
}