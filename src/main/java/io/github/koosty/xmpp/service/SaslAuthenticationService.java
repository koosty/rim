package io.github.koosty.xmpp.service;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service for handling SASL authentication in XMPP connections.
 * Supports PLAIN, SCRAM-SHA-1, and SCRAM-SHA-256 mechanisms according to RFC6120.
 */
public interface SaslAuthenticationService {
    
    /**
     * Process SASL authentication request from client.
     * 
     * @param connectionId unique connection identifier
     * @param xmlData the incoming SASL XML stanza
     * @return SASL authentication result with response XML
     */
    Mono<SaslAuthenticationResult> processAuth(String connectionId, String xmlData);
    
    /**
     * Get list of supported SASL mechanisms for the connection.
     * 
     * @param connectionId unique connection identifier
     * @param tlsEstablished whether TLS has been established
     * @return list of supported mechanism names
     */
    List<String> getSupportedMechanisms(String connectionId, boolean tlsEstablished);
    
    /**
     * Check if the connection is authenticated.
     * 
     * @param connectionId unique connection identifier
     * @return true if authenticated, false otherwise
     */
    boolean isAuthenticated(String connectionId);
    
    /**
     * Get the authenticated JID for the connection.
     * 
     * @param connectionId unique connection identifier
     * @return authenticated JID or null if not authenticated
     */
    String getAuthenticatedJid(String connectionId);
    
    /**
     * Clean up authentication state when connection closes.
     * 
     * @param connectionId unique connection identifier
     */
    void cleanupConnection(String connectionId);
    
    /**
     * Result of SASL authentication operation.
     */
    record SaslAuthenticationResult(
        boolean success,
        String responseXml,
        String authenticatedJid,
        String errorCondition,
        String errorText,
        boolean requiresStreamRestart
    ) {
        
        public static SaslAuthenticationResult success(String authenticatedJid, String responseXml) {
            return new SaslAuthenticationResult(true, responseXml, authenticatedJid, null, null, true);
        }
        
        public static SaslAuthenticationResult failure(String errorCondition, String errorText, String responseXml) {
            return new SaslAuthenticationResult(false, responseXml, null, errorCondition, errorText, false);
        }
        
        public static SaslAuthenticationResult challenge(String responseXml) {
            return new SaslAuthenticationResult(false, responseXml, null, null, null, false);
        }
    }
}