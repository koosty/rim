package io.github.koosty.xmpp.service;

import reactor.core.publisher.Mono;

/**
 * Service for handling TLS negotiation in XMPP connections.
 * Implements STARTTLS according to RFC6120 Section 5.
 */
public interface TlsNegotiationService {
    
    /**
     * Process STARTTLS request from client.
     * 
     * @param connectionId unique connection identifier
     * @param xmlData the incoming STARTTLS XML stanza
     * @return TLS negotiation result with response XML
     */
    Mono<TlsNegotiationResult> processStartTls(String connectionId, String xmlData);
    
    /**
     * Check if TLS is required for the connection.
     * 
     * @param connectionId unique connection identifier
     * @return true if TLS is mandatory, false otherwise
     */
    boolean isTlsRequired(String connectionId);
    
    /**
     * Result of TLS negotiation operation.
     */
    record TlsNegotiationResult(
        boolean success,
        String responseXml,
        String errorReason,
        boolean requiresStreamRestart
    ) {
        
        public static TlsNegotiationResult success(String responseXml) {
            return new TlsNegotiationResult(true, responseXml, null, true);
        }
        
        public static TlsNegotiationResult failure(String errorReason, String responseXml) {
            return new TlsNegotiationResult(false, responseXml, errorReason, false);
        }
    }
}