package io.github.koosty.xmpp.actor;

/**
 * Connection states following RFC6120 stream negotiation process.
 */
public enum ConnectionState {
    /**
     * Initial state - connection established but no XML stream opened
     */
    CONNECTED,
    
    /**
     * XML stream opened, waiting for or processing stream features
     */
    STREAM_INITIATED,
    
    /**
     * TLS negotiation in progress
     */
    TLS_NEGOTIATING,
    
    /**
     * TLS established, stream restarted
     */
    TLS_ESTABLISHED,
    
    /**
     * SASL authentication in progress
     */
    AUTHENTICATING,
    
    /**
     * Successfully authenticated, stream restarted
     */
    AUTHENTICATED,
    
    /**
     * Resource binding in progress
     */
    BINDING_RESOURCE,
    
    /**
     * Fully authenticated and bound - ready for stanza exchange
     */
    BOUND,
    
    /**
     * Resource has been bound to the session
     */
    RESOURCE_BOUND,
    
    /**
     * Session has been established - fully ready for communication
     */
    SESSION_ESTABLISHED,
    
    /**
     * Connection is being closed
     */
    CLOSING,
    
    /**
     * Connection closed
     */
    CLOSED,
    
    /**
     * Error state - connection should be terminated
     */
    ERROR
}