package io.github.koosty.xmpp.auth;

/**
 * Interface for SASL mechanism handlers.
 * Each SASL mechanism (PLAIN, SCRAM-SHA-1, SCRAM-SHA-256) implements this interface.
 */
public interface SaslMechanismHandler {
    
    /**
     * Process the initial authentication request.
     * @param authData The initial authentication data from the client
     * @return Challenge data to send to client, or null if complete
     * @throws Exception if authentication processing fails
     */
    String processInitialAuth(String authData) throws Exception;
    
    /**
     * Process an authentication response from the client.
     * @param responseData The response data from the client
     * @return Challenge data to send to client, or null if complete/failed
     * @throws Exception if response processing fails
     */
    String processResponse(String responseData) throws Exception;
    
    /**
     * Check if the authentication process is complete.
     * @return true if authentication is complete (success or failure)
     */
    boolean isComplete();
    
    /**
     * Get the authenticated JID if authentication was successful.
     * @return the authenticated JID, or null if not authenticated
     */
    String getAuthenticatedJid();
    
    /**
     * Reset the handler state for reuse.
     */
    void reset();
}