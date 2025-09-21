package io.github.koosty.xmpp.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import java.util.Base64;

/**
 * Test for SASL PLAIN mechanism handler.
 */
class PlainMechanismHandlerTest {
    
    private PlainMechanismHandler handler;
    
    @Mock
    private UserAuthenticationService userAuthenticationService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Mock successful authentication for valid credentials
        when(userAuthenticationService.authenticatePlain("testuser", "password123"))
            .thenReturn(Mono.just("testuser@localhost"));
        // Mock for empty credentials
        when(userAuthenticationService.authenticatePlain("", ""))
            .thenReturn(Mono.empty());
        
        handler = new PlainMechanismHandler(userAuthenticationService);
    }
    
    @Test
    void shouldHandleValidPlainAuth() throws Exception {
        // Create PLAIN auth data: \0username\0password
        String authString = "\0testuser\0password123";
        String authData = Base64.getEncoder().encodeToString(authString.getBytes());
        
        String result = handler.processInitialAuth(authData);
        
        assertNull(result); // No challenge needed for PLAIN
        assertTrue(handler.isComplete());
        assertEquals("testuser@localhost", handler.getAuthenticatedJid());
    }
    
    @Test
    void shouldHandleValidPlainAuthWithAuthzid() throws Exception {
        // Create PLAIN auth data: authzid\0authcid\0password
        String authString = "authzid\0testuser\0password123";
        String authData = Base64.getEncoder().encodeToString(authString.getBytes());
        
        String result = handler.processInitialAuth(authData);
        
        assertNull(result); // No challenge needed for PLAIN
        assertTrue(handler.isComplete());
        assertEquals("testuser@localhost", handler.getAuthenticatedJid());
    }
    
    @Test
    void shouldRejectEmptyCredentials() throws Exception {
        // Create PLAIN auth data with empty username
        String authString = "\0\0password123";
        String authData = Base64.getEncoder().encodeToString(authString.getBytes());
        
        String result = handler.processInitialAuth(authData);
        
        assertNull(result);
        assertTrue(handler.isComplete());
        assertNull(handler.getAuthenticatedJid()); // Failed auth
    }
    
    @Test
    void shouldRejectMalformedData() throws Exception {
        // Create malformed PLAIN auth data (missing separators)
        String authString = "testuser_password123";
        String authData = Base64.getEncoder().encodeToString(authString.getBytes());
        
        String result = handler.processInitialAuth(authData);
        
        assertNull(result);
        assertTrue(handler.isComplete());
        assertNull(handler.getAuthenticatedJid()); // Failed auth
    }
    
    @Test
    void shouldResetCorrectly() throws Exception {
        // First auth
        String authString = "\0testuser\0password123";
        String authData = Base64.getEncoder().encodeToString(authString.getBytes());
        handler.processInitialAuth(authData);
        
        // Verify state
        assertTrue(handler.isComplete());
        assertEquals("testuser@localhost", handler.getAuthenticatedJid());
        
        // Reset
        handler.reset();
        
        // Verify reset state
        assertFalse(handler.isComplete());
        assertNull(handler.getAuthenticatedJid());
    }
    
    @Test
    void shouldHandleEmptyInitialAuth() throws Exception {
        String result = handler.processInitialAuth("");
        
        assertNull(result);
        assertFalse(handler.isComplete()); // Should wait for response
    }
    
    @Test
    void shouldHandleNullInitialAuth() throws Exception {
        String result = handler.processInitialAuth(null);
        
        assertNull(result);
        assertFalse(handler.isComplete()); // Should wait for response
    }
}