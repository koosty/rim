package io.github.koosty.xmpp.service.impl;

import io.github.koosty.xmpp.service.SaslAuthenticationService;
import io.github.koosty.xmpp.auth.SaslMechanismHandler;
import io.github.koosty.xmpp.auth.PlainMechanismHandler;
import io.github.koosty.xmpp.auth.ScramSha1MechanismHandler;
import io.github.koosty.xmpp.auth.ScramSha256MechanismHandler;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of SASL authentication service.
 * Extracts logic from SaslAuthenticationActor for service-based architecture.
 */
@Service
public class DefaultSaslAuthenticationService implements SaslAuthenticationService {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultSaslAuthenticationService.class);
    
    private final Map<String, SaslMechanismHandler> mechanismHandlers = new HashMap<>();
    private final ConcurrentMap<String, SaslState> connectionStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> authenticatedJids = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> currentMechanisms = new ConcurrentHashMap<>();
    
    private enum SaslState {
        IDLE, AUTHENTICATING, SUCCESS, FAILURE
    }
    
    public DefaultSaslAuthenticationService() {
        initializeMechanismHandlers();
    }
    
    private void initializeMechanismHandlers() {
        mechanismHandlers.put("PLAIN", new PlainMechanismHandler());
        mechanismHandlers.put("SCRAM-SHA-1", new ScramSha1MechanismHandler());
        mechanismHandlers.put("SCRAM-SHA-256", new ScramSha256MechanismHandler());
    }
    
    @Override
    public Mono<SaslAuthenticationResult> processAuth(String connectionId, String xmlData) {
        logger.debug("Processing SASL authentication for connection: {}", connectionId);
        
        return Mono.fromCallable(() -> {
            try {
                if (xmlData.contains("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl'")) {
                    return handleSaslAuth(connectionId, xmlData);
                } else if (xmlData.contains("<response xmlns='urn:ietf:params:xml:ns:xmpp-sasl'")) {
                    return handleSaslResponse(connectionId, xmlData);
                } else {
                    logger.warn("Unknown SASL XML format for connection: {}", connectionId);
                    return SaslAuthenticationResult.failure(
                        "malformed-request", 
                        "Unknown SASL request format",
                        generateAuthFailure("malformed-request", "Unknown SASL request format")
                    );
                }
            } catch (Exception e) {
                logger.error("Error processing SASL authentication for connection {}: {}", connectionId, e.getMessage(), e);
                return SaslAuthenticationResult.failure(
                    "temporary-auth-failure",
                    "Internal authentication error",
                    generateAuthFailure("temporary-auth-failure", "Internal authentication error")
                );
            }
        });
    }
    
    @Override
    public List<String> getSupportedMechanisms(String connectionId, boolean tlsEstablished) {
        List<String> mechanisms = new ArrayList<>();
        
        // PLAIN mechanism only available over TLS
        if (tlsEstablished) {
            mechanisms.add("PLAIN");
        }
        
        // SCRAM mechanisms are always available
        mechanisms.add("SCRAM-SHA-1");
        mechanisms.add("SCRAM-SHA-256");
        
        return mechanisms;
    }
    
    @Override
    public boolean isAuthenticated(String connectionId) {
        return connectionStates.getOrDefault(connectionId, SaslState.IDLE) == SaslState.SUCCESS;
    }
    
    @Override
    public String getAuthenticatedJid(String connectionId) {
        return authenticatedJids.get(connectionId);
    }
    
    @Override
    public void cleanupConnection(String connectionId) {
        connectionStates.remove(connectionId);
        authenticatedJids.remove(connectionId);
        currentMechanisms.remove(connectionId);
        logger.debug("Cleaned up SASL state for connection: {}", connectionId);
    }
    
    private SaslAuthenticationResult handleSaslAuth(String connectionId, String xmlData) {
        SaslState currentState = connectionStates.getOrDefault(connectionId, SaslState.IDLE);
        if (currentState != SaslState.IDLE) {
            logger.warn("SASL authentication already in progress for connection: {}", connectionId);
            return SaslAuthenticationResult.failure(
                "temporary-auth-failure",
                "Authentication already in progress",
                generateAuthFailure("temporary-auth-failure", "Authentication already in progress")
            );
        }
        
        String mechanism = extractAttribute(xmlData, "mechanism");
        String authData = extractTextContent(xmlData);
        
        if (mechanism == null) {
            return SaslAuthenticationResult.failure(
                "malformed-request",
                "Missing mechanism attribute",
                generateAuthFailure("malformed-request", "Missing mechanism attribute")
            );
        }
        
        SaslMechanismHandler handler = mechanismHandlers.get(mechanism);
        if (handler == null) {
            logger.warn("Unsupported SASL mechanism: {} for connection: {}", mechanism, connectionId);
            return SaslAuthenticationResult.failure(
                "invalid-mechanism",
                "Unsupported mechanism: " + mechanism,
                generateAuthFailure("invalid-mechanism", "Unsupported mechanism")
            );
        }
        
        connectionStates.put(connectionId, SaslState.AUTHENTICATING);
        currentMechanisms.put(connectionId, mechanism);
        
        try {
            String challengeData = handler.processInitialAuth(authData);
            
            if (handler.isComplete()) {
                // Authentication completed (success or failure)
                String authenticatedJid = handler.getAuthenticatedJid();
                if (authenticatedJid != null) {
                    // Authentication successful
                    authenticatedJids.put(connectionId, authenticatedJid);
                    connectionStates.put(connectionId, SaslState.SUCCESS);
                    
                    logger.info("SASL authentication successful for connection {}: {}", connectionId, authenticatedJid);
                    
                    return SaslAuthenticationResult.success(
                        authenticatedJid,
                        generateAuthSuccess()
                    );
                } else {
                    // Authentication failed
                    connectionStates.put(connectionId, SaslState.FAILURE);
                    return SaslAuthenticationResult.failure(
                        "not-authorized",
                        "Authentication failed",
                        generateAuthFailure("not-authorized", "Authentication failed")
                    );
                }
            } else if (challengeData != null) {
                // Send challenge
                return SaslAuthenticationResult.challenge(
                    generateChallenge(challengeData)
                );
            } else {
                // Authentication failed
                connectionStates.put(connectionId, SaslState.FAILURE);
                return SaslAuthenticationResult.failure(
                    "not-authorized",
                    "Authentication failed",
                    generateAuthFailure("not-authorized", "Authentication failed")
                );
            }
        } catch (Exception e) {
            logger.error("Error during SASL authentication for connection {}: {}", connectionId, e.getMessage(), e);
            connectionStates.put(connectionId, SaslState.FAILURE);
            return SaslAuthenticationResult.failure(
                "temporary-auth-failure",
                "Authentication processing error",
                generateAuthFailure("temporary-auth-failure", "Authentication processing error")
            );
        }
    }
    
    private SaslAuthenticationResult handleSaslResponse(String connectionId, String xmlData) {
        SaslState currentState = connectionStates.getOrDefault(connectionId, SaslState.IDLE);
        if (currentState != SaslState.AUTHENTICATING) {
            logger.warn("Received SASL response without active authentication for connection: {}", connectionId);
            return SaslAuthenticationResult.failure(
                "malformed-request",
                "No active authentication session",
                generateAuthFailure("malformed-request", "No active authentication session")
            );
        }
        
        String mechanism = currentMechanisms.get(connectionId);
        SaslMechanismHandler handler = mechanismHandlers.get(mechanism);
        
        if (handler == null) {
            logger.error("Lost SASL handler for mechanism: {} connection: {}", mechanism, connectionId);
            return SaslAuthenticationResult.failure(
                "temporary-auth-failure",
                "Lost authentication handler",
                generateAuthFailure("temporary-auth-failure", "Lost authentication handler")
            );
        }
        
        String responseData = extractTextContent(xmlData);
        
        try {
            String challengeData = handler.processResponse(responseData);
            
            if (handler.isComplete()) {
                // Authentication completed (success or failure)
                String authenticatedJid = handler.getAuthenticatedJid();
                if (authenticatedJid != null) {
                    // Authentication successful
                    authenticatedJids.put(connectionId, authenticatedJid);
                    connectionStates.put(connectionId, SaslState.SUCCESS);
                    
                    logger.info("SASL authentication successful for connection {}: {}", connectionId, authenticatedJid);
                    
                    return SaslAuthenticationResult.success(
                        authenticatedJid,
                        generateAuthSuccess()
                    );
                } else {
                    // Authentication failed
                    connectionStates.put(connectionId, SaslState.FAILURE);
                    return SaslAuthenticationResult.failure(
                        "not-authorized",
                        "Authentication failed",
                        generateAuthFailure("not-authorized", "Authentication failed")
                    );
                }
            } else if (challengeData != null) {
                // Send challenge
                return SaslAuthenticationResult.challenge(
                    generateChallenge(challengeData)
                );
            } else {
                // Authentication failed
                connectionStates.put(connectionId, SaslState.FAILURE);
                return SaslAuthenticationResult.failure(
                    "not-authorized",
                    "Authentication failed",
                    generateAuthFailure("not-authorized", "Authentication failed")
                );
            }
        } catch (Exception e) {
            logger.error("Error processing SASL response for connection {}: {}", connectionId, e.getMessage(), e);
            connectionStates.put(connectionId, SaslState.FAILURE);
            return SaslAuthenticationResult.failure(
                "temporary-auth-failure",
                "Response processing error",
                generateAuthFailure("temporary-auth-failure", "Response processing error")
            );
        }
    }
    
    private String extractAttribute(String xml, String attributeName) {
        Pattern pattern = Pattern.compile(attributeName + "=['\"]([^'\"]*)['\"]");
        Matcher matcher = pattern.matcher(xml);
        return matcher.find() ? matcher.group(1) : null;
    }
    
    private String extractTextContent(String xml) {
        Pattern pattern = Pattern.compile(">([^<]*)<");
        Matcher matcher = pattern.matcher(xml);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
    
    private String generateAuthSuccess() {
        return "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>";
    }
    
    private String generateChallenge(String challengeData) {
        return String.format(
            "<challenge xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>%s</challenge>",
            challengeData
        );
    }
    
    private String generateAuthFailure(String condition, String text) {
        return String.format(
            "<failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><%s/>%s</failure>",
            condition,
            text != null ? "<text>" + text + "</text>" : ""
        );
    }
}