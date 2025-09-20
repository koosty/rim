package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.*;
import io.github.koosty.xmpp.jid.Jid;
import io.github.koosty.xmpp.jid.JidValidator;
import io.github.koosty.xmpp.resource.ResourceManager;
import reactor.netty.NettyOutbound;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Optional;

/**
 * ResourceBindingActor handles resource binding for authenticated users.
 * Implements RFC6120 Section 7 resource binding protocol.
 * Follows hybrid Reactor-Actor pattern with isolated state management.
 */
public class ResourceBindingActor {
    
    private final String connectionId;
    private final ActorSystem actorSystem;
    private final JidValidator jidValidator;
    private final ResourceManager resourceManager;
    private final NettyOutbound outbound;
    
    private final BlockingQueue<ActorMessage> mailbox = new LinkedBlockingQueue<>();
    private final AtomicReference<ResourceBindingState> state = new AtomicReference<>(ResourceBindingState.READY);
    private volatile Thread processingThread;
    private volatile boolean running = true;
    
    public ResourceBindingActor(String connectionId, ActorSystem actorSystem, 
                               JidValidator jidValidator, ResourceManager resourceManager,
                               NettyOutbound outbound) {
        this.connectionId = connectionId;
        this.actorSystem = actorSystem;
        this.jidValidator = jidValidator;
        this.resourceManager = resourceManager;
        this.outbound = outbound;
    }
    
    /**
     * Send message to this actor
     */
    public void tell(ActorMessage message) {
        if (running) {
            mailbox.offer(message);
        }
    }
    
    /**
     * Start the actor's message processing loop
     */
    public void start() {
        processingThread = new Thread(this::processMessages, "ResourceBindingActor-" + connectionId);
        processingThread.setDaemon(true);
        processingThread.start();
    }
    
    /**
     * Stop the actor
     */
    public void stop() {
        running = false;
        if (processingThread != null) {
            processingThread.interrupt();
        }
    }
    
    /**
     * Main message processing loop
     */
    private void processMessages() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                ActorMessage message = mailbox.take();
                handleMessage(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Log error and continue processing
                System.err.println("Error processing message in ResourceBindingActor: " + e.getMessage());
            }
        }
    }
    
    /**
     * Handle incoming actor message
     */
    private void handleMessage(ActorMessage message) {
        switch (message.getType()) {
            case RESOURCE_BINDING_REQUEST -> handleResourceBindingRequest((ResourceBindingRequestMessage) message);
            case ACTOR_STOP -> stop();
            default -> {
                // Unexpected message type
                System.err.println("Unexpected message type in ResourceBindingActor: " + message.getType());
            }
        }
    }
    
    /**
     * Handle resource binding request according to RFC6120 Section 7
     */
    private void handleResourceBindingRequest(ResourceBindingRequestMessage request) {
        try {
            // Parse the bare JID from authenticated user
            Optional<Jid> bareJidOpt = jidValidator.parseJid(request.userJid());
            if (bareJidOpt.isEmpty()) {
                sendBindingFailure(request.iqId(), "bad-request", "Invalid JID format");
                return;
            }
            
            Jid bareJid = bareJidOpt.get();
            if (!bareJid.isBareJid()) {
                // Convert to bare JID if full JID provided
                bareJid = bareJid.toBareJid();
            }
            
            // Generate or validate requested resource
            String assignedResource = resourceManager.generateResource(
                bareJid, 
                request.requestedResource(), 
                connectionId
            );
            
            // Create full JID with assigned resource
            Optional<Jid> fullJidOpt = jidValidator.createFullJid(
                bareJid.localpart(), 
                bareJid.domainpart(), 
                assignedResource
            );
            
            if (fullJidOpt.isEmpty()) {
                sendBindingFailure(request.iqId(), "internal-server-error", "Failed to create full JID");
                return;
            }
            
            Jid fullJid = fullJidOpt.get();
            
            // Send successful binding response
            sendBindingSuccess(request.iqId(), fullJid.toString());
            
            // Update state and notify other actors
            state.set(ResourceBindingState.BOUND);
            
            // Notify session creation
            actorSystem.tellConnectionActor(connectionId, new SessionBoundMessage(
                connectionId + "-session",
                fullJid.toString(),
                connectionId
            ));
            
        } catch (Exception e) {
            sendBindingFailure(request.iqId(), "internal-server-error", 
                "Resource binding failed: " + e.getMessage());
        }
    }
    
    /**
     * Send resource binding success response
     */
    private void sendBindingSuccess(String iqId, String boundJid) {
        String response = buildBindingSuccessIq(iqId, boundJid);
        sendResponse(response);
        
        // Notify connection actor of successful binding
        ConnectionActor actor = actorSystem.getConnectionActor(connectionId);
        if (actor != null) {
            actor.tell(new ResourceBindingSuccessMessage(connectionId, boundJid, iqId));
        }
    }
    
    /**
     * Send resource binding failure response
     */
    private void sendBindingFailure(String iqId, String errorType, String errorMessage) {
        String response = buildBindingFailureIq(iqId, errorType, errorMessage);
        sendResponse(response);
        
        // Notify connection actor of binding failure
        ConnectionActor actor = actorSystem.getConnectionActor(connectionId);
        if (actor != null) {
            actor.tell(new ResourceBindingFailureMessage(connectionId, errorType, errorMessage, iqId));
        }
    }
    
    /**
     * Send XML response through connection
     */
    private void sendResponse(String xmlResponse) {
        try {
            outbound.sendString(reactor.core.publisher.Mono.just(xmlResponse))
                .then()
                .subscribe();
        } catch (Exception e) {
            System.err.println("Failed to send resource binding response: " + e.getMessage());
        }
    }
    
    /**
     * Build successful resource binding IQ response
     */
    private String buildBindingSuccessIq(String iqId, String boundJid) {
        return String.format("""
            <iq type='result' id='%s'>
                <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>
                    <jid>%s</jid>
                </bind>
            </iq>""", iqId, boundJid);
    }
    
    /**
     * Build resource binding failure IQ response
     */
    private String buildBindingFailureIq(String iqId, String errorType, String errorMessage) {
        return String.format("""
            <iq type='error' id='%s'>
                <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>
                <error type='cancel'>
                    <%s xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>
                    <text xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'>%s</text>
                </error>
            </iq>""", iqId, errorType, errorMessage);
    }
    
    /**
     * Get current binding state
     */
    public ResourceBindingState getState() {
        return state.get();
    }
    
    /**
     * Check if actor is healthy
     */
    public boolean isHealthy() {
        return running && processingThread != null && processingThread.isAlive();
    }
    
    /**
     * Resource binding states
     */
    public enum ResourceBindingState {
        READY,
        PROCESSING,
        BOUND,
        FAILED
    }
}