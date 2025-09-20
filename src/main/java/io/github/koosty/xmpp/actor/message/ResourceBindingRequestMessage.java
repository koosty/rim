package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message for requesting resource binding for a user session.
 */
public record ResourceBindingRequestMessage(
    String connectionId,
    String userJid,
    String requestedResource,
    String iqId,
    Instant timestamp
) implements ActorMessage {
    
    public ResourceBindingRequestMessage(String connectionId, String userJid, String requestedResource, String iqId) {
        this(connectionId, userJid, requestedResource, iqId, Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.RESOURCE_BINDING_REQUEST;
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