package io.github.koosty.xmpp.actor.message;

import java.time.Instant;

/**
 * Message representing incoming XML data from a client connection.
 */
public record IncomingXmlMessage(
    String connectionId, 
    String xmlData, 
    Instant timestamp
) implements ActorMessage {
    
    public static IncomingXmlMessage of(String connectionId, String xmlData) {
        return new IncomingXmlMessage(connectionId, xmlData, Instant.now());
    }
    
    @Override
    public MessageType getType() { 
        return MessageType.INCOMING_XML; 
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