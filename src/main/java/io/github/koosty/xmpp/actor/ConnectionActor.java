package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.*;
import io.github.koosty.xmpp.stream.XmlStreamProcessor;
import io.github.koosty.xmpp.features.StreamFeaturesManager;
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
    private volatile Thread processingThread;
    private volatile boolean running = true;
    
    // Phase 2: TLS and SASL actors
    private volatile TlsNegotiationActor tlsActor;
    private volatile SaslAuthenticationActor saslActor;
    private volatile NettyOutbound nettyOutbound;
    
    // Connection-specific state
    private String streamId;
    private String clientJid;
    private String serverDomain;
    private boolean tlsEstablished = false;
    private boolean authenticated = false;
    private String authenticatedJid;
    
    public ConnectionActor(String connectionId, XmlStreamProcessor xmlProcessor, 
                          Consumer<OutgoingStanzaMessage> outboundSender, 
                          StreamFeaturesManager featuresManager) {
        this.connectionId = connectionId;
        this.xmlProcessor = xmlProcessor;
        this.outboundSender = outboundSender;
        this.featuresManager = featuresManager;
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
            case TLS_NEGOTIATION -> handleTlsNegotiation((TlsNegotiationMessage) message);
            case TLS_NEGOTIATION_SUCCESS -> handleTlsSuccess((TlsNegotiationSuccessMessage) message);
            case TLS_NEGOTIATION_FAILURE -> handleTlsFailure((TlsNegotiationFailureMessage) message);
            case SASL_AUTH -> handleSaslAuth((SaslAuthMessage) message);
            case SASL_AUTH_SUCCESS -> handleSaslSuccess((SaslAuthSuccessMessage) message);
            case SASL_AUTH_FAILURE -> handleSaslFailure((SaslAuthFailureMessage) message);
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
                // Route to TLS actor
                if (tlsActor == null) {
                    initializeTlsActor();
                }
                if (tlsActor != null) {
                    tlsActor.tell(new IncomingXmlMessage(connectionId, xmlData, message.timestamp()));
                }
                return;
            }
            
            if (xmlData.contains("xmlns='urn:ietf:params:xml:ns:xmpp-sasl'")) {
                // Route to SASL actor
                if (saslActor == null) {
                    initializeSaslActor();
                }
                if (saslActor != null) {
                    saslActor.tell(new IncomingXmlMessage(connectionId, xmlData, message.timestamp()));
                }
                return;
            }
            
            if (state.get() == ConnectionState.STREAM_INITIATED) {
                // Send initial stream features
                sendStreamFeatures();
                state.set(ConnectionState.TLS_NEGOTIATING);
            } else {
                // Process regular stanza (to be implemented in later phases)
                logger.debug("Received stanza on connection {}: {}", connectionId, xmlData);
            }
        } catch (Exception e) {
            logger.error("Error processing XML for connection {}: {}", connectionId, e.getMessage());
            sendStreamError("not-well-formed", "Invalid XML");
        }
    }
    
    private void handleStreamOpen(String streamHeader) {
        logger.info("Stream opened for connection {}", connectionId);
        
        // Generate stream ID and response header
        streamId = xmlProcessor.generateStreamId();
        serverDomain = "localhost"; // TODO: Get from configuration
        
        xmlProcessor.generateStreamHeader(serverDomain, null, streamId)
            .doOnNext(header -> outboundSender.accept(OutgoingStanzaMessage.of(connectionId, header)))
            .subscribe();
            
        state.set(ConnectionState.STREAM_INITIATED);
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
        
        if (!tlsEstablished) {
            features = featuresManager.generateInitialFeatures();
        } else if (!authenticated) {
            features = featuresManager.generatePostTlsFeatures();
        } else {
            features = featuresManager.generatePostSaslFeatures();
        }
        
        logger.debug("Sending stream features to connection {}: {}", connectionId, features);
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
        // Implementation will be expanded in later phases
    }
    
    private void handleTlsNegotiation(TlsNegotiationMessage message) {
        logger.debug("Handling TLS negotiation for connection {}", connectionId);
        // Implementation will be added in Phase 2
    }
    
    private void handleSaslAuth(SaslAuthMessage message) {
        logger.debug("Handling SASL auth for connection {}", connectionId);
        // Implementation will be added in Phase 2
    }
    
    private void handleResourceBinding(ResourceBindingMessage message) {
        logger.debug("Handling resource binding for connection {}", connectionId);
        // Implementation will be added in Phase 3
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
     * Set the NettyOutbound for this connection (used by TLS and SASL actors).
     */
    public void setNettyOutbound(NettyOutbound outbound) {
        this.nettyOutbound = outbound;
    }
    
    /**
     * Initialize TLS actor for this connection.
     */
    private void initializeTlsActor() {
        if (tlsActor == null && nettyOutbound != null) {
            tlsActor = new TlsNegotiationActor(connectionId, null); // ActorSystem will be passed
            tlsActor.start(nettyOutbound);
            logger.debug("TLS actor initialized for connection {}", connectionId);
        }
    }
    
    /**
     * Initialize SASL actor for this connection.
     */
    private void initializeSaslActor() {
        if (saslActor == null && nettyOutbound != null) {
            saslActor = new SaslAuthenticationActor(connectionId, null); // ActorSystem will be passed
            saslActor.start(nettyOutbound);
            logger.debug("SASL actor initialized for connection {}", connectionId);
        }
    }
    
    private void handleTlsSuccess(TlsNegotiationSuccessMessage message) {
        logger.info("TLS negotiation successful for connection {}", connectionId);
        tlsEstablished = true;
        state.set(ConnectionState.TLS_ESTABLISHED);
        
        // Send new stream features after TLS
        String features = featuresManager.generatePostTlsFeatures();
        sendFeatures(features);
    }
    
    private void handleTlsFailure(TlsNegotiationFailureMessage message) {
        logger.error("TLS negotiation failed for connection {}: {}", connectionId, message.errorReason());
        sendStreamError("policy-violation", "TLS required");
        state.set(ConnectionState.CLOSING);
    }
    
    private void handleSaslSuccess(SaslAuthSuccessMessage message) {
        logger.info("SASL authentication successful for connection {}: {}", connectionId, message.authenticatedJid());
        authenticated = true;
        authenticatedJid = message.authenticatedJid();
        clientJid = message.authenticatedJid();
        state.set(ConnectionState.AUTHENTICATED);
        
        // Send new stream features after SASL
        String features = featuresManager.generatePostSaslFeatures();
        sendFeatures(features);
    }
    
    private void handleSaslFailure(SaslAuthFailureMessage message) {
        logger.warn("SASL authentication failed for connection {}: {} - {}", 
                   connectionId, message.errorCondition(), message.errorText());
        sendStreamError("not-authorized", "SASL authentication failed");
        state.set(ConnectionState.CLOSING);
    }
    
    private void sendFeatures(String features) {
        OutgoingStanzaMessage featuresMessage = new OutgoingStanzaMessage(
            connectionId, features, java.time.Instant.now()
        );
        outboundSender.accept(featuresMessage);
    }
}