package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message requesting TLS negotiation initiation.
 */
public record TlsNegotiationRequestMessage(
    String connectionId,
    Instant timestamp
) implements ActorMessage {
    
    public TlsNegotiationRequestMessage(String connectionId) {
        this(connectionId, Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.TLS_NEGOTIATION_REQUEST;
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