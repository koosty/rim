package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.ActorMessage;
import io.github.koosty.xmpp.actor.message.GenericActorMessage;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Map;
import java.util.HashMap;

/**
 * Base class for Actor-like components providing sequential message processing
 */
public abstract class AbstractActor {
    
    private static final Logger logger = LoggerFactory.getLogger(AbstractActor.class);
    
    protected final String actorId;
    protected final BlockingQueue<ActorMessage> mailbox = new LinkedBlockingQueue<>();
    protected volatile Thread processingThread;
    protected volatile boolean running = true;
    
    public AbstractActor(String actorId) {
        this.actorId = actorId;
    }
    
    /**
     * Start the actor's message processing thread
     */
    public void start() {
        if (processingThread != null && processingThread.isAlive()) {
            return; // Already running
        }
        
        processingThread = new Thread(this::processMessages, "Actor-" + actorId);
        processingThread.start();
        logger.debug("Started actor: {}", actorId);
    }
    
    /**
     * Stop the actor
     */
    public void stop() {
        running = false;
        if (processingThread != null) {
            processingThread.interrupt();
        }
        logger.debug("Stopped actor: {}", actorId);
    }
    
    /**
     * Send a message to this actor
     */
    public void tell(ActorMessage message) {
        if (running) {
            mailbox.offer(message);
        } else {
            logger.warn("Attempted to send message to stopped actor: {}", actorId);
        }
    }
    
    /**
     * Check if actor is healthy
     */
    public boolean isHealthy() {
        return running && processingThread != null && processingThread.isAlive();
    }
    
    /**
     * Get actor ID
     */
    public String getActorId() {
        return actorId;
    }
    
    /**
     * Main message processing loop
     */
    private void processMessages() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                ActorMessage message = mailbox.take();
                processMessage(message)
                    .doOnError(error -> logger.error("Error processing message in actor {}: {}", 
                        actorId, error.getMessage()))
                    .subscribe();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Process a single message - to be implemented by subclasses
     */
    protected abstract Mono<ActorMessage> processMessage(ActorMessage message);
    
    /**
     * Create a response message with common fields
     */
    protected ActorMessage createResponse(String type, boolean success, String message) {
        return createResponse(type, success, message, Map.of());
    }
    
    /**
     * Create a response message with payload
     */
    protected ActorMessage createResponse(String type, boolean success, String message, Map<String, Object> payload) {
        Map<String, Object> responsePayload = new HashMap<>(payload);
        responsePayload.put("success", success);
        responsePayload.put("message", message);
        responsePayload.put("actorId", actorId);
        
        return new GenericActorMessage(type, actorId, responsePayload);
    }
}