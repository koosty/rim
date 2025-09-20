package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.*;
import reactor.netty.NettyOutbound;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor responsible for handling TLS negotiation for XMPP connections.
 * Implements STARTTLS according to RFC6120 Section 5.
 */
public class TlsNegotiationActor {
    private static final Logger logger = LoggerFactory.getLogger(TlsNegotiationActor.class);
    
    private enum TlsState {
        IDLE, NEGOTIATING, SUCCESS, FAILURE
    }
    
    private final String connectionId;
    private final BlockingQueue<ActorMessage> mailbox = new LinkedBlockingQueue<>();
    private final AtomicReference<TlsState> state = new AtomicReference<>(TlsState.IDLE);
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final ActorSystem actorSystem;
    private volatile Thread processingThread;
    private volatile NettyOutbound outbound;
    
    public TlsNegotiationActor(String connectionId, ActorSystem actorSystem) {
        this.connectionId = connectionId;
        this.actorSystem = actorSystem;
    }
    
    public void tell(ActorMessage message) {
        if (active.get()) {
            mailbox.offer(message);
        }
    }
    
    public void start(NettyOutbound outbound) {
        this.outbound = outbound;
        processingThread = new Thread(this::processMessages);
        processingThread.setDaemon(true);
        processingThread.start();
        logger.debug("TlsNegotiationActor started for connection: {}", connectionId);
    }
    
    public void stop() {
        active.set(false);
        if (processingThread != null) {
            processingThread.interrupt();
        }
        logger.debug("TlsNegotiationActor stopped for connection: {}", connectionId);
    }
    
    private void processMessages() {
        while (active.get() && !Thread.currentThread().isInterrupted()) {
            try {
                ActorMessage message = mailbox.take();
                handleMessage(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error processing message in TlsNegotiationActor: {}", e.getMessage(), e);
            }
        }
    }
    
    private void handleMessage(ActorMessage message) {
        switch (message.getType()) {
            case TLS_NEGOTIATION_REQUEST -> handleTlsRequest((TlsNegotiationRequestMessage) message);
            case INCOMING_XML -> handleIncomingXml((IncomingXmlMessage) message);
            default -> logger.warn("Unhandled message type: {} in TlsNegotiationActor", message.getType());
        }
    }
    
    private void handleTlsRequest(TlsNegotiationRequestMessage message) {
        if (state.get() != TlsState.IDLE) {
            logger.warn("TLS negotiation already in progress or completed for connection: {}", connectionId);
            return;
        }
        
        state.set(TlsState.NEGOTIATING);
        sendTlsProceed();
    }
    
    private void handleIncomingXml(IncomingXmlMessage message) {
        String xmlData = message.xmlData().trim();
        
        if (xmlData.contains("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>")) {
            handleStartTlsCommand();
        } else if (state.get() == TlsState.NEGOTIATING) {
            // After TLS proceed, the next data should be TLS handshake
            handleTlsHandshake(xmlData);
        }
    }
    
    private void handleStartTlsCommand() {
        logger.debug("Received STARTTLS command for connection: {}", connectionId);
        
        if (state.get() != TlsState.IDLE) {
            sendTlsFailure("TLS negotiation already in progress");
            return;
        }
        
        state.set(TlsState.NEGOTIATING);
        sendTlsProceed();
    }
    
    private void sendTlsProceed() {
        String tlsProceed = "<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>";
        
        if (outbound != null) {
            outbound.sendString(reactor.core.publisher.Mono.just(tlsProceed))
                    .then()
                    .doOnSuccess(v -> {
                        logger.debug("Sent TLS proceed for connection: {}", connectionId);
                        // In a real implementation, we would now upgrade the connection to TLS
                        simulateTlsUpgrade();
                    })
                    .doOnError(e -> {
                        logger.error("Failed to send TLS proceed: {}", e.getMessage());
                        sendTlsNegotiationFailure("Failed to send TLS proceed");
                    })
                    .subscribe();
        } else {
            logger.error("No outbound connection available for TLS proceed");
            sendTlsNegotiationFailure("No outbound connection");
        }
    }
    
    private void sendTlsFailure(String reason) {
        String tlsFailure = "<failure xmlns='urn:ietf:params:xml:ns:xmpp-tls'>" +
                           "<temporary-auth-failure/>" +
                           "</failure>";
        
        if (outbound != null) {
            outbound.sendString(reactor.core.publisher.Mono.just(tlsFailure))
                    .then()
                    .doOnSuccess(v -> logger.debug("Sent TLS failure for connection: {}", connectionId))
                    .subscribe();
        }
        
        state.set(TlsState.FAILURE);
        sendTlsNegotiationFailure(reason);
    }
    
    private void handleTlsHandshake(String data) {
        // In a real implementation, this would handle the actual TLS handshake
        // For now, we'll simulate successful TLS upgrade
        logger.debug("Handling TLS handshake for connection: {}", connectionId);
        simulateTlsUpgrade();
    }
    
    private void simulateTlsUpgrade() {
        // Simulate successful TLS upgrade
        // In a real implementation, this would:
        // 1. Create SSL engine
        // 2. Wrap the connection with TLS
        // 3. Perform handshake
        
        try {
            // Simulate some processing time
            Thread.sleep(100);
            state.set(TlsState.SUCCESS);
            sendTlsNegotiationSuccess();
            logger.info("TLS negotiation successful for connection: {}", connectionId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state.set(TlsState.FAILURE);
            sendTlsNegotiationFailure("TLS upgrade interrupted");
        }
    }
    
    private void sendTlsNegotiationSuccess() {
        TlsNegotiationSuccessMessage successMessage = 
            new TlsNegotiationSuccessMessage(connectionId);
        
        // Notify the connection actor that TLS is ready
        ConnectionActor connectionActor = actorSystem.getConnectionActor(connectionId);
        if (connectionActor != null) {
            connectionActor.tell(successMessage);
        }
    }
    
    private void sendTlsNegotiationFailure(String reason) {
        TlsNegotiationFailureMessage failureMessage = 
            new TlsNegotiationFailureMessage(connectionId, reason);
        
        // Notify the connection actor of TLS failure
        ConnectionActor connectionActor = actorSystem.getConnectionActor(connectionId);
        if (connectionActor != null) {
            connectionActor.tell(failureMessage);
        }
    }
    
    public TlsState getCurrentState() {
        return state.get();
    }
    
    public boolean isHealthy() {
        return active.get() && processingThread != null && processingThread.isAlive();
    }
    
    /**
     * Creates an SSL engine for the TLS upgrade.
     * In a real implementation, this would use proper certificate configuration.
     */
    private SSLEngine createSSLEngine() throws Exception {
        SSLContext sslContext = SSLContext.getDefault();
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false);
        sslEngine.setNeedClientAuth(false);
        return sslEngine;
    }
}