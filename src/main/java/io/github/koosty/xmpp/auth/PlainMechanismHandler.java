package io.github.koosty.xmpp.auth;

import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * SASL PLAIN mechanism handler according to RFC4616.
 * Handles authentication in the format: [authzid]\0authcid\0passwd
 */
public class PlainMechanismHandler implements SaslMechanismHandler {
    private static final Logger logger = LoggerFactory.getLogger(PlainMechanismHandler.class);
    
    private final UserAuthenticationService authenticationService;
    private boolean complete = false;
    private String authenticatedJid = null;
    
    public PlainMechanismHandler(UserAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    
    @Override
    public String processInitialAuth(String authData) throws Exception {
        if (authData == null || authData.isEmpty()) {
            return null; // No initial data, wait for response
        }
        
        return processAuthData(authData);
    }
    
    @Override
    public String processResponse(String responseData) throws Exception {
        return processAuthData(responseData);
    }
    
    private String processAuthData(String authData) throws Exception {
        logger.debug("Processing auth data: {}", authData);
        try {
            // Decode base64 auth data
            byte[] decodedData = Base64.getDecoder().decode(authData);
            String authString = new String(decodedData);
            logger.debug("Decoded auth string: {}", authString);
            
            // Parse PLAIN format: [authzid]\0authcid\0passwd
            String[] parts = authString.split("\0");
            
            if (parts.length < 2) {
                logger.warn("Invalid PLAIN authentication format");
                complete = true;
                return null;
            }
            
            String authcid = parts.length >= 3 ? parts[1] : parts[0];
            String passwd = parts.length >= 3 ? parts[2] : parts[1];
            
            // Use UserAuthenticationService to verify credentials
            if (!authcid.isEmpty() && !passwd.isEmpty()) {
                try {
                    // Block on the Mono to get the result synchronously
                    // TODO: In future, make the SASL handler interface async
                    String resultJid = authenticationService.authenticatePlain(authcid, passwd)
                        .block(java.time.Duration.ofSeconds(5)); // 5 second timeout
                    
                    if (resultJid != null) {
                        authenticatedJid = resultJid;
                        complete = true;
                        logger.info("PLAIN authentication successful for user: {}", authcid);
                        return null; // Success, no challenge needed
                    } else {
                        logger.warn("PLAIN authentication failed for user: {}", authcid);
                        complete = true;
                        return null;
                    }
                } catch (Exception e) {
                    logger.error("PLAIN authentication error for user {}: {}", authcid, e.getMessage());
                    complete = true;
                    return null;
                }
            } else {
                logger.warn("PLAIN authentication failed: empty credentials");
                complete = true;
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Error processing PLAIN authentication: {}", e.getMessage());
            complete = true;
            throw e;
        }
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
    }
}