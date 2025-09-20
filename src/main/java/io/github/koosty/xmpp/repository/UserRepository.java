package io.github.koosty.xmpp.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repository for managing XMPP users in PostgreSQL database.
 */
@Repository
public interface UserRepository extends R2dbcRepository<XmppUser, String> {
    
    /**
     * Find user by username.
     * @param username The username to search for
     * @return Mono containing the user if found
     */
    Mono<XmppUser> findByUsername(String username);
    
    /**
     * Find user by JID.
     * @param jid The JID to search for
     * @return Mono containing the user if found
     */
    Mono<XmppUser> findByJid(String jid);
    
    /**
     * Check if user exists by username.
     * @param username The username to check
     * @return Mono containing true if user exists
     */
    Mono<Boolean> existsByUsername(String username);
    
    /**
     * Find active user by username.
     * @param username The username to search for
     * @return Mono containing the user if found and active
     */
    @Query("SELECT * FROM xmpp_users WHERE username = :username AND active = true")
    Mono<XmppUser> findActiveUserByUsername(String username);
    
    /**
     * Update last login timestamp.
     * @param jid The user's JID
     * @param lastLogin The last login timestamp
     * @return Mono containing the number of updated rows
     */
    @Query("UPDATE xmpp_users SET last_login = :lastLogin WHERE jid = :jid")
    Mono<Integer> updateLastLogin(String jid, java.time.Instant lastLogin);
}