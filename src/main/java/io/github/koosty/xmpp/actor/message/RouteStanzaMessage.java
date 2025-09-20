package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

public record RouteStanzaMessage(
    String fromConnectionId,
    String toJid,
    String stanzaXml,
    Instant timestamp
) implements ActorMessage {
    @Override public MessageType getType() { return MessageType.ROUTE_STANZA; }
    @Override public String getSender() { return fromConnectionId; }
    @Override public Instant getTimestamp() { return timestamp; }
}