package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message for sending XML stanza data to a client connection.
 */
public record OutgoingStanzaMessage(
    String connectionId,
    String xmlData,
    Instant timestamp
) implements ActorMessage {
    
    public static OutgoingStanzaMessage of(String connectionId, String xmlData) {
        return new OutgoingStanzaMessage(connectionId, xmlData, Instant.now());
    }
    
    @Override
    public MessageType getType() { 
        return MessageType.OUTGOING_STANZA; 
    }
    
    @Override
    public String getSender() { 
        return "server"; 
    }
    
    @Override
    public Instant getTimestamp() { 
        return timestamp; 
    }
}