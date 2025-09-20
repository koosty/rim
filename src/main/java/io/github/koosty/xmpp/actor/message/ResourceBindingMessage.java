package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

public record ResourceBindingMessage(
    String connectionId,
    String resource,
    Instant timestamp
) implements ActorMessage {
    @Override public MessageType getType() { return MessageType.RESOURCE_BINDING; }
    @Override public String getSender() { return connectionId; }
    @Override public Instant getTimestamp() { return timestamp; }
}