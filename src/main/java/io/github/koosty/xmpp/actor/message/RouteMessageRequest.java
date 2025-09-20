package io.github.koosty.xmpp.actor.message;

import io.github.koosty.xmpp.stanza.MessageStanza;
import java.time.Instant;

/**
 * Request to route a message stanza to its destination
 */
public record RouteMessageRequest(
    MessageStanza stanza,
    String sourceJid,
    String sender,
    Instant timestamp
) implements ActorMessage {
    
    /**
     * Create request with automatic timestamp and sender
     */
    public RouteMessageRequest(MessageStanza stanza, String sourceJid) {
        this(stanza, sourceJid, "ConnectionActor", Instant.now());
    }
    
    @Override
    public MessageType getType() {
        return MessageType.ROUTE_MESSAGE;
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