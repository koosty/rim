package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message indicating failed SASL authentication.
 */
public record SaslAuthFailureMessage(
    String connectionId,
    String errorCondition,
    String errorText,
    Instant timestamp
) implements ActorMessage {
    
    public SaslAuthFailureMessage(String connectionId, String errorCondition, String errorText) {
        this(connectionId, errorCondition, errorText, Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.SASL_AUTH_FAILURE;
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