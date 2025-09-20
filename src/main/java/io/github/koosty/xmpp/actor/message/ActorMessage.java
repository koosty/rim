package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Base interface for all actor messages in the XMPP server.
 * All messages are immutable and carry metadata for tracing and debugging.
 */
public sealed interface ActorMessage permits 
    IncomingXmlMessage, 
    OutgoingStanzaMessage, 
    TlsNegotiationMessage,
    TlsNegotiationRequestMessage,
    TlsNegotiationSuccessMessage,
    TlsNegotiationFailureMessage,
    SaslAuthMessage,
    SaslAuthRequestMessage,
    SaslAuthResponseMessage,
    SaslAuthSuccessMessage,
    SaslAuthFailureMessage,
    ResourceBindingMessage,
    ResourceBindingRequestMessage,
    ResourceBindingSuccessMessage,
    ResourceBindingFailureMessage,
    SessionCreatedMessage,
    SessionBoundMessage,
    SessionTerminatedMessage,
    PresenceUpdateMessage,
    ConnectionBoundMessage,
    ConnectionClosedMessage,
    RouteStanzaMessage,
    RouteMessageRequest,
    RouteMessageResponse,
    StreamInitiationMessage,
    ActorSystemMessage,
    GenericActorMessage {
    
    MessageType getType();
    String getSender();
    Instant getTimestamp();
}