package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.*;
import io.github.koosty.xmpp.jid.Jid;
import io.github.koosty.xmpp.jid.JidValidator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;
import java.util.Optional;

/**
 * SessionActor tracks individual user sessions with isolated state management.
 * Follows hybrid Reactor-Actor pattern for session lifecycle management.
 * Each session represents a bound resource for a user.
 */
public class SessionActor {
    
    private final String sessionId;
    private final String bareJid;
    private final JidValidator jidValidator;
    
    private final BlockingQueue<ActorMessage> mailbox = new LinkedBlockingQueue<>();
    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.CREATED);
    private volatile Thread processingThread;
    private volatile boolean running = true;
    
    // Session state
    private volatile String boundResourceId;
    private volatile String fullJid;
    private volatile String connectionId;
    private volatile Instant createdAt;
    private volatile Instant boundAt;
    private volatile Instant lastActivity;
    
    public SessionActor(String sessionId, String bareJid, JidValidator jidValidator) {
        this.sessionId = sessionId;
        this.bareJid = bareJid;
        this.jidValidator = jidValidator;
        this.createdAt = Instant.now();
        this.lastActivity = Instant.now();
    }
    
    /**
     * Send message to this actor
     */
    public void tell(ActorMessage message) {
        if (running) {
            mailbox.offer(message);
            updateLastActivity();
        }
    }
    
    /**
     * Start the actor's message processing loop
     */
    public void start() {
        processingThread = new Thread(this::processMessages, "SessionActor-" + sessionId);
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
                System.err.println("Error processing message in SessionActor: " + e.getMessage());
            }
        }
    }
    
    /**
     * Handle incoming actor message
     */
    private void handleMessage(ActorMessage message) {
        updateLastActivity();
        
        switch (message.getType()) {
            case SESSION_BOUND -> handleSessionBound((SessionBoundMessage) message);
            case SESSION_TERMINATED -> handleSessionTerminated((SessionTerminatedMessage) message);
            case INCOMING_XML -> handleIncomingXml((IncomingXmlMessage) message);
            case PRESENCE_UPDATE -> handlePresenceUpdate((PresenceUpdateMessage) message);
            case ACTOR_STOP -> stop();
            default -> {
                System.err.println("Unexpected message type in SessionActor: " + message.getType());
            }
        }
    }
    
    /**
     * Handle session bound to resource
     */
    private void handleSessionBound(SessionBoundMessage message) {
        if (!sessionId.equals(message.sessionId())) {
            return; // Message not for this session
        }
        
        // Validate and parse full JID
        Optional<Jid> fullJidOpt = jidValidator.parseJid(message.fullJid());
        if (fullJidOpt.isEmpty()) {
            System.err.println("Invalid full JID in session binding: " + message.fullJid());
            return;
        }
        
        Jid parsedJid = fullJidOpt.get();
        if (!parsedJid.toBareJidString().equals(bareJid)) {
            System.err.println("JID mismatch in session binding. Expected: " + bareJid + ", got: " + parsedJid.toBareJidString());
            return;
        }
        
        // Update session state
        this.fullJid = message.fullJid();
        this.connectionId = message.connectionId();
        this.boundResourceId = parsedJid.resourcepart();
        this.boundAt = Instant.now();
        
        // Transition to bound state
        if (state.compareAndSet(SessionState.CREATED, SessionState.BOUND)) {
            System.out.println("Session " + sessionId + " bound to " + fullJid);
        }
    }
    
    /**
     * Handle session termination
     */
    private void handleSessionTerminated(SessionTerminatedMessage message) {
        if (!sessionId.equals(message.sessionId())) {
            return; // Message not for this session
        }
        
        System.out.println("Session " + sessionId + " terminated: " + message.reason());
        
        // Transition to terminated state
        state.set(SessionState.TERMINATED);
        
        // Clean up and stop
        cleanup();
        stop();
    }
    
    /**
     * Handle incoming XML for this session
     */
    private void handleIncomingXml(IncomingXmlMessage message) {
        if (!connectionId.equals(message.connectionId())) {
            return; // Message not for this session's connection
        }
        
        // Process stanzas for this session
        // This would include message delivery, presence updates, etc.
        updateLastActivity();
    }
    
    /**
     * Handle presence updates for this session
     */
    private void handlePresenceUpdate(PresenceUpdateMessage message) {
        if (fullJid != null && (fullJid.equals(message.fromJid()) || fullJid.equals(message.toJid()))) {
            System.out.println("Presence update for session " + sessionId + ": " + message.show());
            updateLastActivity();
        }
    }
    
    /**
     * Update last activity timestamp
     */
    private void updateLastActivity() {
        lastActivity = Instant.now();
    }
    
    /**
     * Clean up session resources
     */
    private void cleanup() {
        // Additional cleanup logic would go here
        // For example, notifying other actors, cleaning up resources, etc.
    }
    
    /**
     * Get session information
     */
    public SessionInfo getSessionInfo() {
        return new SessionInfo(
            sessionId,
            bareJid,
            fullJid,
            boundResourceId,
            connectionId,
            state.get(),
            createdAt,
            boundAt,
            lastActivity
        );
    }
    
    /**
     * Get current session state
     */
    public SessionState getState() {
        return state.get();
    }
    
    /**
     * Check if actor is healthy
     */
    public boolean isHealthy() {
        return running && processingThread != null && processingThread.isAlive();
    }
    
    /**
     * Check if session is active (bound and not terminated)
     */
    public boolean isActive() {
        SessionState currentState = state.get();
        return currentState == SessionState.BOUND;
    }
    
    /**
     * Session states
     */
    public enum SessionState {
        CREATED,
        BOUND,
        TERMINATED
    }
    
    /**
     * Immutable session information record
     */
    public record SessionInfo(
        String sessionId,
        String bareJid,
        String fullJid,
        String resourceId,
        String connectionId,
        SessionState state,
        Instant createdAt,
        Instant boundAt,
        Instant lastActivity
    ) {}
}