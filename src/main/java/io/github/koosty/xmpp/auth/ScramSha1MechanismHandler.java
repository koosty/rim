package io.github.koosty.xmpp.auth;

import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SASL SCRAM-SHA-1 mechanism handler according to RFC5802.
 * This is a simplified implementation for demonstration purposes.
 */
public class ScramSha1MechanismHandler implements SaslMechanismHandler {
    private static final Logger logger = LoggerFactory.getLogger(ScramSha1MechanismHandler.class);
    
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
        logger.info("SCRAM-SHA-1 authentication successful for user: {}", username);
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
                    String salt = Base64.getEncoder().encodeToString("salt".getBytes());
                    String serverFirstMessage = "r=" + serverNonce + ",s=" + salt + ",i=4096";
                    
                    logger.debug("SCRAM-SHA-1 sending challenge for user: {}", username);
                    return serverFirstMessage;
                } else {
                    logger.warn("Invalid SCRAM-SHA-1 client-first-message format");
                    complete = true;
                    return null;
                }
            } else {
                logger.warn("Invalid SCRAM-SHA-1 message format");
                complete = true;
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Error processing SCRAM-SHA-1 authentication: {}", e.getMessage());
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