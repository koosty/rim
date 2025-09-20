package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

public record ConnectionBoundMessage(
    String connectionId,
    String jid,
    Instant timestamp
) implements ActorMessage {
    @Override public MessageType getType() { return MessageType.CONNECTION_BOUND; }
    @Override public String getSender() { return connectionId; }
    @Override public Instant getTimestamp() { return timestamp; }
}