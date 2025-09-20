package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Response from message routing operation
 */
public record RouteMessageResponse(
    boolean success,
    String message,
    String targetJid,
    String sender,
    Instant timestamp
) implements ActorMessage {
    
    /**
     * Create response with automatic timestamp and sender
     */
    public RouteMessageResponse(boolean success, String message, String targetJid) {
        this(success, message, targetJid, "MessageRoutingActor", Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.ROUTE_MESSAGE_RESPONSE;
    }
    
    @Override
    public String getSender() {
        return sender;
    }
    
    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}