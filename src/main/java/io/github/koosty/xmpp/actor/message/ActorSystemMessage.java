package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

public record ActorSystemMessage(
    String actorId,
    String command,
    Instant timestamp
) implements ActorMessage {
    @Override public MessageType getType() { return MessageType.ACTOR_SUPERVISION; }
    @Override public String getSender() { return "system"; }
    @Override public Instant getTimestamp() { return timestamp; }
}