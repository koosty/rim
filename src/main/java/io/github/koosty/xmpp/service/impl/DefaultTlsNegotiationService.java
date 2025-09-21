package io.github.koosty.xmpp.service.impl;

import io.github.koosty.xmpp.service.TlsNegotiationService;
import io.github.koosty.xmpp.config.XmppSecurityProperties;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default implementation of TLS negotiation service.
 * Extracts logic from TlsNegotiationActor for service-based architecture.
 */
@Service
public class DefaultTlsNegotiationService implements TlsNegotiationService {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultTlsNegotiationService.class);
    
    private final XmppSecurityProperties securityProperties;
    private final ConcurrentMap<String, TlsState> connectionStates = new ConcurrentHashMap<>();
    
    private enum TlsState {
        IDLE, NEGOTIATING, SUCCESS, FAILURE
    }
    
    public DefaultTlsNegotiationService(XmppSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }
    
    @Override
    public Mono<TlsNegotiationResult> processStartTls(String connectionId, String xmlData) {
        logger.debug("Processing STARTTLS request for connection: {}", connectionId);
        
        if (!securityProperties.getTls().isEnabled()) {
            logger.warn("STARTTLS requested but TLS is disabled for connection: {}", connectionId);
            return Mono.just(TlsNegotiationResult.failure(
                "TLS not supported", 
                generateTlsFailure("policy-violation")
            ));
        }
        
        TlsState currentState = connectionStates.getOrDefault(connectionId, TlsState.IDLE);
        if (currentState != TlsState.IDLE) {
            logger.warn("TLS negotiation already in progress for connection: {}", connectionId);
            return Mono.just(TlsNegotiationResult.failure(
                "TLS negotiation already in progress",
                generateTlsFailure("temporary-auth-failure")
            ));
        }
        
        return processTlsNegotiation(connectionId);
    }
    
    @Override
    public boolean isTlsRequired(String connectionId) {
        return securityProperties.getTls().isEnabled() && securityProperties.getTls().isRequired();
    }
    
    private Mono<TlsNegotiationResult> processTlsNegotiation(String connectionId) {
        connectionStates.put(connectionId, TlsState.NEGOTIATING);
        
        return Mono.fromCallable(() -> {
            try {
                // Generate TLS proceed response
                String tlsProceed = generateTlsProceed();
                
                // In a real implementation, this would handle actual TLS upgrade
                // For now, simulate successful TLS negotiation
                simulateTlsUpgrade(connectionId);
                
                connectionStates.put(connectionId, TlsState.SUCCESS);
                logger.info("TLS negotiation successful for connection: {}", connectionId);
                
                return TlsNegotiationResult.success(tlsProceed);
                
            } catch (Exception e) {
                logger.error("TLS negotiation failed for connection {}: {}", connectionId, e.getMessage());
                connectionStates.put(connectionId, TlsState.FAILURE);
                return TlsNegotiationResult.failure(
                    "TLS upgrade failed: " + e.getMessage(),
                    generateTlsFailure("temporary-auth-failure")
                );
            }
        });
    }
    
    private void simulateTlsUpgrade(String connectionId) throws Exception {
        // In a real implementation, this would:
        // 1. Create SSL engine
        // 2. Wrap the connection with TLS
        // 3. Perform handshake
        
        // Create SSL engine to validate configuration
        createSSLEngine();
        
        // Simulate processing time
        Thread.sleep(50);
        
        logger.debug("TLS upgrade simulation completed for connection: {}", connectionId);
    }
    
    private SSLEngine createSSLEngine() throws Exception {
        SSLContext sslContext = SSLContext.getDefault();
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false);
        sslEngine.setNeedClientAuth(false);
        return sslEngine;
    }
    
    private String generateTlsProceed() {
        return "<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>";
    }
    
    private String generateTlsFailure(String condition) {
        return String.format(
            "<failure xmlns='urn:ietf:params:xml:ns:xmpp-tls'><%s/></failure>",
            condition
        );
    }
    
    /**
     * Clean up connection state when connection closes
     */
    public void cleanupConnection(String connectionId) {
        connectionStates.remove(connectionId);
        logger.debug("Cleaned up TLS state for connection: {}", connectionId);
    }
    
    /**
     * Get current TLS state for connection
     */
    public boolean isTlsEstablished(String connectionId) {
        return connectionStates.getOrDefault(connectionId, TlsState.IDLE) == TlsState.SUCCESS;
    }
}