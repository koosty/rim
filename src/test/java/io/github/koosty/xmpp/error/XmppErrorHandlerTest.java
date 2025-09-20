package io.github.koosty.xmpp.error;

import io.github.koosty.xmpp.stanza.IqStanza;
import io.github.koosty.xmpp.stanza.MessageStanza;
import io.github.koosty.xmpp.stanza.PresenceStanza;
import io.github.koosty.xmpp.stanza.XmppStanza;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class XmppErrorHandlerTest {
    
    private XmppErrorHandler errorHandler;
    private DocumentBuilderFactory documentBuilderFactory;
    
    @BeforeEach
    void setUp() {
        errorHandler = new XmppErrorHandler();
        documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
    }
    
    @Test
    void generateStreamError_shouldCreateValidXml() throws Exception {
        String errorXml = errorHandler.generateStreamError(
            StreamErrorCondition.BAD_FORMAT, 
            Optional.of("Test error message")
        );
        
        assertNotNull(errorXml);
        assertTrue(errorXml.contains("stream:error"));
        assertTrue(errorXml.contains("bad-format"));
        assertTrue(errorXml.contains("Test error message"));
        
        // Validate XML structure
        Document doc = parseXml(errorXml);
        Element root = doc.getDocumentElement();
        assertEquals("error", root.getLocalName());
        assertEquals("http://etherx.jabber.org/streams", root.getNamespaceURI());
    }
    
    @Test
    void generateStreamError_withoutDescriptiveText() throws Exception {
        String errorXml = errorHandler.generateStreamError(
            StreamErrorCondition.NOT_WELL_FORMED, 
            Optional.empty()
        );
        
        assertNotNull(errorXml);
        assertTrue(errorXml.contains("not-well-formed"));
        assertFalse(errorXml.contains("<text"));
        
        // Validate XML structure
        Document doc = parseXml(errorXml);
        Element root = doc.getDocumentElement();
        assertEquals("error", root.getLocalName());
    }
    
    @Test
    void generateStanzaError_forMessageStanza() {
        MessageStanza originalMessage = new MessageStanza(
            "msg123",
            "alice@example.com/resource",
            "bob@example.com/resource",
            "chat",
            "Hello world",
            null,
            null,
            null
        );
        
        XmppStanza errorStanza = errorHandler.generateStanzaError(
            originalMessage,
            StanzaErrorCondition.BAD_REQUEST,
            StanzaErrorType.MODIFY,
            Optional.of("Invalid message format")
        );
        
        assertInstanceOf(MessageStanza.class, errorStanza);
        assertEquals("bob@example.com/resource", errorStanza.from());
        assertEquals("alice@example.com/resource", errorStanza.to());
        assertEquals("error", errorStanza.type());
        assertEquals("msg123", errorStanza.id());
        
        MessageStanza errorMessage = (MessageStanza) errorStanza;
        assertNull(errorMessage.body()); // error messages have no body
    }
    
    @Test
    void generateStanzaError_forIqStanza() {
        IqStanza originalIq = new IqStanza(
            "iq123",
            "client@example.com/resource",
            "server.example.com",
            "get",
            "urn:xmpp:ping",
            null,
            null
        );
        
        XmppStanza errorStanza = errorHandler.generateStanzaError(
            originalIq,
            StanzaErrorCondition.SERVICE_UNAVAILABLE,
            StanzaErrorType.CANCEL,
            Optional.of("Service is down")
        );
        
        assertInstanceOf(IqStanza.class, errorStanza);
        assertEquals("server.example.com", errorStanza.from());
        assertEquals("client@example.com/resource", errorStanza.to());
        assertEquals("error", errorStanza.type());
        assertEquals("iq123", errorStanza.id());
        
        IqStanza errorIq = (IqStanza) errorStanza;
        assertNull(errorIq.queryNamespace()); // error IQs have no query namespace
    }
    
    @Test
    void generateStanzaError_forPresenceStanza() {
        PresenceStanza originalPresence = new PresenceStanza(
            "pres123",
            "user@example.com/resource",
            null,
            "available",
            "chat",
            "Available for chat",
            5,
            null
        );
        
        XmppStanza errorStanza = errorHandler.generateStanzaError(
            originalPresence,
            StanzaErrorCondition.FORBIDDEN,
            StanzaErrorType.AUTH,
            Optional.of("Not authorized")
        );
        
        assertInstanceOf(PresenceStanza.class, errorStanza);
        assertEquals(null, errorStanza.from());
        assertEquals("user@example.com/resource", errorStanza.to());
        assertEquals("error", errorStanza.type());
        assertEquals("pres123", errorStanza.id());
        
        PresenceStanza errorPresence = (PresenceStanza) errorStanza;
        assertNull(errorPresence.show()); // error presence has no show
        assertNull(errorPresence.status()); // error presence has no status
        assertEquals(0, errorPresence.priority()); // default priority for errors
    }
    
    @Test
    void generateBadFormatError_shouldContainCorrectCondition() throws Exception {
        String errorXml = errorHandler.generateBadFormatError();
        
        assertTrue(errorXml.contains("bad-format"));
        assertTrue(errorXml.contains("urn:ietf:params:xml:ns:xmpp-streams"));
        
        Document doc = parseXml(errorXml);
        Element root = doc.getDocumentElement();
        assertEquals("error", root.getLocalName());
    }
    
    @Test
    void generateBadRequestError_shouldSwapToAndFrom() {
        MessageStanza originalMessage = new MessageStanza(
            "test123",
            "sender@example.com",
            "recipient@example.com",
            "chat",
            "Test message",
            null,
            null,
            null
        );
        
        XmppStanza errorStanza = errorHandler.generateBadRequestError(originalMessage);
        
        assertEquals("recipient@example.com", errorStanza.from());
        assertEquals("sender@example.com", errorStanza.to());
        assertEquals("error", errorStanza.type());
        assertEquals("test123", errorStanza.id());
    }
    
    @Test
    void allStreamErrorConditions_shouldHaveValidElementNames() {
        for (StreamErrorCondition condition : StreamErrorCondition.values()) {
            assertNotNull(condition.getElementName());
            assertFalse(condition.getElementName().isEmpty());
            
            // Test round-trip conversion
            assertEquals(condition, StreamErrorCondition.fromElementName(condition.getElementName()));
        }
    }
    
    @Test
    void allStanzaErrorConditions_shouldHaveValidElementNames() {
        for (StanzaErrorCondition condition : StanzaErrorCondition.values()) {
            assertNotNull(condition.getElementName());
            assertFalse(condition.getElementName().isEmpty());
            
            // Test round-trip conversion
            assertEquals(condition, StanzaErrorCondition.fromElementName(condition.getElementName()));
        }
    }
    
    @Test
    void allStanzaErrorTypes_shouldHaveValidValues() {
        for (StanzaErrorType type : StanzaErrorType.values()) {
            assertNotNull(type.getValue());
            assertFalse(type.getValue().isEmpty());
            
            // Test round-trip conversion
            assertEquals(type, StanzaErrorType.fromValue(type.getValue()));
        }
    }
    
    @Test
    void unknownStreamErrorCondition_shouldReturnUndefinedCondition() {
        StreamErrorCondition condition = StreamErrorCondition.fromElementName("unknown-condition");
        assertEquals(StreamErrorCondition.UNDEFINED_CONDITION, condition);
    }
    
    @Test
    void unknownStanzaErrorCondition_shouldReturnUndefinedCondition() {
        StanzaErrorCondition condition = StanzaErrorCondition.fromElementName("unknown-condition");
        assertEquals(StanzaErrorCondition.UNDEFINED_CONDITION, condition);
    }
    
    @Test
    void unknownStanzaErrorType_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> 
            StanzaErrorType.fromValue("unknown-type"));
    }
    
    private Document parseXml(String xml) throws Exception {
        DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }
}