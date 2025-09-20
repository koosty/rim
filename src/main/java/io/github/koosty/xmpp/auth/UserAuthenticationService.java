package io.github.koosty.xmpp.auth;

import io.github.koosty.xmpp.repository.UserRepository;
import io.github.koosty.xmpp.repository.XmppUser;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for user authentication and credential management.
 * Supports password verification for various SASL mechanisms.
 */
@Service
public class UserAuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(UserAuthenticationService.class);
    
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    
    public UserAuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    /**
     * Authenticate user with plain text password (for PLAIN mechanism).
     * @param username The username
     * @param password The plain text password
     * @return Mono containing the authenticated JID if successful
     */
    public Mono<String> authenticatePlain(String username, String password) {
        return userRepository.findActiveUserByUsername(username)
            .filter(user -> verifyPassword(password, user.passwordHash(), user.passwordSalt(), user.hashAlgorithm()))
            .map(XmppUser::jid)
            .doOnSuccess(jid -> {
                if (jid != null) {
                    updateLastLogin(jid).subscribe();
                    logger.info("PLAIN authentication successful for user: {}", username);
                } else {
                    logger.warn("PLAIN authentication failed for user: {}", username);
                }
            });
    }
    
    /**
     * Get stored credentials for SCRAM authentication.
     * @param username The username
     * @return Mono containing SCRAM credentials if user exists
     */
    public Mono<ScramCredentials> getScramCredentials(String username) {
        return userRepository.findActiveUserByUsername(username)
            .map(user -> new ScramCredentials(
                user.jid(),
                user.passwordHash(),
                user.passwordSalt(),
                4096, // iteration count
                user.hashAlgorithm()
            ))
            .doOnSuccess(creds -> {
                if (creds != null) {
                    logger.debug("Retrieved SCRAM credentials for user: {}", username);
                }
            });
    }
    
    /**
     * Create a new user with hashed password.
     * @param username The username
     * @param password The plain text password
     * @return Mono containing the created user's JID
     */
    public Mono<String> createUser(String username, String password) {
        return userRepository.existsByUsername(username)
            .flatMap(exists -> {
                if (exists) {
                    return Mono.error(new IllegalArgumentException("User already exists: " + username));
                }
                
                // Generate salt and hash password
                String salt = generateSalt();
                String hashedPassword = hashPassword(password, salt, "SCRAM-SHA-256");
                
                XmppUser newUser = XmppUser.create(username, hashedPassword, salt);
                return userRepository.save(newUser).map(XmppUser::jid);
            })
            .doOnSuccess(jid -> logger.info("Created new user: {}", username));
    }
    
    /**
     * Verify password against stored hash.
     */
    private boolean verifyPassword(String password, String storedHash, String salt, String algorithm) {
        try {
            String computedHash = hashPassword(password, salt, algorithm);
            return computedHash.equals(storedHash);
        } catch (Exception e) {
            logger.error("Error verifying password: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Hash password using specified algorithm.
     */
    private String hashPassword(String password, String salt, String algorithm) {
        try {
            switch (algorithm) {
                case "SCRAM-SHA-1":
                    return pbkdf2Hash(password, salt, 4096, "HmacSHA1");
                case "SCRAM-SHA-256":
                    return pbkdf2Hash(password, salt, 4096, "HmacSHA256");
                default:
                    // Fallback to simple SHA-256 for demo
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    digest.update((password + salt).getBytes(StandardCharsets.UTF_8));
                    return Base64.getEncoder().encodeToString(digest.digest());
            }
        } catch (Exception e) {
            logger.error("Error hashing password: {}", e.getMessage());
            throw new RuntimeException("Password hashing failed", e);
        }
    }
    
    /**
     * PBKDF2 password hashing for SCRAM mechanisms.
     */
    private String pbkdf2Hash(String password, String salt, int iterations, String algorithm) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(password.getBytes(StandardCharsets.UTF_8), algorithm);
        Mac mac = Mac.getInstance(algorithm);
        mac.init(keySpec);
        
        byte[] saltBytes = Base64.getDecoder().decode(salt);
        byte[] hash = mac.doFinal(saltBytes);
        
        // Simplified PBKDF2 - real implementation would do proper iterations
        for (int i = 1; i < iterations; i++) {
            hash = mac.doFinal(hash);
        }
        
        return Base64.getEncoder().encodeToString(hash);
    }
    
    /**
     * Generate random salt for password hashing.
     */
    private String generateSalt() {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    /**
     * Update user's last login timestamp.
     */
    private Mono<Integer> updateLastLogin(String jid) {
        return userRepository.updateLastLogin(jid, java.time.Instant.now())
            .doOnSuccess(count -> logger.debug("Updated last login for user: {}", jid));
    }
    
    /**
     * Record class for SCRAM authentication credentials.
     */
    public static record ScramCredentials(
        String jid,
        String passwordHash,
        String salt,
        int iterations,
        String algorithm
    ) {}
}