package io.github.koosty.xmpp.repository;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entity representing a user in the XMPP system.
 */
@Table("xmpp_users")
public record XmppUser(
    @Id
    String jid,
    String username,
    String passwordHash,
    String passwordSalt,
    String hashAlgorithm,
    java.time.Instant createdAt,
    java.time.Instant lastLogin,
    boolean active
) {
    
    /**
     * Create a new user with default values.
     */
    public static XmppUser create(String username, String passwordHash, String passwordSalt) {
        String jid = username + "@localhost";
        return new XmppUser(
            jid,
            username,
            passwordHash,
            passwordSalt,
            "SCRAM-SHA-256",
            java.time.Instant.now(),
            null,
            true
        );
    }
    
    /**
     * Update last login timestamp.
     */
    public XmppUser withLastLogin(java.time.Instant lastLogin) {
        return new XmppUser(
            this.jid,
            this.username,
            this.passwordHash,
            this.passwordSalt,
            this.hashAlgorithm,
            this.createdAt,
            lastLogin,
            this.active
        );
    }
}