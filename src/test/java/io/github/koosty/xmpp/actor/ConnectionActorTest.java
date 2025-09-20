package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.IncomingXmlMessage;
import io.github.koosty.xmpp.config.XmppSecurityProperties;
import io.github.koosty.xmpp.stream.XmlStreamProcessor;
import io.github.koosty.xmpp.features.StreamFeaturesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for ConnectionActor
 */
class ConnectionActorTest {
    
    private ConnectionActor actor;
    private XmlStreamProcessor xmlProcessor;
    private StreamFeaturesManager featuresManager;
    private AtomicReference<String> lastOutboundMessage;
    
    @BeforeEach
    void setUp() {
        xmlProcessor = new XmlStreamProcessor();
        
        // Create mock security properties for testing
        XmppSecurityProperties securityProperties = new XmppSecurityProperties();
        featuresManager = new StreamFeaturesManager(securityProperties);
        lastOutboundMessage = new AtomicReference<>();
        
        // Mock ActorSystem for testing
        ActorSystem mockActorSystem = null; // In tests, we can use null for now
        
        actor = new ConnectionActor("test-conn", xmlProcessor, message -> {
            lastOutboundMessage.set(message.xmlData());
        }, featuresManager, mockActorSystem, securityProperties);
    }
    
    @Test
    void actorStartsAndStops() throws InterruptedException {
        actor.start();
        assertTrue(actor.isHealthy());
        
        Thread.sleep(100); // Give actor time to start
        
        actor.stop();
        Thread.sleep(100); // Give actor time to stop
        
        assertFalse(actor.isHealthy());
    }
    
    @Test
    void actorProcessesIncomingXmlMessage() throws InterruptedException {
        actor.start();
        
        // Send stream header
        String streamHeader = "<?xml version='1.0'?><stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' to='localhost' version='1.0'>";
        IncomingXmlMessage message = IncomingXmlMessage.of("test-conn", streamHeader);
        
        actor.tell(message);
        
        // Wait a bit for processing
        Thread.sleep(200);
        
        // Should have received stream response
        assertNotNull(lastOutboundMessage.get());
        assertTrue(lastOutboundMessage.get().contains("stream:features"));
        
        actor.stop();
    }
    
    @Test
    void actorHandlesStreamClose() throws InterruptedException {
        actor.start();
        
        IncomingXmlMessage closeMessage = IncomingXmlMessage.of("test-conn", "</stream:stream>");
        actor.tell(closeMessage);
        
        // Wait a bit longer for stream close processing
        Thread.sleep(500);
        
        // Should be in CLOSED or CLOSING state (both are valid during shutdown)
        assertTrue(actor.getState() == ConnectionState.CLOSED || actor.getState() == ConnectionState.CLOSING);
        actor.stop();
    }
}