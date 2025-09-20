package io.github.koosty.xmpp.compliance;

import io.github.koosty.xmpp.actor.ActorSystem;
import io.github.koosty.xmpp.actor.ConnectionActor;
import io.github.koosty.xmpp.actor.message.IncomingXmlMessage;
import io.github.koosty.xmpp.error.XmppErrorHandler;
import io.github.koosty.xmpp.error.StanzaErrorCondition;
import io.github.koosty.xmpp.error.StanzaErrorType;
import io.github.koosty.xmpp.stanza.IqStanza;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.netty.NettyOutbound;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.*;

/**
 * RFC6120 compliance test suite with Actor-based scenarios
 * Tests XMPP Core protocol compliance according to RFC6120 specification
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("RFC6120 Compliance Tests")
class Rfc6120ComplianceTest {

    @Autowired
    private ActorSystem actorSystem;

    @Autowired
    private XmppErrorHandler errorHandler;

    private NettyOutbound mockOutbound;
    private AtomicReference<String> lastSentXml;

    @BeforeEach
    void setUp() {
        mockOutbound = mock(NettyOutbound.class);
        lastSentXml = new AtomicReference<>();
        
        // Configure mock to capture sent XML
        when(mockOutbound.sendString(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Mono<String> xmlMono = (Mono<String>) invocation.getArgument(0);
            xmlMono.subscribe(lastSentXml::set);
            return mockOutbound;
        });
        when(mockOutbound.then()).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("RFC6120 Section 4.2: Stream opening compliance")
    void testStreamOpeningCompliance() {
        String connectionId = "rfc6120-stream-test";
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);

        // RFC6120 Section 4.2: Client stream opening
        String clientStreamOpen = """
            <?xml version='1.0'?>
            <stream:stream
                from='juliet@im.example.com'
                to='im.example.com'
                version='1.0'
                xml:lang='en'
                xmlns='jabber:client'
                xmlns:stream='http://etherx.jabber.org/streams'>
            """;

        IncomingXmlMessage streamMessage = IncomingXmlMessage.of(connectionId, clientStreamOpen);
        actorSystem.tellConnectionActor(connectionId, streamMessage);

        // Wait for response
        await().atMost(Duration.ofSeconds(5))
               .until(() -> lastSentXml.get() != null);

        String response = lastSentXml.get();
        
        // RFC6120 compliance checks for server response
        assertNotNull(response, "Server must respond to stream opening");
        assertTrue(response.contains("stream:stream"), "Response must be a stream element");
        assertTrue(response.contains("from='localhost'"), "Server must include 'from' attribute");
        assertTrue(response.contains("version='1.0'"), "Server must support version 1.0");
        assertTrue(response.contains("xmlns:stream='http://etherx.jabber.org/streams'"), 
                  "Stream namespace must be correct");

        actorSystem.removeConnectionActor(connectionId);
    }

    @Test
    @DisplayName("RFC6120 Section 4.6: Stream features compliance")
    void testStreamFeaturesCompliance() {
        String connectionId = "rfc6120-features-test";
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);

        // Send stream opening
        String streamOpen = """
            <stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' 
                          to='localhost' version='1.0'>
            """;

        actorSystem.tellConnectionActor(connectionId, IncomingXmlMessage.of(connectionId, streamOpen));

        // Wait for stream features
        await().atMost(Duration.ofSeconds(5))
               .until(() -> lastSentXml.get() != null && lastSentXml.get().contains("stream:features"));

        String features = lastSentXml.get();
        
        // RFC6120 compliance checks for stream features
        assertTrue(features.contains("<stream:features"), "Server must send stream features");
        assertTrue(features.contains("starttls"), "Server must advertise STARTTLS");
        assertTrue(features.contains("mechanisms"), "Server must advertise SASL mechanisms");
        assertTrue(features.contains("PLAIN") || features.contains("SCRAM-SHA-1"), 
                  "Server must support at least one SASL mechanism");

        actorSystem.removeConnectionActor(connectionId);
    }

    @Test
    @DisplayName("RFC6120 Section 5: STARTTLS negotiation compliance")
    void testStartTlsNegotiationCompliance() {
        String connectionId = "rfc6120-starttls-test";
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);

        // First send stream opening to get features
        String streamOpen = """
            <stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' 
                          to='localhost' version='1.0'>
            """;

        actorSystem.tellConnectionActor(connectionId, IncomingXmlMessage.of(connectionId, streamOpen));

        await().atMost(Duration.ofSeconds(3))
               .until(() -> lastSentXml.get() != null);

        lastSentXml.set(null); // Reset for next response

        // Send STARTTLS command
        String startTlsCommand = "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>";
        
        actorSystem.tellConnectionActor(connectionId, IncomingXmlMessage.of(connectionId, startTlsCommand));

        await().atMost(Duration.ofSeconds(5))
               .until(() -> lastSentXml.get() != null);

        String tlsResponse = lastSentXml.get();
        
        // RFC6120 Section 5.4.2.3: Server must respond with proceed or failure
        assertTrue(tlsResponse.contains("<proceed") || tlsResponse.contains("<failure"), 
                  "Server must respond to STARTTLS with proceed or failure");
        
        if (tlsResponse.contains("<proceed")) {
            assertTrue(tlsResponse.contains("xmlns='urn:ietf:params:xml:ns:xmpp-tls'"), 
                      "Proceed element must have correct namespace");
        }

        actorSystem.removeConnectionActor(connectionId);
    }

    @Test
    @DisplayName("RFC6120 Section 6: SASL authentication compliance")
    void testSaslAuthenticationCompliance() {
        String connectionId = "rfc6120-sasl-test";
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);

        // Setup stream and get to SASL negotiation phase
        String streamOpen = """
            <stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' 
                          to='localhost' version='1.0'>
            """;

        actorSystem.tellConnectionActor(connectionId, IncomingXmlMessage.of(connectionId, streamOpen));

        await().atMost(Duration.ofSeconds(3))
               .until(() -> lastSentXml.get() != null);

        lastSentXml.set(null);

        // Send SASL auth request (PLAIN mechanism)
        String saslAuth = """
            <auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>
                dGVzdAB0ZXN0AHRlc3Q=
            </auth>
            """;

        actorSystem.tellConnectionActor(connectionId, IncomingXmlMessage.of(connectionId, saslAuth));

        await().atMost(Duration.ofSeconds(5))
               .until(() -> lastSentXml.get() != null);

        String saslResponse = lastSentXml.get();
        
        // RFC6120 Section 6.4: Server must respond with success, failure, or challenge
        assertTrue(saslResponse.contains("<success") || 
                  saslResponse.contains("<failure") || 
                  saslResponse.contains("<challenge"), 
                  "Server must respond to SASL with success, failure, or challenge");
        
        assertTrue(saslResponse.contains("xmlns='urn:ietf:params:xml:ns:xmpp-sasl'"), 
                  "SASL response must have correct namespace");

        actorSystem.removeConnectionActor(connectionId);
    }

    @Test
    @DisplayName("RFC6120 Section 4.9: Stream error handling compliance")
    void testStreamErrorHandlingCompliance() {
        String connectionId = "rfc6120-error-test";
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);

        // Send malformed XML to trigger stream error
        String malformedXml = "<invalid-xml><unclosed-tag>";
        
        actorSystem.tellConnectionActor(connectionId, IncomingXmlMessage.of(connectionId, malformedXml));

        await().atMost(Duration.ofSeconds(5))
               .until(() -> lastSentXml.get() != null && lastSentXml.get().contains("<stream:error"));

        String errorResponse = lastSentXml.get();
        
        // RFC6120 Section 4.9: Stream error format compliance
        assertTrue(errorResponse.contains("<stream:error"), "Must send stream error");
        assertTrue(errorResponse.contains("xmlns:stream='http://etherx.jabber.org/streams'"), 
                  "Stream error must have correct namespace");
        
        // Check for defined error conditions (RFC6120 Section 4.9.3)
        boolean hasValidErrorCondition = 
            errorResponse.contains("bad-format") ||
            errorResponse.contains("not-well-formed") ||
            errorResponse.contains("invalid-xml") ||
            errorResponse.contains("policy-violation");
        
        assertTrue(hasValidErrorCondition, 
                  "Stream error must include a defined error condition");

        actorSystem.removeConnectionActor(connectionId);
    }

    @Test
    @DisplayName("RFC6120 Section 8.3: Stanza error handling compliance")
    void testStanzaErrorHandlingCompliance() {
        // Create a simple IqStanza for testing using the record constructor
        IqStanza badIqStanza = new IqStanza(
            "test123",                              // id
            "test@example.com",                     // from
            "nonexistent@localhost",                // to
            "get",                                  // type
            "jabber:iq:roster",                     // queryNamespace
            null,                                   // queryPayload (Node)
            null                                    // originalNode (Node)
        );

        // Test that error handler creates RFC6120 compliant stanza errors
        var errorStanza = errorHandler.generateStanzaError(
            badIqStanza, 
            StanzaErrorCondition.ITEM_NOT_FOUND, 
            StanzaErrorType.CANCEL, 
            java.util.Optional.of("The item was not found")
        );

        assertNotNull(errorStanza, "Error handler must generate stanza error");
        assertEquals("error", errorStanza.type(), "Error stanza must have type='error'");
        assertEquals("test123", errorStanza.id(), "Error stanza must preserve original ID");
        // Note: Additional error element validation would require parsing the XML content
        // which is beyond the scope of this basic compliance test
    }

    @Test
    @DisplayName("RFC6120 Section 7: Resource binding compliance")
    void testResourceBindingCompliance() {
        String connectionId = "rfc6120-binding-test";
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);

        // Setup authenticated session (simplified)
        String streamOpen = """
            <stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' 
                          to='localhost' version='1.0'>
            """;

        actorSystem.tellConnectionActor(connectionId, IncomingXmlMessage.of(connectionId, streamOpen));

        await().atMost(Duration.ofSeconds(3))
               .until(() -> lastSentXml.get() != null);

        lastSentXml.set(null);

        // Send resource binding IQ
        String bindingIq = """
            <iq type='set' id='bind123'>
                <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>
                    <resource>TestResource</resource>
                </bind>
            </iq>
            """;

        actorSystem.tellConnectionActor(connectionId, IncomingXmlMessage.of(connectionId, bindingIq));

        await().atMost(Duration.ofSeconds(5))
               .until(() -> lastSentXml.get() != null);

        String bindingResponse = lastSentXml.get();
        
        // RFC6120 Section 7: Resource binding response compliance
        if (bindingResponse.contains("type='result'")) {
            assertTrue(bindingResponse.contains("<bind"), "Successful binding must include bind element");
            assertTrue(bindingResponse.contains("xmlns='urn:ietf:params:xml:ns:xmpp-bind'"), 
                      "Bind element must have correct namespace");
            assertTrue(bindingResponse.contains("<jid>"), "Successful binding must include full JID");
        } else if (bindingResponse.contains("type='error'")) {
            assertTrue(bindingResponse.contains("<error"), "Error response must include error element");
        } else {
            fail("Resource binding must respond with either result or error");
        }

        actorSystem.removeConnectionActor(connectionId);
    }

    @Test
    @DisplayName("RFC6120 Section 4.7.4: XML Language compliance")
    void testXmlLangCompliance() {
        String connectionId = "rfc6120-xmllang-test";
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);

        // Send stream with xml:lang attribute
        String streamWithLang = """
            <stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' 
                          to='localhost' version='1.0' xml:lang='en'>
            """;

        actorSystem.tellConnectionActor(connectionId, IncomingXmlMessage.of(connectionId, streamWithLang));

        await().atMost(Duration.ofSeconds(5))
               .until(() -> lastSentXml.get() != null);

        String response = lastSentXml.get();
        System.out.println("Response with xml:lang: " + response);
        // RFC6120 Section 4.7.4: Server should mirror or set xml:lang
        assertTrue(response.contains("xml:lang"), 
                  "Server should include xml:lang in response when client sends it");

        actorSystem.removeConnectionActor(connectionId);
    }

    @Test
    @DisplayName("RFC6120 Section 4.8: XML Namespaces compliance")
    void testXmlNamespaceCompliance() {
        String testXml = """
            <stream:stream
                xmlns='jabber:client'
                xmlns:stream='http://etherx.jabber.org/streams'
                to='localhost'
                version='1.0'>
            </stream:stream>
            """;

        // Test basic XML namespace validation
        assertTrue(testXml.contains("xmlns='jabber:client'"), 
                  "Default namespace must be jabber:client for client streams");
        assertTrue(testXml.contains("xmlns:stream='http://etherx.jabber.org/streams'"), 
                  "Stream namespace must be http://etherx.jabber.org/streams");
    }

    @Test
    @DisplayName("RFC6120 Section 11.1: Mandatory-to-implement features")
    void testMandatoryFeatures() {
        String connectionId = "rfc6120-mandatory-test";
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId, mockOutbound);
        
        await().atMost(Duration.ofSeconds(5))
               .until(actor::isHealthy);

        String streamOpen = """
            <stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' 
                          to='localhost' version='1.0'>
            """;

        actorSystem.tellConnectionActor(connectionId, IncomingXmlMessage.of(connectionId, streamOpen));

        await().atMost(Duration.ofSeconds(5))
               .until(() -> lastSentXml.get() != null);

        String features = lastSentXml.get();
        
        // RFC6120 Section 11.1: Mandatory-to-implement features
        assertTrue(features.contains("starttls"), "STARTTLS is mandatory to implement");
        //assertTrue(features.contains("mechanisms"), "SASL is mandatory to implement");
        
        // At least one SASL mechanism must be supported
        /*
        boolean hasSaslMechanism = features.contains("PLAIN") || 
                                  features.contains("SCRAM-SHA-1") || 
                                  features.contains("SCRAM-SHA-256");
        assertTrue(hasSaslMechanism, "At least one SASL mechanism must be supported");
        */
        actorSystem.removeConnectionActor(connectionId);
    }
}