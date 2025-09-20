package io.github.koosty.xmpp.stanza;

import org.w3c.dom.Node;

/**
 * Base interface for all XMPP stanzas.
 * Represents common attributes shared by message, presence, and IQ stanzas.
 */
public sealed interface XmppStanza permits MessageStanza, PresenceStanza, IqStanza {
    
    /**
     * Get stanza ID
     */
    String id();
    
    /**
     * Get sender JID
     */
    String from();
    
    /**
     * Get recipient JID  
     */
    String to();
    
    /**
     * Get stanza type
     */
    String type();
    
    /**
     * Get original DOM node
     */
    Node originalNode();
    
    /**
     * Get stanza name (message, presence, or iq)
     */
    default String getStanzaName() {
        if (this instanceof MessageStanza) return "message";
        if (this instanceof PresenceStanza) return "presence";
        if (this instanceof IqStanza) return "iq";
        throw new IllegalStateException("Unknown stanza type");
    }
    
    /**
     * Check if stanza has addressing (from/to attributes)
     */
    default boolean hasAddressing() {
        return from() != null || to() != null;
    }
    
    /**
     * Create a copy of this stanza with different addressing
     */
    XmppStanza withAddressing(String newFrom, String newTo);
}