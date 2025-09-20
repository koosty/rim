package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.*;
import io.github.koosty.xmpp.auth.SaslMechanismHandler;
import io.github.koosty.xmpp.auth.PlainMechanismHandler;
import io.github.koosty.xmpp.auth.ScramSha1MechanismHandler;
import io.github.koosty.xmpp.auth.ScramSha256MechanismHandler;
import reactor.netty.NettyOutbound;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor responsible for handling SASL authentication for XMPP connections.
 * Supports PLAIN, SCRAM-SHA-1, and SCRAM-SHA-256 mechanisms according to RFC6120.
 */
public class SaslAuthenticationActor {
    private static final Logger logger = LoggerFactory.getLogger(SaslAuthenticationActor.class);
    
    private enum SaslState {
        IDLE, AUTHENTICATING, SUCCESS, FAILURE
    }
    
    private final String connectionId;
    private final BlockingQueue<ActorMessage> mailbox = new LinkedBlockingQueue<>();
    private final AtomicReference<SaslState> state = new AtomicReference<>(SaslState.IDLE);
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final ActorSystem actorSystem;
    private final Map<String, SaslMechanismHandler> mechanismHandlers = new HashMap<>();
    private volatile Thread processingThread;
    private volatile NettyOutbound outbound;
    private volatile String currentMechanism;
    private volatile String authenticatedJid;
    
    public SaslAuthenticationActor(String connectionId, ActorSystem actorSystem) {
        this.connectionId = connectionId;
        this.actorSystem = actorSystem;
        initializeMechanismHandlers();
    }
    
    private void initializeMechanismHandlers() {
        mechanismHandlers.put("PLAIN", new PlainMechanismHandler());
        mechanismHandlers.put("SCRAM-SHA-1", new ScramSha1MechanismHandler());
        mechanismHandlers.put("SCRAM-SHA-256", new ScramSha256MechanismHandler());
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
        logger.debug("SaslAuthenticationActor started for connection: {}", connectionId);
    }
    
    public void stop() {
        active.set(false);
        if (processingThread != null) {
            processingThread.interrupt();
        }
        logger.debug("SaslAuthenticationActor stopped for connection: {}", connectionId);
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
                logger.error("Error processing message in SaslAuthenticationActor: {}", e.getMessage(), e);
            }
        }
    }
    
    private void handleMessage(ActorMessage message) {
        switch (message.getType()) {
            case SASL_AUTH_REQUEST -> handleAuthRequest((SaslAuthRequestMessage) message);
            case SASL_AUTH_RESPONSE -> handleAuthResponse((SaslAuthResponseMessage) message);
            case INCOMING_XML -> handleIncomingXml((IncomingXmlMessage) message);
            default -> logger.warn("Unhandled message type: {} in SaslAuthenticationActor", message.getType());
        }
    }
    
    private void handleAuthRequest(SaslAuthRequestMessage message) {
        if (state.get() != SaslState.IDLE) {
            logger.warn("SASL authentication already in progress for connection: {}", connectionId);
            sendAuthFailure("malformed-request", "Authentication already in progress");
            return;
        }
        
        String mechanism = message.mechanism();
        String authData = message.authData();
        
        if (!mechanismHandlers.containsKey(mechanism)) {
            logger.warn("Unsupported SASL mechanism: {} for connection: {}", mechanism, connectionId);
            sendAuthFailure("invalid-mechanism", "Mechanism not supported");
            return;
        }
        
        currentMechanism = mechanism;
        state.set(SaslState.AUTHENTICATING);
        
        SaslMechanismHandler handler = mechanismHandlers.get(mechanism);
        try {
            String response = handler.processInitialAuth(authData);
            
            if (handler.isComplete()) {
                handleAuthenticationComplete(handler.getAuthenticatedJid());
            } else {
                sendChallenge(response);
            }
        } catch (Exception e) {
            logger.error("Error processing SASL auth request: {}", e.getMessage(), e);
            sendAuthFailure("temporary-auth-failure", "Internal authentication error");
        }
    }
    
    private void handleAuthResponse(SaslAuthResponseMessage message) {
        if (state.get() != SaslState.AUTHENTICATING) {
            logger.warn("Received SASL response without active authentication for connection: {}", connectionId);
            sendAuthFailure("malformed-request", "No active authentication");
            return;
        }
        
        if (currentMechanism == null) {
            sendAuthFailure("malformed-request", "No active mechanism");
            return;
        }
        
        SaslMechanismHandler handler = mechanismHandlers.get(currentMechanism);
        try {
            String responseData = message.responseData();
            String challengeResponse = handler.processResponse(responseData);
            
            if (handler.isComplete()) {
                handleAuthenticationComplete(handler.getAuthenticatedJid());
            } else if (challengeResponse != null) {
                sendChallenge(challengeResponse);
            } else {
                sendAuthFailure("not-authorized", "Authentication failed");
            }
        } catch (Exception e) {
            logger.error("Error processing SASL response: {}", e.getMessage(), e);
            sendAuthFailure("temporary-auth-failure", "Internal authentication error");
        }
    }
    
    private void handleIncomingXml(IncomingXmlMessage message) {
        String xmlData = message.xmlData().trim();
        
        if (xmlData.contains("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl'")) {
            handleSaslAuth(xmlData);
        } else if (xmlData.contains("<response xmlns='urn:ietf:params:xml:ns:xmpp-sasl'")) {
            handleSaslResponse(xmlData);
        }
    }
    
    private void handleSaslAuth(String xmlData) {
        // Parse mechanism and initial data from XML
        String mechanism = extractAttribute(xmlData, "mechanism");
        String authData = extractTextContent(xmlData);
        
        if (mechanism != null) {
            SaslAuthRequestMessage authMessage = new SaslAuthRequestMessage(connectionId, mechanism, authData);
            handleAuthRequest(authMessage);
        } else {
            sendAuthFailure("malformed-request", "Missing mechanism attribute");
        }
    }
    
    private void handleSaslResponse(String xmlData) {
        String responseData = extractTextContent(xmlData);
        SaslAuthResponseMessage responseMessage = new SaslAuthResponseMessage(connectionId, responseData);
        handleAuthResponse(responseMessage);
    }
    
    private void handleAuthenticationComplete(String jid) {
        if (jid != null) {
            authenticatedJid = jid;
            state.set(SaslState.SUCCESS);
            sendAuthSuccess();
            
            // Notify connection actor of successful authentication
            SaslAuthSuccessMessage successMessage = new SaslAuthSuccessMessage(connectionId, jid);
            ConnectionActor connectionActor = actorSystem.getConnectionActor(connectionId);
            if (connectionActor != null) {
                connectionActor.tell(successMessage);
            }
            
            logger.info("SASL authentication successful for connection: {} as {}", connectionId, jid);
        } else {
            state.set(SaslState.FAILURE);
            sendAuthFailure("not-authorized", "Authentication failed");
        }
    }
    
    private void sendChallenge(String challengeData) {
        String challenge = "<challenge xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>" +
                          Base64.getEncoder().encodeToString(challengeData.getBytes()) +
                          "</challenge>";
        
        sendToClient(challenge);
    }
    
    private void sendAuthSuccess() {
        String success = "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>";
        sendToClient(success);
    }
    
    private void sendAuthFailure(String condition, String text) {
        String failure = "<failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>" +
                        "<" + condition + "/>";
        if (text != null && !text.isEmpty()) {
            failure += "<text xml:lang='en'>" + text + "</text>";
        }
        failure += "</failure>";
        
        sendToClient(failure);
        
        state.set(SaslState.FAILURE);
        
        // Notify connection actor of authentication failure
        SaslAuthFailureMessage failureMessage = new SaslAuthFailureMessage(connectionId, condition, text);
        ConnectionActor connectionActor = actorSystem.getConnectionActor(connectionId);
        if (connectionActor != null) {
            connectionActor.tell(failureMessage);
        }
    }
    
    private void sendToClient(String xmlData) {
        if (outbound != null) {
            outbound.sendString(reactor.core.publisher.Mono.just(xmlData))
                    .then()
                    .doOnError(e -> logger.error("Failed to send SASL data: {}", e.getMessage()))
                    .subscribe();
        }
    }
    
    private String extractAttribute(String xml, String attributeName) {
        String pattern = attributeName + "='([^']*)'";
        java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher matcher = regex.matcher(xml);
        return matcher.find() ? matcher.group(1) : null;
    }
    
    private String extractTextContent(String xml) {
        int start = xml.indexOf('>');
        int end = xml.lastIndexOf('<');
        
        if (start > 0 && end > start) {
            String content = xml.substring(start + 1, end).trim();
            if (!content.isEmpty()) {
                try {
                    return new String(Base64.getDecoder().decode(content));
                } catch (IllegalArgumentException e) {
                    return content;
                }
            }
        }
        return "";
    }
    
    public SaslState getCurrentState() {
        return state.get();
    }
    
    public String getAuthenticatedJid() {
        return authenticatedJid;
    }
    
    public boolean isHealthy() {
        return active.get() && processingThread != null && processingThread.isAlive();
    }
}