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
    SASL_AUTH,
    
    // Resource management
    RESOURCE_BINDING,
    RESOURCE_BINDING_REQUEST,
    RESOURCE_BINDING_SUCCESS,
    RESOURCE_BINDING_FAILURE,
    
    // Session management
    SESSION_CREATED,
    SESSION_BOUND,
    SESSION_TERMINATED,
    
    // Message routing
    ROUTE_STANZA,
    ROUTE_MESSAGE,
    ROUTE_MESSAGE_RESPONSE,
    REGISTER_CONNECTION,
    UNREGISTER_CONNECTION,
    GET_CONNECTION_COUNT,
    PRESENCE_UPDATE,
    
    // Actor system
    ACTOR_START,
    ACTOR_STOP,
    ACTOR_SUPERVISION,
    ACTOR_SYSTEM_MESSAGE,
    
    // Connection management
    CONNECTION_BOUND,
    
    // Server information and discovery
    DISCO_INFO_REQUEST,
    DISCO_INFO_RESPONSE,
    DISCO_ITEMS_REQUEST,
    DISCO_ITEMS_RESPONSE,
    SERVER_INFO_REQUEST,
    SERVER_INFO_RESPONSE,
    FEATURE_QUERY,
    FEATURE_QUERY_RESPONSE,
    
    // Connection monitoring
    CONNECTION_HEALTH_CHECK,
    CONNECTION_TIMEOUT,
    CONNECTION_METRICS_REQUEST,
    CONNECTION_METRICS_RESPONSE,
    CONNECTION_CLEANUP,
    
    // Configuration
    CONFIG_UPDATE,
    CONFIG_RELOAD,
    CONFIG_VALIDATION,
    
    // Metrics and monitoring
    METRICS_COLLECTION,
    HEALTH_STATUS_REQUEST,
    HEALTH_STATUS_RESPONSE,
    PERFORMANCE_METRICS,
    
    // Error responses
    ERROR_RESPONSE
}