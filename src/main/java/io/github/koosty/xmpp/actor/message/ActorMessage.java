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
    SaslAuthMessage,
    ResourceBindingMessage,
    PresenceUpdateMessage,
    ConnectionBoundMessage,
    ConnectionClosedMessage,
    RouteStanzaMessage,
    StreamInitiationMessage,
    ActorSystemMessage {
    
    MessageType getType();
    String getSender();
    Instant getTimestamp();
}