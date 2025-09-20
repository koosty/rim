package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message for stream initiation according to RFC6120 Section 4.
 */
public record StreamInitiationMessage(
    String connectionId,
    String from,
    String to,
    String streamId,
    Instant timestamp
) implements ActorMessage {
    
    public static StreamInitiationMessage of(String connectionId, String from, String to, String streamId) {
        return new StreamInitiationMessage(connectionId, from, to, streamId, Instant.now());
    }
    
    @Override
    public MessageType getType() { 
        return MessageType.STREAM_INITIATION; 
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