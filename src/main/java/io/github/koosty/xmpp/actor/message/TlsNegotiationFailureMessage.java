package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message indicating failed TLS negotiation.
 */
public record TlsNegotiationFailureMessage(
    String connectionId,
    String errorReason,
    Instant timestamp
) implements ActorMessage {
    
    public TlsNegotiationFailureMessage(String connectionId, String errorReason) {
        this(connectionId, errorReason, Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.TLS_NEGOTIATION_FAILURE;
    }
    
    @Override
    public String getSender() {
        return connectionId;
    }
    
    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}