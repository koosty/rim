package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message indicating a new session has been created.
 */
public record SessionCreatedMessage(
    String sessionId,
    String bareJid,
    String connectionId,
    Instant timestamp
) implements ActorMessage {
    
    public SessionCreatedMessage(String sessionId, String bareJid, String connectionId) {
        this(sessionId, bareJid, connectionId, Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.SESSION_CREATED;
    }
    
    @Override
    public String getSender() {
        return connectionId;
    }
    
    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}