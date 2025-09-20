package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message indicating a session has been terminated.
 */
public record SessionTerminatedMessage(
    String sessionId,
    String fullJid,
    String reason,
    Instant timestamp
) implements ActorMessage {
    
    public SessionTerminatedMessage(String sessionId, String fullJid, String reason) {
        this(sessionId, fullJid, reason, Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.SESSION_TERMINATED;
    }
    
    @Override
    public String getSender() {
        return sessionId;
    }
    
    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}