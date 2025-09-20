package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

public record ConnectionClosedMessage(
    String connectionId,
    String reason,
    Instant timestamp
) implements ActorMessage {
    @Override public MessageType getType() { return MessageType.CONNECTION_CLOSED; }
    @Override public String getSender() { return connectionId; }
    @Override public Instant getTimestamp() { return timestamp; }
}