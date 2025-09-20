package io.github.koosty.xmpp.actor.message;

import java.time.Instant;
import java.util.Map;

/**
 * Generic actor message for responses and system messages
 */
public record GenericActorMessage(
    String messageType,
    String sender,
    Map<String, Object> payload,
    Instant timestamp
) implements ActorMessage {
    
    /**
     * Create with automatic timestamp
     */
    public GenericActorMessage(String messageType, String sender, Map<String, Object> payload) {
        this(messageType, sender, payload, Instant.now());
    }
    
    @Override
    public MessageType getType() {
        // Convert string to enum
        try {
            return MessageType.valueOf(messageType.replace("-", "_").toUpperCase());
        } catch (IllegalArgumentException e) {
            return MessageType.ACTOR_SYSTEM_MESSAGE;
        }
    }
    
    @Override
    public String getSender() {
        return sender;
    }
    
    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * Get typed payload value
     */
    public <T> T getPayload(String key, Class<T> type) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }
}