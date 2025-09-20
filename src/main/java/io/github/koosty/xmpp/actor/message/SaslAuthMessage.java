package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

public record SaslAuthMessage(
    String connectionId,
    String mechanism,
    String data,
    Instant timestamp
) implements ActorMessage {
    @Override public MessageType getType() { return MessageType.SASL_AUTH; }
    @Override public String getSender() { return connectionId; }
    @Override public Instant getTimestamp() { return timestamp; }
}