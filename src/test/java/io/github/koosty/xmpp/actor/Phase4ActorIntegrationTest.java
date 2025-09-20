package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.stanza.*;
import io.github.koosty.xmpp.connection.XmppConnection;
import io.github.koosty.xmpp.jid.JidValidator;
import io.github.koosty.xmpp.actor.message.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase4ActorIntegrationTest {
    
    @Mock
    private JidValidator jidValidator;
    
    @Mock
    private XmppConnection mockConnection;
    
    private MessageRoutingActor messageRoutingActor;
    private PresenceActor presenceActor;
    private IqProcessingActor iqProcessingActor;
    private ActorSupervision actorSupervision;
    private StanzaProcessor stanzaProcessor;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock JID validator
        when(jidValidator.parseJid(anyString())).thenReturn(java.util.Optional.of(
            new io.github.koosty.xmpp.jid.Jid("test", "example.com", "resource")
        ));
        
        // Setup mock connection
        when(mockConnection.sendStanza(any())).thenReturn(Mono.just(true));
        when(mockConnection.isActive()).thenReturn(true);
        when(mockConnection.getBoundJid()).thenReturn("test@example.com/resource");
        
        // Create actors
        messageRoutingActor = new MessageRoutingActor("message-router", jidValidator);
        presenceActor = new PresenceActor("presence-manager", jidValidator);
        iqProcessingActor = new IqProcessingActor("iq-processor", "Test Server", "1.0.0");
        actorSupervision = new ActorSupervision("supervisor");
        stanzaProcessor = new StanzaProcessor(jidValidator);
        
        // Start actors
        messageRoutingActor.start();
        presenceActor.start();
        iqProcessingActor.start();
        actorSupervision.start();
    }
    
    @Test
    @DisplayName("Should parse message stanza correctly")
    void shouldParseMessageStanza() throws Exception {
        String messageXml = """
            <message xmlns='jabber:client'
                     from='alice@example.com/desktop' 
                     to='bob@example.com' 
                     type='chat' 
                     id='msg1'>
                <body>Hello Bob!</body>
            </message>
            """;
        
        var result = stanzaProcessor.parseStanza(messageXml);
        
        assertTrue(result.isPresent());
        assertInstanceOf(MessageStanza.class, result.get());
        
        MessageStanza message = (MessageStanza) result.get();
        assertEquals("alice@example.com/desktop", message.from());
        assertEquals("bob@example.com", message.to());
        assertEquals("chat", message.type());
        assertEquals("msg1", message.id());
        assertEquals("Hello Bob!", message.body());
        assertTrue(message.isChatMessage());
    }
    
    @Test
    @DisplayName("Should parse presence stanza correctly")
    void shouldParsePresenceStanza() throws Exception {
        String presenceXml = """
            <presence xmlns='jabber:client'
                      from='alice@example.com/mobile' 
                      type='available'>
                <show>away</show>
                <status>Gone to lunch</status>
                <priority>5</priority>
            </presence>
            """;
        
        var result = stanzaProcessor.parseStanza(presenceXml);
        
        assertTrue(result.isPresent());
        assertInstanceOf(PresenceStanza.class, result.get());
        
        PresenceStanza presence = (PresenceStanza) result.get();
        assertEquals("alice@example.com/mobile", presence.from());
        assertEquals("available", presence.type());
        assertEquals("away", presence.show());
        assertEquals("Gone to lunch", presence.status());
        assertEquals(5, presence.priority());
        assertTrue(presence.isAvailable());
        assertEquals(PresenceStanza.PresenceShow.AWAY, presence.getShowEnum());
    }
    
    @Test
    @DisplayName("Should parse IQ stanza correctly")
    void shouldParseIqStanza() throws Exception {
        String iqXml = """
            <iq xmlns='jabber:client'
                from='alice@example.com/desktop' 
                to='example.com' 
                type='get' 
                id='ping1'>
                <ping xmlns='urn:xmpp:ping'/>
            </iq>
            """;
        
        var result = stanzaProcessor.parseStanza(iqXml);
        
        assertTrue(result.isPresent());
        assertInstanceOf(IqStanza.class, result.get());
        
        IqStanza iq = (IqStanza) result.get();
        assertEquals("alice@example.com/desktop", iq.from());
        assertEquals("example.com", iq.to());
        assertEquals("get", iq.type());
        assertEquals("ping1", iq.id());
        assertEquals("urn:xmpp:ping", iq.queryNamespace());
        assertTrue(iq.isGet());
        assertTrue(iq.isRequest());
    }
    
    @Test
    @DisplayName("Should route message to registered connection")
    void shouldRouteMessageToConnection() {
        // Register connection
        GenericActorMessage registerMsg = new GenericActorMessage(
            "register-connection", 
            "test-sender", 
            java.util.Map.of(
                "jid", "bob@example.com/desktop",
                "connection", mockConnection
            )
        );
        
        StepVerifier.create(messageRoutingActor.processMessage(registerMsg))
            .expectNextMatches(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                return genericResponse.getPayload("success", Boolean.class);
            })
            .verifyComplete();
        
        // Create and route message
        MessageStanza message = new MessageStanza(
            "msg1", 
            "alice@example.com/mobile", 
            "bob@example.com/desktop", 
            "chat", 
            "Hello Bob!", 
            null, 
            null, 
            null
        );
        
        RouteMessageRequest routeRequest = new RouteMessageRequest(message, "alice@example.com/mobile");
        
        StepVerifier.create(messageRoutingActor.processMessage(routeRequest))
            .expectNextMatches(response -> {
                RouteMessageResponse routeResponse = (RouteMessageResponse) response;
                return routeResponse.success() && "Message delivered".equals(routeResponse.message());
            })
            .verifyComplete();
        
        // Verify connection was called
        verify(mockConnection).sendStanza(message);
    }
    
    @Test
    @DisplayName("Should handle presence subscription workflow")
    void shouldHandlePresenceSubscription() {
        // Register connections
        GenericActorMessage registerAlice = new GenericActorMessage(
            "register-connection", 
            "test-sender", 
            java.util.Map.of(
                "jid", "alice@example.com/desktop",
                "connection", mockConnection
            )
        );
        
        presenceActor.tell(registerAlice);
        
        // Create subscription request
        PresenceStanza subscribeStanza = new PresenceStanza(
            "sub1", 
            "alice@example.com", 
            "bob@example.com", 
            "subscribe", 
            null, 
            null, 
            0, 
            null
        );
        
        GenericActorMessage presenceMsg = new GenericActorMessage(
            "presence-update", 
            "test-sender", 
            java.util.Map.of(
                "jid", "alice@example.com",
                "presence", subscribeStanza
            )
        );
        
        StepVerifier.create(presenceActor.processMessage(presenceMsg))
            .expectNextMatches(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                return genericResponse.getPayload("success", Boolean.class);
            })
            .verifyComplete();
    }
    
    @Test
    @DisplayName("Should process IQ ping request")
    void shouldProcessIqPingRequest() {
        // Register connection
        GenericActorMessage registerMsg = new GenericActorMessage(
            "register-connection", 
            "test-sender", 
            java.util.Map.of(
                "jid", "alice@example.com/desktop",
                "connection", mockConnection
            )
        );
        
        StepVerifier.create(iqProcessingActor.processMessage(registerMsg))
            .expectNextMatches(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                return genericResponse.getPayload("success", Boolean.class);
            })
            .verifyComplete();
        
        // Create ping IQ
        IqStanza pingStanza = IqStanza.createPing("example.com");
        IqStanza pingWithFrom = (IqStanza) pingStanza.withAddressing("alice@example.com/desktop", "example.com");
        
        GenericActorMessage iqMsg = new GenericActorMessage(
            "route-message", 
            "test-sender", 
            java.util.Map.of(
                "stanza", pingWithFrom,
                "sourceJid", "alice@example.com/desktop"
            )
        );
        
        StepVerifier.create(iqProcessingActor.processMessage(iqMsg))
            .expectNextMatches(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                return genericResponse.getPayload("success", Boolean.class);
            })
            .verifyComplete();
        
        // Verify response was sent
        verify(mockConnection, atLeastOnce()).sendStanza(any(IqStanza.class));
    }
    
    @Test
    @DisplayName("Should supervise actor lifecycle")
    void shouldSuperviseActorLifecycle() {
        // Create a mock actor to supervise
        AbstractActor mockActor = spy(new MessageRoutingActor("test-actor", jidValidator));
        
        // Start supervising
        GenericActorMessage superviseMsg = new GenericActorMessage(
            "actor-supervision", 
            "test-sender", 
            java.util.Map.of(
                "action", "supervise",
                "actorId", "test-actor",
                "actorType", "MessageRoutingActor",
                "actor", mockActor
            )
        );
        
        StepVerifier.create(actorSupervision.processMessage(superviseMsg))
            .expectNextMatches(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                return genericResponse.getPayload("success", Boolean.class);
            })
            .verifyComplete();
        
        // Test restart
        GenericActorMessage restartMsg = new GenericActorMessage(
            "actor-supervision", 
            "test-sender", 
            java.util.Map.of(
                "action", "restart",
                "actorId", "test-actor"
            )
        );
        
        StepVerifier.create(actorSupervision.processMessage(restartMsg))
            .expectNextMatches(response -> {
                GenericActorMessage genericResponse = (GenericActorMessage) response;
                return genericResponse.getPayload("success", Boolean.class);
            })
            .verifyComplete();
        
        // Verify actor methods were called
        verify(mockActor, atLeastOnce()).stop();
        verify(mockActor, atLeastOnce()).start();
    }
    
    @Test
    @DisplayName("Should create appropriate stanza response types")
    void shouldCreateStanzaResponses() {
        // Test message reply
        MessageStanza original = new MessageStanza(
            "msg1", 
            "alice@example.com", 
            "bob@example.com", 
            "chat", 
            "Hello", 
            null, 
            null, 
            null
        );
        
        MessageStanza reply = original.createReply("Hi back!");
        assertEquals("bob@example.com", reply.from());
        assertEquals("alice@example.com", reply.to());
        assertEquals("Hi back!", reply.body());
        
        // Test presence response
        PresenceStanza subscribePresence = new PresenceStanza(
            "sub1", 
            "alice@example.com", 
            "bob@example.com", 
            "subscribe", 
            null, 
            null, 
            0, 
            null
        );
        
        PresenceStanza subscribedResponse = subscribePresence.createSubscribedResponse();
        assertEquals("bob@example.com", subscribedResponse.from());
        assertEquals("alice@example.com", subscribedResponse.to());
        assertEquals("subscribed", subscribedResponse.type());
        
        // Test IQ response
        IqStanza pingRequest = IqStanza.createPing("example.com");
        IqStanza pingResponse = pingRequest.createResult();
        assertEquals(pingRequest.id(), pingResponse.id());
        assertEquals("result", pingResponse.type());
        assertTrue(pingResponse.isResult());
    }
    
    @Test
    @DisplayName("Should validate stanza processing workflow")
    void shouldValidateStanzaProcessingWorkflow() throws Exception {
        // Test complete workflow: parse -> validate -> route
        String messageXml = """
            <message xmlns='jabber:client'
                     from='alice@example.com/mobile' 
                     to='bob@example.com/desktop' 
                     type='chat' 
                     id='workflow-test'>
                <body>Test workflow message</body>
            </message>
            """;
        
        // Step 1: Parse stanza
        var parseResult = stanzaProcessor.parseStanza(messageXml);
        assertTrue(parseResult.isPresent());
        MessageStanza parsedMessage = (MessageStanza) parseResult.get();
        
        // Step 2: Validate stanza (basic validation)
        assertNotNull(parsedMessage.id());
        assertNotNull(parsedMessage.from());
        assertNotNull(parsedMessage.to());
        
        // Step 3: Register target connection
        GenericActorMessage registerMsg = new GenericActorMessage(
            "register-connection", 
            "test-sender", 
            java.util.Map.of(
                "jid", "bob@example.com/desktop",
                "connection", mockConnection
            )
        );
        
        messageRoutingActor.tell(registerMsg);
        
        // Step 4: Route message
        RouteMessageRequest routeRequest = new RouteMessageRequest(
            parsedMessage, 
            "alice@example.com/mobile"
        );
        
        StepVerifier.create(messageRoutingActor.processMessage(routeRequest))
            .expectNextMatches(response -> {
                RouteMessageResponse routeResponse = (RouteMessageResponse) response;
                return routeResponse.success();
            })
            .verifyComplete();
        
        // Verify end-to-end delivery
        verify(mockConnection).sendStanza(parsedMessage);
    }
}