package io.github.koosty.xmpp.stanza;

import org.w3c.dom.Node;

/**
 * Represents an XMPP presence stanza.
 * Handles presence notifications, subscriptions, and availability status.
 */
public record PresenceStanza(
    String id,
    String from,
    String to, 
    String type,
    String show,
    String status,
    int priority,
    Node originalNode
) implements XmppStanza {
    
    /**
     * Create presence stanza with validation
     */
    public PresenceStanza {
        // Default type to 'available' if null
        if (type == null || type.isEmpty()) {
            type = "available";
        }
        
        // Validate priority range (-128 to 127)
        if (priority < -128 || priority > 127) {
            priority = 0;
        }
    }
    
    /**
     * Check if this is an available presence
     */
    public boolean isAvailable() {
        return "available".equals(type);
    }
    
    /**
     * Check if this is an unavailable presence
     */
    public boolean isUnavailable() {
        return "unavailable".equals(type);
    }
    
    /**
     * Check if this is a subscription request
     */
    public boolean isSubscribe() {
        return "subscribe".equals(type);
    }
    
    /**
     * Check if this is a subscription approval
     */
    public boolean isSubscribed() {
        return "subscribed".equals(type);
    }
    
    /**
     * Check if this is an unsubscription request
     */
    public boolean isUnsubscribe() {
        return "unsubscribe".equals(type);
    }
    
    /**
     * Check if this is an unsubscription confirmation
     */
    public boolean isUnsubscribed() {
        return "unsubscribed".equals(type);
    }
    
    /**
     * Check if this is a presence probe
     */
    public boolean isProbe() {
        return "probe".equals(type);
    }
    
    /**
     * Check if this is an error presence
     */
    public boolean isError() {
        return "error".equals(type);
    }
    
    /**
     * Check if presence has status text
     */
    public boolean hasStatus() {
        return status != null && !status.isEmpty();
    }
    
    /**
     * Check if presence has show information
     */
    public boolean hasShow() {
        return show != null && !show.isEmpty();
    }
    
    /**
     * Get availability as enum for easier handling
     */
    public PresenceShow getShowEnum() {
        if (show == null || show.isEmpty()) {
            return isAvailable() ? PresenceShow.ONLINE : PresenceShow.OFFLINE;
        }
        
        return switch (show) {
            case "away" -> PresenceShow.AWAY;
            case "chat" -> PresenceShow.CHAT;
            case "dnd" -> PresenceShow.DO_NOT_DISTURB;
            case "xa" -> PresenceShow.EXTENDED_AWAY;
            default -> PresenceShow.ONLINE;
        };
    }
    
    @Override
    public XmppStanza withAddressing(String newFrom, String newTo) {
        return new PresenceStanza(id, newFrom, newTo, type, show, status, priority, originalNode);
    }
    
    /**
     * Create subscription approval response
     */
    public PresenceStanza createSubscribedResponse() {
        return new PresenceStanza(null, to, from, "subscribed", null, null, 0, null);
    }
    
    /**
     * Create subscription denial response
     */
    public PresenceStanza createUnsubscribedResponse() {
        return new PresenceStanza(null, to, from, "unsubscribed", null, null, 0, null);
    }
    
    /**
     * Presence show enumeration
     */
    public enum PresenceShow {
        ONLINE,
        AWAY,
        CHAT,
        DO_NOT_DISTURB,
        EXTENDED_AWAY,
        OFFLINE
    }
}