package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message indicating successful SASL authentication.
 */
public record SaslAuthSuccessMessage(
    String connectionId,
    String authenticatedJid,
    Instant timestamp
) implements ActorMessage {
    
    public SaslAuthSuccessMessage(String connectionId, String authenticatedJid) {
        this(connectionId, authenticatedJid, Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.SASL_AUTH_SUCCESS;
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