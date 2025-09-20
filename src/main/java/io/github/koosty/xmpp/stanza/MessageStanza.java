package io.github.koosty.xmpp.stanza;

import org.w3c.dom.Node;

/**
 * Represents an XMPP message stanza.
 * Handles chat messages, headlines, and other message types.
 */
public record MessageStanza(
    String id,
    String from, 
    String to,
    String type,
    String body,
    String subject,
    String thread,
    Node originalNode
) implements XmppStanza {
    
    /**
     * Create message stanza with validation
     */
    public MessageStanza {
        // Default type to 'normal' if null
        if (type == null || type.isEmpty()) {
            type = "normal";
        }
    }
    
    /**
     * Check if message has a body
     */
    public boolean hasBody() {
        return body != null && !body.isEmpty();
    }
    
    /**
     * Check if message has a subject
     */
    public boolean hasSubject() {
        return subject != null && !subject.isEmpty();
    }
    
    /**
     * Check if message is part of a thread
     */
    public boolean hasThread() {
        return thread != null && !thread.isEmpty();
    }
    
    /**
     * Check if this is a chat message
     */
    public boolean isChatMessage() {
        return "chat".equals(type);
    }
    
    /**
     * Check if this is a groupchat message
     */
    public boolean isGroupchatMessage() {
        return "groupchat".equals(type);
    }
    
    /**
     * Check if this is a headline message
     */
    public boolean isHeadlineMessage() {
        return "headline".equals(type);
    }
    
    /**
     * Check if this is an error message
     */
    public boolean isErrorMessage() {
        return "error".equals(type);
    }
    
    @Override
    public XmppStanza withAddressing(String newFrom, String newTo) {
        return new MessageStanza(id, newFrom, newTo, type, body, subject, thread, originalNode);
    }
    
    /**
     * Create a reply message with swapped addressing
     */
    public MessageStanza createReply(String replyBody) {
        return new MessageStanza(
            null, // New ID will be generated
            to,   // Reply goes back to original sender
            from, // Reply comes from original recipient
            "chat".equals(type) ? "chat" : "normal",
            replyBody,
            null, // No subject for replies typically
            thread, // Preserve thread if present
            null  // No original node for new message
        );
    }
}