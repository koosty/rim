package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

public record PresenceUpdateMessage(
    String fromJid,
    String toJid,
    String show,
    String status,
    Instant timestamp
) implements ActorMessage {
    @Override public MessageType getType() { return MessageType.PRESENCE_UPDATE; }
    @Override public String getSender() { return fromJid; }
    @Override public Instant getTimestamp() { return timestamp; }
}