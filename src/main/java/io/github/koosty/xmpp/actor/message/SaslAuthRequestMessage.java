package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message requesting SASL authentication.
 */
public record SaslAuthRequestMessage(
    String connectionId,
    String mechanism,
    String authData,
    Instant timestamp
) implements ActorMessage {
    
    public SaslAuthRequestMessage(String connectionId, String mechanism, String authData) {
        this(connectionId, mechanism, authData, Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.SASL_AUTH_REQUEST;
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