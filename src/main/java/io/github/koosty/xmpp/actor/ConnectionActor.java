package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.*;
import io.github.koosty.xmpp.config.XmppSecurityProperties;
import io.github.koosty.xmpp.stream.XmlStreamProcessor;
import io.github.koosty.xmpp.features.StreamFeaturesManager;
import io.github.koosty.xmpp.service.TlsNegotiationService;
import io.github.koosty.xmpp.service.SaslAuthenticationService;
import io.github.koosty.xmpp.service.ResourceBindingService;
import io.github.koosty.xmpp.service.IqProcessingService;
import reactor.netty.NettyOutbound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Actor for managing individual XMPP client connections with isolated state.
 * Processes messages sequentially to avoid race conditions.
 */
public class ConnectionActor {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionActor.class);
    
    private final String connectionId;
    private final BlockingQueue<ActorMessage> mailbox = new LinkedBlockingQueue<>();
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.CONNECTED);
    private final XmlStreamProcessor xmlProcessor;
    private final Consumer<OutgoingStanzaMessage> outboundSender;
    private final StreamFeaturesManager featuresManager;
    private final ActorSystem actorSystem;
    private final XmppSecurityProperties securityProperties;
    
    // Injected services for handling specific XMPP operations
    private final TlsNegotiationService tlsNegotiationService;
    private final SaslAuthenticationService saslAuthenticationService;
    private final ResourceBindingService resourceBindingService;
    private final IqProcessingService iqProcessingService;
    
    private volatile Thread processingThread;
    private volatile boolean running = true;
    
    private volatile NettyOutbound nettyOutbound;
    
    // Connection-specific state
    private String streamId;
    private String clientJid;
    private String serverDomain;
    private boolean tlsEstablished = false;
    private boolean authenticated = false;
    private String authenticatedJid;
    private boolean awaitingPostSaslStream = false;
    
    public ConnectionActor(String connectionId, XmlStreamProcessor xmlProcessor, 
                          Consumer<OutgoingStanzaMessage> outboundSender, 
                          StreamFeaturesManager featuresManager,
                          ActorSystem actorSystem,
                          XmppSecurityProperties securityProperties,
                          TlsNegotiationService tlsNegotiationService,
                          SaslAuthenticationService saslAuthenticationService,
                          ResourceBindingService resourceBindingService,
                          IqProcessingService iqProcessingService) {
        this.connectionId = connectionId;
        this.xmlProcessor = xmlProcessor;
        this.outboundSender = outboundSender;
        this.featuresManager = featuresManager;
        this.actorSystem = actorSystem;
        this.securityProperties = securityProperties;
        this.tlsNegotiationService = tlsNegotiationService;
        this.saslAuthenticationService = saslAuthenticationService;
        this.resourceBindingService = resourceBindingService;
        this.iqProcessingService = iqProcessingService;
    }
    
    /**
     * Send message to this actor's mailbox
     */
    public void tell(ActorMessage message) {
        if (running) {
            if (!mailbox.offer(message)) {
                logger.warn("Failed to enqueue message for connection {}: mailbox full", connectionId);
            }
        }
    }
    
    /**
     * Start the actor's message processing thread
     */
    public void start() {
        if (processingThread == null) {
            processingThread = new Thread(this::processMessages);
            processingThread.setDaemon(true);
            processingThread.setName("ConnectionActor-" + connectionId);
            processingThread.start();
            logger.info("Started ConnectionActor for connection {}", connectionId);
        }
    }
    
    /**
     * Stop the actor gracefully
     */
    public void stop() {
        running = false;
        if (processingThread != null) {
            processingThread.interrupt();
        }
        logger.info("Stopped ConnectionActor for connection {}", connectionId);
    }
    
    /**
     * Check if actor is healthy
     */
    public boolean isHealthy() {
        return running && processingThread != null && processingThread.isAlive();
    }
    
    /**
     * Get current connection state
     */
    public ConnectionState getState() {
        return state.get();
    }
    
    private void processMessages() {
        logger.debug("Starting message processing for connection {}", connectionId);
        
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                ActorMessage message = mailbox.take();
                handleMessage(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error processing message for connection {}: {}", connectionId, e.getMessage(), e);
                // Continue processing other messages
            }
        }
        
        logger.debug("Stopped message processing for connection {}", connectionId);
    }
    
    private void handleMessage(ActorMessage message) {
        logger.debug("Processing message type {} for connection {}", message.getType(), connectionId);
        
        switch (message.getType()) {
            case INCOMING_XML -> processIncomingXml((IncomingXmlMessage) message);
            case STREAM_INITIATION -> handleStreamInitiation((StreamInitiationMessage) message);
            case RESOURCE_BINDING -> handleResourceBinding((ResourceBindingMessage) message);
            case CONNECTION_CLOSED -> handleConnectionClosed((ConnectionClosedMessage) message);
            default -> logger.warn("Unhandled message type {} for connection {}", message.getType(), connectionId);
        }
    }
    
    private void processIncomingXml(IncomingXmlMessage message) {
        String xmlData = message.xmlData();
        
        // Check if this is a stream header
        if (xmlProcessor.isValidStreamHeader(xmlData)) {
            handleStreamOpen(xmlData);
            return;
        }
        
        // Check for stream close
        if (xmlData.trim().equals("</stream:stream>")) {
            handleStreamClose();
            return;
        }
        
        // Parse XML stanza
        try {
            // Route messages based on connection state and XML content
            if (xmlData.contains("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'")) {
                // Check if TLS is enabled before processing
                if (!securityProperties.getTls().isEnabled()) {
                    logger.warn("STARTTLS requested but TLS is disabled for connection {}", connectionId);
                    sendTlsNotSupported();
                    return;
                }
                
                // Process TLS directly with service
                handleTlsNegotiation(new TlsNegotiationMessage(connectionId, xmlData, message.timestamp()));
                return;
            }
            
            if (xmlData.contains("xmlns='urn:ietf:params:xml:ns:xmpp-sasl'")) {
                // Process SASL directly with service
                // Extract mechanism and data from XML for service call
                String mechanism = extractSaslMechanism(xmlData);
                String data = extractSaslData(xmlData);
                handleSaslAuth(new SaslAuthMessage(connectionId, mechanism, data, message.timestamp()));
                return;
            }
            
            // Handle resource binding and session IQ stanzas
            if (authenticated && xmlData.contains("<iq") && 
                (xmlData.contains("xmlns='urn:ietf:params:xml:ns:xmpp-bind'") || 
                 xmlData.contains("xmlns='urn:ietf:params:xml:ns:xmpp-session'"))) {
                logger.debug("Processing resource binding/session IQ with service");
                handleResourceBinding(ResourceBindingMessage.of(connectionId, xmlData));
                return;
            }
            
            // Handle general IQ stanzas (ping, version, disco, etc.)
            if (authenticated && xmlData.contains("<iq")) {
                logger.debug("Processing general IQ stanza with IQ processing service");
                handleIqProcessing(xmlData);
                return;
            }
            
            if (state.get() == ConnectionState.STREAM_INITIATED && !authenticated) {
                // Send initial stream features
                sendStreamFeatures();
                state.set(ConnectionState.TLS_NEGOTIATING);
            } else {
                // Process regular stanza
                logger.debug("Received stanza on connection {}: {}", connectionId, xmlData);
            }
        } catch (Exception e) {
            logger.error("Error processing XML for connection {}: {}", connectionId, e.getMessage());
            sendStreamError("not-well-formed", "Invalid XML");
        }
    }
    
    private void handleStreamOpen(String streamHeader) {
        logger.info("Stream opened for connection {} - authenticated: {}, awaitingPostSaslStream: {}", 
                   connectionId, authenticated, awaitingPostSaslStream);
        
        // Generate stream ID and response header
        streamId = xmlProcessor.generateStreamId();
        serverDomain = "localhost"; // TODO: Get from configuration
        
        xmlProcessor.generateStreamHeader(serverDomain, null, streamId)
            .doOnNext(header -> {
                outboundSender.accept(OutgoingStanzaMessage.of(connectionId, header));
                
                // If authenticated and doing stream restart, set flag for post-SASL features
                if (authenticated && !awaitingPostSaslStream) {
                    awaitingPostSaslStream = true;
                    logger.debug("Set awaitingPostSaslStream=true for stream restart after authentication");
                }
                
                // Send appropriate stream features based on connection state
                sendStreamFeatures();
            })
            .subscribe();
            
        // Update state based on current authentication status
        if (authenticated) {
            state.set(ConnectionState.AUTHENTICATED);
        } else {
            state.set(ConnectionState.STREAM_INITIATED);
        }
    }
    
    private void handleStreamClose() {
        logger.info("Stream close requested for connection {}", connectionId);
        state.set(ConnectionState.CLOSING);
        
        xmlProcessor.generateStreamClose()
            .doOnNext(closeTag -> outboundSender.accept(OutgoingStanzaMessage.of(connectionId, closeTag)))
            .subscribe();
            
        state.set(ConnectionState.CLOSED);
        stop();
    }
    
    private void sendStreamFeatures() {
        String features;
        
        logger.debug("sendStreamFeatures() - tlsEstablished: {}, authenticated: {}, awaitingPostSaslStream: {}", 
                    tlsEstablished, authenticated, awaitingPostSaslStream);
        
        // Handle post-SASL features first (after authentication)
        if (authenticated && awaitingPostSaslStream) {
            logger.debug("Generating post-SASL features (bind/session)");
            features = featuresManager.generatePostSaslFeatures();
            // Clear the flag after sending post-SASL features
            awaitingPostSaslStream = false;
            logger.debug("Cleared awaitingPostSaslStream flag for connection {}", connectionId);
        } else if (!tlsEstablished) {
            logger.debug("Generating initial features (pre-TLS)");
            features = featuresManager.generateInitialFeatures();
        } else if (!authenticated && !awaitingPostSaslStream) {
            logger.debug("Generating post-TLS features (SASL)");
            features = featuresManager.generatePostTlsFeatures();
        } else {
            logger.debug("Generating post-SASL features (bind/session) - fallback");
            features = featuresManager.generatePostSaslFeatures();
        }
        
        outboundSender.accept(OutgoingStanzaMessage.of(connectionId, features));
    }
    
    private void sendStreamError(String condition, String text) {
        xmlProcessor.generateStreamError(condition, text)
            .doOnNext(error -> outboundSender.accept(OutgoingStanzaMessage.of(connectionId, error)))
            .doOnNext(error -> state.set(ConnectionState.ERROR))
            .subscribe();
    }
    
    private void handleStreamInitiation(StreamInitiationMessage message) {
        logger.debug("Handling stream initiation for connection {}", connectionId);
        // Stream initiation is handled through processIncomingXml -> handleStreamOpen
        // This method exists for future extension if needed
    }
    
    private void handleTlsNegotiation(TlsNegotiationMessage message) {
        logger.debug("Handling TLS negotiation for connection {}", connectionId);
        
        // Use TLS service directly instead of delegating to actor  
        tlsNegotiationService.processStartTls(connectionId, message.command())
            .subscribe(
                result -> {
                    // Send TLS response back to client
                    if (result.responseXml() != null && !result.responseXml().isEmpty()) {
                        outboundSender.accept(OutgoingStanzaMessage.of(connectionId, result.responseXml()));
                    }
                    
                    // Update TLS state if successful
                    if (result.success()) {
                        tlsEstablished = true;
                        state.set(ConnectionState.TLS_ESTABLISHED);
                        logger.info("TLS established for connection {}", connectionId);
                        
                        // Trigger stream restart after TLS
                        sendStreamFeaturesAfterTls();
                    } else {
                        logger.warn("TLS negotiation failed for connection {}: {}", connectionId, result.errorReason());
                        state.set(ConnectionState.ERROR);
                    }
                },
                error -> {
                    logger.error("Error during TLS negotiation for connection {}: {}", connectionId, error.getMessage());
                    state.set(ConnectionState.ERROR);
                }
            );
    }
    
    private void handleSaslAuth(SaslAuthMessage message) {
        logger.debug("Handling SASL auth for connection {}", connectionId);
        
        // Use SASL service directly instead of delegating to actor
        // Build XML from mechanism and data for service call
        String authXml = buildSaslAuthXml(message.mechanism(), message.data());
        saslAuthenticationService.processAuth(connectionId, authXml)
            .subscribe(
                result -> {
                    // Send SASL response back to client
                    if (result.responseXml() != null && !result.responseXml().isEmpty()) {
                        outboundSender.accept(OutgoingStanzaMessage.of(connectionId, result.responseXml()));
                    }
                    
                    // Update authentication state if successful
                    if (result.success()) {
                        authenticated = true;
                        authenticatedJid = result.authenticatedJid();
                        state.set(ConnectionState.AUTHENTICATED);
                        logger.info("SASL authentication successful for connection {}, JID: {}", connectionId, authenticatedJid);
                        
                        // Trigger stream restart after SASL
                        awaitingPostSaslStream = true;
                        sendStreamFeaturesAfterSasl();
                    } else {
                        logger.warn("SASL authentication failed for connection {}: {} - {}", connectionId, result.errorCondition(), result.errorText());
                        state.set(ConnectionState.ERROR);
                    }
                },
                error -> {
                    logger.error("Error during SASL authentication for connection {}: {}", connectionId, error.getMessage());
                    state.set(ConnectionState.ERROR);
                }
            );
    }
    
    private void handleResourceBinding(ResourceBindingMessage message) {
        logger.debug("Handling resource binding for connection {}", connectionId);
        
        // Use ResourceBindingService directly
        resourceBindingService.processResourceBinding(connectionId, authenticatedJid, message.xmlData())
            .subscribe(
                result -> {
                    // Send response back to client
                    if (result.responseXml() != null && !result.responseXml().isEmpty()) {
                        outboundSender.accept(OutgoingStanzaMessage.of(connectionId, result.responseXml()));
                    }
                    
                    // Update resource binding state if successful
                    if (result.success()) {
                        clientJid = result.fullJid();
                        state.set(ConnectionState.RESOURCE_BOUND);
                        logger.info("Resource binding successful for connection {}, full JID: {}", connectionId, clientJid);
                    } else {
                        logger.warn("Resource binding failed for connection {}: {} - {} - {}", 
                                  connectionId, result.errorType(), result.errorCondition(), result.errorText());
                    }
                },
                error -> {
                    logger.error("Error during resource binding for connection {}: {}", connectionId, error.getMessage());
                }
            );
    }
    
    private void handleIqProcessing(String xmlData) {
        logger.debug("Handling general IQ processing for connection {}", connectionId);
        
        // Use IqProcessingService directly
        iqProcessingService.processIq(connectionId, clientJid, xmlData)
            .subscribe(
                result -> {
                    // Send IQ response back to client
                    if (result.responseXml() != null && !result.responseXml().isEmpty()) {
                        outboundSender.accept(OutgoingStanzaMessage.of(connectionId, result.responseXml()));
                        logger.debug("Sent IQ response for connection {}: {}", connectionId, result.iqType());
                    }
                },
                error -> {
                    logger.error("Error during IQ processing for connection {}: {}", connectionId, error.getMessage());
                }
            );
    }
    
    private String extractSaslMechanism(String xmlData) {
        // Simple extraction of SASL mechanism from auth element
        if (xmlData.contains("mechanism=")) {
            int start = xmlData.indexOf("mechanism=\"") + 11;
            int end = xmlData.indexOf("\"", start);
            if (end > start) {
                return xmlData.substring(start, end);
            }
        }
        return "PLAIN"; // Default mechanism
    }
    
    private String extractSaslData(String xmlData) {
        // Simple extraction of SASL data from auth element content
        int start = xmlData.indexOf(">") + 1;
        int end = xmlData.lastIndexOf("<");
        if (end > start) {
            return xmlData.substring(start, end).trim();
        }
        return ""; // Empty data
    }
    
    private String buildSaslAuthXml(String mechanism, String data) {
        // Build SASL auth XML from mechanism and data
        if (data != null && !data.isEmpty()) {
            return String.format("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='%s'>%s</auth>", 
                               mechanism, data);
        } else {
            return String.format("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='%s'/>", mechanism);
        }
    }

    private void handleConnectionClosed(ConnectionClosedMessage message) {
        logger.info("Connection {} closed: {}", connectionId, message.reason());
        state.set(ConnectionState.CLOSED);
        stop();
    }
    
    // Getters for actor state (for testing and monitoring)
    public String getConnectionId() { return connectionId; }
    public String getStreamId() { return streamId; }
    public String getClientJid() { return clientJid; }
    public boolean isTlsEstablished() { return tlsEstablished; }
    public boolean isAuthenticated() { return authenticated; }
    public String getAuthenticatedJid() { return authenticatedJid; }
    
    // Phase 2: TLS and SASL integration methods
    
    /**
     * Set the NettyOutbound for this connection (used for TLS upgrade).
     */
    public void setNettyOutbound(NettyOutbound outbound) {
        this.nettyOutbound = outbound;
    }
    
    // Helper methods for stream features after TLS and SASL
    
    private void sendStreamFeaturesAfterTls() {
        String features = featuresManager.generatePostTlsFeatures();
        outboundSender.accept(OutgoingStanzaMessage.of(connectionId, features));
    }
    
    private void sendStreamFeaturesAfterSasl() {
        String features = featuresManager.generatePostSaslFeatures();
        outboundSender.accept(OutgoingStanzaMessage.of(connectionId, features));
    }
    
    private void sendFeatures(String features) {
        logger.debug("Sending stream features to connection {}: {}", connectionId, features);
        OutgoingStanzaMessage featuresMessage = new OutgoingStanzaMessage(
            connectionId, features, java.time.Instant.now()
        );
        outboundSender.accept(featuresMessage);
    }

    /**
     * Send TLS not supported error to client
     */
    private void sendTlsNotSupported() {
        String errorMessage = "<failure xmlns='urn:ietf:params:xml:ns:xmpp-tls'>" +
                              "<policy-violation/>" +
                              "</failure>";
        
        OutgoingStanzaMessage outgoingMessage = new OutgoingStanzaMessage(
            connectionId, errorMessage, java.time.Instant.now()
        );
        outboundSender.accept(outgoingMessage);
        
        logger.info("Sent TLS not supported error to connection {}", connectionId);
    }
}