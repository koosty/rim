package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

public record TlsNegotiationMessage(
    String connectionId,
    String command,
    Instant timestamp
) implements ActorMessage {
    @Override public MessageType getType() { return MessageType.TLS_NEGOTIATION; }
    @Override public String getSender() { return connectionId; }
    @Override public Instant getTimestamp() { return timestamp; }
}