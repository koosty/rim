package io.github.koosty.xmpp.jid;

/**
 * Immutable JID (Jabber ID) record representing localpart@domainpart/resourcepart.
 * Implements RFC6120 Section 3 JID format and normalization.
 */
public record Jid(
    String localpart,
    String domainpart, 
    String resourcepart
) {
    
    /**
     * Create JID with validation
     */
    public Jid {
        if (domainpart == null || domainpart.isEmpty()) {
            throw new IllegalArgumentException("Domainpart cannot be null or empty");
        }
    }
    
    /**
     * Get the bare JID (without resourcepart)
     */
    public Jid toBareJid() {
        return new Jid(localpart, domainpart, null);
    }
    
    /**
     * Check if this is a bare JID (no resourcepart)
     */
    public boolean isBareJid() {
        return resourcepart == null;
    }
    
    /**
     * Check if this is a full JID (has resourcepart)
     */
    public boolean isFullJid() {
        return resourcepart != null;
    }
    
    /**
     * Check if this is a server JID (no localpart)
     */
    public boolean isServerJid() {
        return localpart == null;
    }
    
    /**
     * Get string representation of JID
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        if (localpart != null) {
            sb.append(localpart).append('@');
        }
        
        sb.append(domainpart);
        
        if (resourcepart != null) {
            sb.append('/').append(resourcepart);
        }
        
        return sb.toString();
    }
    
    /**
     * Create a new JID with different resource
     */
    public Jid withResource(String newResourcepart) {
        return new Jid(localpart, domainpart, newResourcepart);
    }
    
    /**
     * Get bare JID as string
     */
    public String toBareJidString() {
        StringBuilder sb = new StringBuilder();
        
        if (localpart != null) {
            sb.append(localpart).append('@');
        }
        
        sb.append(domainpart);
        
        return sb.toString();
    }
}