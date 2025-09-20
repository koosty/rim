package io.github.koosty.xmpp.connection;

import io.github.koosty.xmpp.stanza.XmppStanza;
import reactor.core.publisher.Mono;

/**
 * Represents an XMPP connection for sending stanzas
 */
public interface XmppConnection {
    
    /**
     * Send a stanza over this connection
     * @param stanza the stanza to send
     * @return Mono that completes with true if sent successfully, false otherwise
     */
    Mono<Boolean> sendStanza(XmppStanza stanza);
    
    /**
     * Get the JID associated with this connection
     * @return the full JID or null if not bound
     */
    String getBoundJid();
    
    /**
     * Check if connection is active
     * @return true if connection is active
     */
    boolean isActive();
    
    /**
     * Close the connection
     * @return Mono that completes when connection is closed
     */
    Mono<Void> close();
}