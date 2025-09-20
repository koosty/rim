package io.github.koosty.xmpp.session;

import io.github.koosty.xmpp.actor.SessionActor;
import io.github.koosty.xmpp.actor.message.SessionTerminatedMessage;
import io.github.koosty.xmpp.jid.JidValidator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * UserSessionManager coordinates multiple sessions per user.
 * Handles session creation, lookup, and multi-session management.
 */
@Component
public class UserSessionManager {
    
    private final JidValidator jidValidator;
    private final AtomicLong sessionCounter = new AtomicLong(0);
    
    // bareJid -> Set of SessionActors
    private final Map<String, Set<SessionActor>> userSessions = new ConcurrentHashMap<>();
    
    // sessionId -> SessionActor
    private final Map<String, SessionActor> sessionRegistry = new ConcurrentHashMap<>();
    
    // connectionId -> SessionActor (for quick lookup)
    private final Map<String, SessionActor> connectionSessions = new ConcurrentHashMap<>();
    
    public UserSessionManager(JidValidator jidValidator) {
        this.jidValidator = jidValidator;
    }
    
    /**
     * Create a new session for a user
     */
    public SessionActor createSession(String bareJid) {
        String sessionId = generateSessionId();
        
        SessionActor sessionActor = new SessionActor(sessionId, bareJid, jidValidator);
        
        // Register session
        sessionRegistry.put(sessionId, sessionActor);
        
        // Add to user sessions
        userSessions.computeIfAbsent(bareJid, k -> ConcurrentHashMap.newKeySet())
                   .add(sessionActor);
        
        // Start the session actor
        sessionActor.start();
        
        return sessionActor;
    }
    
    /**
     * Get session by ID
     */
    public SessionActor getSession(String sessionId) {
        return sessionRegistry.get(sessionId);
    }
    
    /**
     * Get session by connection ID
     */
    public SessionActor getSessionByConnection(String connectionId) {
        return connectionSessions.get(connectionId);
    }
    
    /**
     * Get all sessions for a user (bare JID)
     */
    public Set<SessionActor> getUserSessions(String bareJid) {
        Set<SessionActor> sessions = userSessions.get(bareJid);
        return sessions != null ? Set.copyOf(sessions) : Set.of();
    }
    
    /**
     * Get all active sessions for a user
     */
    public Set<SessionActor> getActiveUserSessions(String bareJid) {
        return getUserSessions(bareJid).stream()
            .filter(SessionActor::isActive)
            .collect(Collectors.toSet());
    }
    
    /**
     * Bind session to connection
     */
    public boolean bindSessionToConnection(String sessionId, String connectionId) {
        SessionActor session = sessionRegistry.get(sessionId);
        if (session == null) {
            return false;
        }
        
        connectionSessions.put(connectionId, session);
        return true;
    }
    
    /**
     * Terminate a session
     */
    public boolean terminateSession(String sessionId, String reason) {
        SessionActor session = sessionRegistry.remove(sessionId);
        if (session == null) {
            return false;
        }
        
        // Remove from user sessions
        SessionActor.SessionInfo info = session.getSessionInfo();
        Set<SessionActor> sessions = userSessions.get(info.bareJid());
        if (sessions != null) {
            sessions.remove(session);
            
            // Clean up empty session sets
            if (sessions.isEmpty()) {
                userSessions.remove(info.bareJid());
            }
        }
        
        // Remove from connection mapping
        if (info.connectionId() != null) {
            connectionSessions.remove(info.connectionId());
        }
        
        // Send termination message to session
        session.tell(new SessionTerminatedMessage(sessionId, info.fullJid(), reason));
        
        return true;
    }
    
    /**
     * Terminate all sessions for a connection
     */
    public int terminateConnectionSessions(String connectionId) {
        SessionActor session = connectionSessions.remove(connectionId);
        if (session == null) {
            return 0;
        }
        
        SessionActor.SessionInfo info = session.getSessionInfo();
        terminateSession(info.sessionId(), "Connection closed");
        
        return 1;
    }
    
    /**
     * Terminate all sessions for a user
     */
    public int terminateUserSessions(String bareJid, String reason) {
        Set<SessionActor> sessions = userSessions.remove(bareJid);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        
        // Terminate each session
        for (SessionActor session : sessions) {
            SessionActor.SessionInfo info = session.getSessionInfo();
            
            // Remove from session registry and connection mapping
            sessionRegistry.remove(info.sessionId());
            if (info.connectionId() != null) {
                connectionSessions.remove(info.connectionId());
            }
            
            // Send termination message
            session.tell(new SessionTerminatedMessage(info.sessionId(), info.fullJid(), reason));
        }
        
        return sessions.size();
    }
    
    /**
     * Get session statistics
     */
    public SessionStatistics getStatistics() {
        int totalSessions = sessionRegistry.size();
        int activeSessions = (int) sessionRegistry.values().stream()
            .mapToLong(session -> session.isActive() ? 1 : 0)
            .sum();
        int uniqueUsers = userSessions.size();
        
        return new SessionStatistics(totalSessions, activeSessions, uniqueUsers);
    }
    
    /**
     * Check if user has active sessions
     */
    public boolean hasActiveSessions(String bareJid) {
        return getUserSessions(bareJid).stream().anyMatch(SessionActor::isActive);
    }
    
    /**
     * Get resource priority for conflict resolution
     * In a full implementation, this would consider presence priority
     */
    public SessionActor getHighestPrioritySession(String bareJid) {
        return getUserSessions(bareJid).stream()
            .filter(SessionActor::isActive)
            .findFirst() // Simplified - would use actual priority logic
            .orElse(null);
    }
    
    /**
     * Clean up terminated sessions (periodic cleanup)
     */
    public int cleanupTerminatedSessions() {
        int cleanedUp = 0;
        
        // Find terminated sessions
        List<String> terminatedSessions = sessionRegistry.entrySet().stream()
            .filter(entry -> entry.getValue().getState() == SessionActor.SessionState.TERMINATED)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        // Remove terminated sessions
        for (String sessionId : terminatedSessions) {
            SessionActor session = sessionRegistry.remove(sessionId);
            if (session != null) {
                SessionActor.SessionInfo info = session.getSessionInfo();
                
                // Remove from user sessions
                Set<SessionActor> userSessionSet = userSessions.get(info.bareJid());
                if (userSessionSet != null) {
                    userSessionSet.remove(session);
                    if (userSessionSet.isEmpty()) {
                        userSessions.remove(info.bareJid());
                    }
                }
                
                // Remove from connection mapping
                if (info.connectionId() != null) {
                    connectionSessions.remove(info.connectionId());
                }
                
                cleanedUp++;
            }
        }
        
        return cleanedUp;
    }
    
    /**
     * Generate unique session ID
     */
    private String generateSessionId() {
        return "session-" + sessionCounter.incrementAndGet() + "-" + System.currentTimeMillis();
    }
    
    /**
     * Session statistics record
     */
    public record SessionStatistics(
        int totalSessions,
        int activeSessions,
        int uniqueUsers
    ) {}
}