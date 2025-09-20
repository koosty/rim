package io.github.koosty.xmpp.auth;

import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SASL SCRAM-SHA-256 mechanism handler according to RFC7677.
 * This is a simplified implementation for demonstration purposes.
 */
public class ScramSha256MechanismHandler implements SaslMechanismHandler {
    private static final Logger logger = LoggerFactory.getLogger(ScramSha256MechanismHandler.class);
    
    private boolean complete = false;
    private String authenticatedJid = null;
    private String clientNonce = null;
    private String serverNonce = null;
    private String username = null;
    
    @Override
    public String processInitialAuth(String authData) throws Exception {
        if (authData == null || authData.isEmpty()) {
            return null; // No initial data, wait for response
        }
        
        return processClientFirstMessage(authData);
    }
    
    @Override
    public String processResponse(String responseData) throws Exception {
        // In SCRAM, this would handle the client-final-message
        // For simplicity, we'll just accept any response after the first challenge
        complete = true;
        authenticatedJid = username + "@localhost";
        logger.info("SCRAM-SHA-256 authentication successful for user: {}", username);
        return null;
    }
    
    private String processClientFirstMessage(String authData) throws Exception {
        try {
            // Decode base64 auth data
            byte[] decodedData = Base64.getDecoder().decode(authData);
            String clientFirstMessage = new String(decodedData);
            
            // Parse client-first-message: n,,n=username,r=clientNonce
            // This is a simplified parsing - real implementation would be more robust
            if (clientFirstMessage.startsWith("n,,")) {
                String usernamePart = clientFirstMessage.substring(3);
                
                // Extract username and client nonce
                String[] parts = usernamePart.split(",");
                for (String part : parts) {
                    if (part.startsWith("n=")) {
                        username = part.substring(2);
                    } else if (part.startsWith("r=")) {
                        clientNonce = part.substring(2);
                    }
                }
                
                if (username != null && clientNonce != null) {
                    // Generate server nonce
                    serverNonce = clientNonce + generateRandomString();
                    
                    // Create server-first-message: r=clientNonce+serverNonce,s=salt,i=iteration
                    String salt = Base64.getEncoder().encodeToString("salt256".getBytes());
                    String serverFirstMessage = "r=" + serverNonce + ",s=" + salt + ",i=4096";
                    
                    logger.debug("SCRAM-SHA-256 sending challenge for user: {}", username);
                    return serverFirstMessage;
                } else {
                    logger.warn("Invalid SCRAM-SHA-256 client-first-message format");
                    complete = true;
                    return null;
                }
            } else {
                logger.warn("Invalid SCRAM-SHA-256 message format");
                complete = true;
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Error processing SCRAM-SHA-256 authentication: {}", e.getMessage());
            complete = true;
            throw e;
        }
    }
    
    private String generateRandomString() {
        return String.valueOf(System.currentTimeMillis() % 10000);
    }
    
    @Override
    public boolean isComplete() {
        return complete;
    }
    
    @Override
    public String getAuthenticatedJid() {
        return authenticatedJid;
    }
    
    @Override
    public void reset() {
        complete = false;
        authenticatedJid = null;
        clientNonce = null;
        serverNonce = null;
        username = null;
    }
}