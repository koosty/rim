package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message indicating a session has been bound to a resource.
 */
public record SessionBoundMessage(
    String sessionId,
    String fullJid,
    String connectionId,
    Instant timestamp
) implements ActorMessage {
    
    public SessionBoundMessage(String sessionId, String fullJid, String connectionId) {
        this(sessionId, fullJid, connectionId, Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.SESSION_BOUND;
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