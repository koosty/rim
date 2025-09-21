package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

public record ResourceBindingMessage(
    String connectionId,
    String xmlData,
    Instant timestamp
) implements ActorMessage {
    @Override public MessageType getType() { return MessageType.RESOURCE_BINDING; }
    @Override public String getSender() { return connectionId; }
    @Override public Instant getTimestamp() { return timestamp; }
    
    // Convenience constructor
    public static ResourceBindingMessage of(String connectionId, String xmlData) {
        return new ResourceBindingMessage(connectionId, xmlData, Instant.now());
    }
}