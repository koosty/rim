package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message indicating successful resource binding.
 */
public record ResourceBindingSuccessMessage(
    String connectionId,
    String boundJid,
    String iqId,
    Instant timestamp
) implements ActorMessage {
    
    public ResourceBindingSuccessMessage(String connectionId, String boundJid, String iqId) {
        this(connectionId, boundJid, iqId, Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.RESOURCE_BINDING_SUCCESS;
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