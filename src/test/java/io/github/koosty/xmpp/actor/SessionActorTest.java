package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.*;
import io.github.koosty.xmpp.jid.JidValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

class SessionActorTest {
    
    private SessionActor sessionActor;
    private JidValidator jidValidator;
    private String sessionId;
    private String bareJid;
    
    @BeforeEach
    void setUp() {
        jidValidator = new JidValidator();
        sessionId = "test-session-1";
        bareJid = "user@example.com";
        sessionActor = new SessionActor(sessionId, bareJid, jidValidator);
    }
    
    @AfterEach
    void tearDown() {
        if (sessionActor != null) {
            sessionActor.stop();
        }
    }
    
    @Test
    void testSessionCreation() {
        assertEquals(sessionId, sessionActor.getSessionInfo().sessionId());
        assertEquals(bareJid, sessionActor.getSessionInfo().bareJid());
        assertEquals(SessionActor.SessionState.CREATED, sessionActor.getState());
        assertNull(sessionActor.getSessionInfo().fullJid());
        assertNull(sessionActor.getSessionInfo().connectionId());
        assertNotNull(sessionActor.getSessionInfo().createdAt());
    }
    
    @Test
    void testSessionBound() {
        sessionActor.start();
        
        String fullJid = "user@example.com/mobile";
        String connectionId = "conn1";
        
        SessionBoundMessage boundMessage = new SessionBoundMessage(sessionId, fullJid, connectionId);
        sessionActor.tell(boundMessage);
        
        // Allow time for message processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertEquals(SessionActor.SessionState.BOUND, sessionActor.getState());
        assertTrue(sessionActor.isActive());
        
        SessionActor.SessionInfo info = sessionActor.getSessionInfo();
        assertEquals(fullJid, info.fullJid());
        assertEquals(connectionId, info.connectionId());
        assertEquals("mobile", info.resourceId());
        assertNotNull(info.boundAt());
    }
    
    @Test
    void testSessionTerminated() {
        sessionActor.start();
        
        String fullJid = "user@example.com/mobile";
        String reason = "User logout";
        
        SessionTerminatedMessage terminatedMessage = new SessionTerminatedMessage(sessionId, fullJid, reason);
        sessionActor.tell(terminatedMessage);
        
        // Allow time for message processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertEquals(SessionActor.SessionState.TERMINATED, sessionActor.getState());
        assertFalse(sessionActor.isActive());
    }
    
    @Test
    void testInvalidJidInBoundMessage() {
        sessionActor.start();
        
        // Try to bind with invalid JID
        String invalidFullJid = "invalid@@jid/resource";
        SessionBoundMessage boundMessage = new SessionBoundMessage(sessionId, invalidFullJid, "conn1");
        sessionActor.tell(boundMessage);
        
        // Allow time for message processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Session should remain in CREATED state due to invalid JID
        assertEquals(SessionActor.SessionState.CREATED, sessionActor.getState());
        assertNull(sessionActor.getSessionInfo().fullJid());
    }
    
    @Test
    void testJidMismatchInBoundMessage() {
        sessionActor.start();
        
        // Try to bind with JID for different user
        String differentUserJid = "otheruser@example.com/mobile";
        SessionBoundMessage boundMessage = new SessionBoundMessage(sessionId, differentUserJid, "conn1");
        sessionActor.tell(boundMessage);
        
        // Allow time for message processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Session should remain in CREATED state due to JID mismatch
        assertEquals(SessionActor.SessionState.CREATED, sessionActor.getState());
        assertNull(sessionActor.getSessionInfo().fullJid());
    }
    
    @Test
    void testMessageForDifferentSession() {
        sessionActor.start();
        
        // Send bound message for different session ID
        SessionBoundMessage boundMessage = new SessionBoundMessage("different-session", 
                                                                   "user@example.com/mobile", 
                                                                   "conn1");
        sessionActor.tell(boundMessage);
        
        // Allow time for message processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Session should remain unchanged
        assertEquals(SessionActor.SessionState.CREATED, sessionActor.getState());
        assertNull(sessionActor.getSessionInfo().fullJid());
    }
    
    @Test
    void testIncomingXmlHandling() {
        sessionActor.start();
        
        // First bind the session
        String fullJid = "user@example.com/mobile";
        String connectionId = "conn1";
        sessionActor.tell(new SessionBoundMessage(sessionId, fullJid, connectionId));
        
        // Then send incoming XML
        IncomingXmlMessage xmlMessage = IncomingXmlMessage.of(connectionId, "<message>test</message>");
        sessionActor.tell(xmlMessage);
        
        // Allow time for message processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Session should process the message and update last activity
        SessionActor.SessionInfo info = sessionActor.getSessionInfo();
        assertNotNull(info.lastActivity());
    }
    
    @Test
    void testPresenceUpdate() {
        sessionActor.start();
        
        // First bind the session
        String fullJid = "user@example.com/mobile";
        sessionActor.tell(new SessionBoundMessage(sessionId, fullJid, "conn1"));
        
        // Send presence update
        PresenceUpdateMessage presenceMessage = new PresenceUpdateMessage(fullJid, 
                                                                          "contact@example.com", 
                                                                          "away", 
                                                                          "Be right back", 
                                                                          java.time.Instant.now());
        sessionActor.tell(presenceMessage);
        
        // Allow time for message processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Session should process the presence update
        assertNotNull(sessionActor.getSessionInfo().lastActivity());
    }
    
    @Test
    void testActorHealthCheck() {
        assertFalse(sessionActor.isHealthy()); // Not started yet
        
        sessionActor.start();
        
        // Allow time for thread to start
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertTrue(sessionActor.isHealthy());
        
        sessionActor.stop();
        
        // Allow time for thread to stop
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertFalse(sessionActor.isHealthy());
    }
}