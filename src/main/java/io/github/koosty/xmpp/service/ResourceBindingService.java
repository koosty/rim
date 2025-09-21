package io.github.koosty.xmpp.service;

import reactor.core.publisher.Mono;

/**
 * Service for handling resource binding and session establishment in XMPP connections.
 * Implements resource binding according to RFC6120 Section 7.
 */
public interface ResourceBindingService {
    
    /**
     * Process resource binding IQ request from client.
     * 
     * @param connectionId unique connection identifier
     * @param authenticatedJid the authenticated bare JID
     * @param xmlData the incoming resource binding IQ XML
     * @return resource binding result with response XML
     */
    Mono<ResourceBindingResult> processResourceBinding(String connectionId, String authenticatedJid, String xmlData);
    
    /**
     * Process session establishment IQ request from client.
     * 
     * @param connectionId unique connection identifier
     * @param fullJid the full JID after resource binding
     * @param xmlData the incoming session IQ XML
     * @return session establishment result with response XML
     */
    Mono<SessionResult> processSessionEstablishment(String connectionId, String fullJid, String xmlData);
    
    /**
     * Generate a unique resource identifier for the connection.
     * 
     * @param connectionId unique connection identifier
     * @param requestedResource client-requested resource (may be null)
     * @return generated unique resource identifier
     */
    String generateResource(String connectionId, String requestedResource);
    
    /**
     * Get the full JID for a connection after resource binding.
     * 
     * @param connectionId unique connection identifier
     * @return full JID or null if not bound
     */
    String getBoundJid(String connectionId);
    
    /**
     * Check if resource binding has been completed for the connection.
     * 
     * @param connectionId unique connection identifier
     * @return true if resource is bound, false otherwise
     */
    boolean isResourceBound(String connectionId);
    
    /**
     * Clean up resource binding state when connection closes.
     * 
     * @param connectionId unique connection identifier
     */
    void cleanupConnection(String connectionId);
    
    /**
     * Result of resource binding operation.
     */
    record ResourceBindingResult(
        boolean success,
        String responseXml,
        String fullJid,
        String errorType,
        String errorCondition,
        String errorText
    ) {
        
        public static ResourceBindingResult success(String fullJid, String responseXml) {
            return new ResourceBindingResult(true, responseXml, fullJid, null, null, null);
        }
        
        public static ResourceBindingResult error(String errorType, String errorCondition, String errorText, String responseXml) {
            return new ResourceBindingResult(false, responseXml, null, errorType, errorCondition, errorText);
        }
    }
    
    /**
     * Result of session establishment operation.
     */
    record SessionResult(
        boolean success,
        String responseXml,
        String errorType,
        String errorCondition,
        String errorText
    ) {
        
        public static SessionResult success(String responseXml) {
            return new SessionResult(true, responseXml, null, null, null);
        }
        
        public static SessionResult error(String errorType, String errorCondition, String errorText, String responseXml) {
            return new SessionResult(false, responseXml, errorType, errorCondition, errorText);
        }
    }
}