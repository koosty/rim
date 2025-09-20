package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message indicating failed resource binding.
 */
public record ResourceBindingFailureMessage(
    String connectionId,
    String errorType,
    String errorMessage,
    String iqId,
    Instant timestamp
) implements ActorMessage {
    
    public ResourceBindingFailureMessage(String connectionId, String errorType, String errorMessage, String iqId) {
        this(connectionId, errorType, errorMessage, iqId, Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.RESOURCE_BINDING_FAILURE;
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