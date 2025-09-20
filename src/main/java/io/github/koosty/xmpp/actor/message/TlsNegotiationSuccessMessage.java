package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message indicating successful TLS negotiation.
 */
public record TlsNegotiationSuccessMessage(
    String connectionId,
    Instant timestamp
) implements ActorMessage {
    
    public TlsNegotiationSuccessMessage(String connectionId) {
        this(connectionId, Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.TLS_NEGOTIATION_SUCCESS;
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