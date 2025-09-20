package io.github.koosty.xmpp.stanza;

import io.github.koosty.xmpp.jid.JidValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StanzaProcessorTest {
    
    @Mock
    private JidValidator jidValidator;
    
    private StanzaProcessor stanzaProcessor;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock JID validator to return valid results
        when(jidValidator.parseJid(anyString())).thenReturn(java.util.Optional.of(
            new io.github.koosty.xmpp.jid.Jid("test", "example.com", "resource")
        ));
        
        stanzaProcessor = new StanzaProcessor(jidValidator);
    }
    
    @Test
    @DisplayName("Should parse simple message stanza")
    void shouldParseSimpleMessageStanza() throws Exception {
        String messageXml = "<message xmlns='jabber:client' from='alice@example.com' to='bob@example.com' type='chat' id='msg1'><body>Hello</body></message>";
        
        var result = stanzaProcessor.parseStanza(messageXml);
        
        System.out.println("Parse result: " + result.isPresent());
        if (result.isPresent()) {
            System.out.println("Stanza type: " + result.get().getClass().getSimpleName());
            if (result.get() instanceof MessageStanza msg) {
                System.out.println("Message details: " + msg);
            }
        } else {
            System.out.println("Parse failed - stanza not recognized");
        }
        
        assertTrue(result.isPresent());
        assertInstanceOf(MessageStanza.class, result.get());
    }
    
    @Test
    @DisplayName("Should parse simple presence stanza")
    void shouldParseSimplePresenceStanza() throws Exception {
        String presenceXml = "<presence xmlns='jabber:client' from='alice@example.com' type='available'/>";
        
        var result = stanzaProcessor.parseStanza(presenceXml);
        
        System.out.println("Presence parse result: " + result.isPresent());
        if (result.isPresent()) {
            System.out.println("Stanza type: " + result.get().getClass().getSimpleName());
        }
        
        assertTrue(result.isPresent());
        assertInstanceOf(PresenceStanza.class, result.get());
    }
    
    @Test
    @DisplayName("Should parse simple IQ stanza")
    void shouldParseSimpleIqStanza() throws Exception {
        String iqXml = "<iq xmlns='jabber:client' from='alice@example.com' to='example.com' type='get' id='ping1'><ping xmlns='urn:xmpp:ping'/></iq>";
        
        var result = stanzaProcessor.parseStanza(iqXml);
        
        System.out.println("IQ parse result: " + result.isPresent());
        if (result.isPresent()) {
            System.out.println("Stanza type: " + result.get().getClass().getSimpleName());
        }
        
        assertTrue(result.isPresent());
        assertInstanceOf(IqStanza.class, result.get());
    }
}