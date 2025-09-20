package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message with SASL authentication response.
 */
public record SaslAuthResponseMessage(
    String connectionId,
    String responseData,
    Instant timestamp
) implements ActorMessage {
    
    public SaslAuthResponseMessage(String connectionId, String responseData) {
        this(connectionId, responseData, Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.SASL_AUTH_RESPONSE;
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