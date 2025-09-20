package io.github.koosty.xmpp.actor.message;

/**
 * Enumeration of all message types in the actor system.
 */
public enum MessageType {
    // Connection lifecycle
    INCOMING_XML,
    OUTGOING_STANZA,
    CONNECTION_OPENED,
    CONNECTION_CLOSED,
    
    // Stream management
    STREAM_INITIATION,
    STREAM_FEATURES,
    STREAM_RESTART,
    STREAM_CLOSE,
    
    // Security negotiation
    TLS_NEGOTIATION,
    TLS_NEGOTIATION_REQUEST,
    TLS_NEGOTIATION_SUCCESS,
    TLS_NEGOTIATION_FAILURE,
    SASL_AUTH,
    SASL_AUTH_REQUEST,
    SASL_AUTH_RESPONSE,
    SASL_AUTH_SUCCESS,
    SASL_AUTH_FAILURE,
    
    // Resource management
    RESOURCE_BINDING,
    SESSION_CREATED,
    
    // Message routing
    ROUTE_STANZA,
    PRESENCE_UPDATE,
    
    // Actor system
    ACTOR_START,
    ACTOR_STOP,
    ACTOR_SUPERVISION,
    
    // Connection management
    CONNECTION_BOUND
}