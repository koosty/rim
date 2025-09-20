package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.ActorMessage;
import io.github.koosty.xmpp.actor.message.GenericActorMessage;
import io.github.koosty.xmpp.stanza.IqStanza;
import io.github.koosty.xmpp.connection.XmppConnection;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Actor responsible for processing IQ (Info/Query) requests and responses.
 * Handles common IQ types like ping, version, disco info/items per RFC6120.
 */
public class IqProcessingActor extends AbstractActor {
    
    private static final Logger logger = LoggerFactory.getLogger(IqProcessingActor.class);
    
    private final Map<String, XmppConnection> connectionsByJid = new ConcurrentHashMap<>();
    private final Map<String, IqStanza> pendingRequests = new ConcurrentHashMap<>();
    private final DocumentBuilderFactory documentBuilderFactory;
    
    // Server information
    private final String serverName;
    private final String serverVersion;
    
    public IqProcessingActor(String actorId, String serverName, String serverVersion) {
        super(actorId);
        this.serverName = serverName != null ? serverName : "XMPP Server";
        this.serverVersion = serverVersion != null ? serverVersion : "1.0.0";
        
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);
    }
    
    @Override
    protected Mono<ActorMessage> processMessage(ActorMessage message) {
        return switch (message.getType()) {
            case ROUTE_MESSAGE -> handleIqStanza((GenericActorMessage) message);
            case REGISTER_CONNECTION -> handleRegisterConnection(message);
            case UNREGISTER_CONNECTION -> handleUnregisterConnection(message);
            case GET_CONNECTION_COUNT -> handleGetIqStats();
            default -> Mono.error(new IllegalArgumentException("Unknown message type: " + message.getType()));
        };
    }
    
    /**
     * Handle IQ stanza processing
     */
    private Mono<ActorMessage> handleIqStanza(GenericActorMessage message) {
        IqStanza stanza = message.getPayload("stanza", IqStanza.class);
        String sourceJid = message.getPayload("sourceJid", String.class);
        
        if (stanza == null) {
            return Mono.just(createResponse("iq-processing-response", false, "No IQ stanza provided"));
        }
        
        return processIqStanza(stanza, sourceJid)
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(Duration.ofSeconds(10))
            .onErrorResume(error -> {
                logger.error("IQ processing error: {}", error.getMessage());
                return Mono.just(createResponse("iq-processing-response", false, error.getMessage()));
            });
    }
    
    /**
     * Process individual IQ stanza
     */
    private Mono<ActorMessage> processIqStanza(IqStanza stanza, String sourceJid) {
        return Mono.fromCallable(() -> {
            if (stanza.isRequest()) {
                return handleIqRequest(stanza, sourceJid);
            } else if (stanza.isResponse()) {
                return handleIqResponse(stanza, sourceJid);
            } else {
                logger.warn("Unknown IQ type: {} from {}", stanza.type(), sourceJid);
                return createResponse("iq-processing-response", false, "Unknown IQ type");
            }
        })
        .flatMap(result -> Mono.just(result));
    }
    
    /**
     * Handle IQ request (get/set)
     */
    private ActorMessage handleIqRequest(IqStanza stanza, String sourceJid) {
        try {
            IqStanza response = switch (stanza.queryNamespace()) {
                case "urn:xmpp:ping" -> handlePingRequest(stanza);
                case "jabber:iq:version" -> handleVersionRequest(stanza);
                case "http://jabber.org/protocol/disco#info" -> handleDiscoInfoRequest(stanza);
                case "http://jabber.org/protocol/disco#items" -> handleDiscoItemsRequest(stanza);
                case "jabber:iq:roster" -> handleRosterRequest(stanza);
                case "urn:ietf:params:xml:ns:xmpp-bind" -> handleResourceBindRequest(stanza);
                case "urn:ietf:params:xml:ns:xmpp-session" -> handleSessionRequest(stanza);
                default -> handleUnknownRequest(stanza);
            };
            
            // Send response back to requesting connection
            if (response != null && sourceJid != null) {
                XmppConnection connection = connectionsByJid.get(sourceJid);
                if (connection != null) {
                    connection.sendStanza(response).subscribe(
                        sent -> logger.debug("IQ response sent to {}: {}", sourceJid, sent),
                        error -> logger.error("Failed to send IQ response to {}: {}", sourceJid, error.getMessage())
                    );
                } else {
                    logger.warn("No connection found for IQ response to {}", sourceJid);
                }
            }
            
            return createResponse("iq-processing-response", true, "IQ request processed");
            
        } catch (Exception e) {
            logger.error("Error processing IQ request: {}", e.getMessage());
            
            // Send error response
            if (sourceJid != null) {
                IqStanza errorResponse = stanza.createError("cancel", "internal-server-error");
                XmppConnection connection = connectionsByJid.get(sourceJid);
                if (connection != null) {
                    connection.sendStanza(errorResponse).subscribe();
                }
            }
            
            return createResponse("iq-processing-response", false, "IQ processing error: " + e.getMessage());
        }
    }
    
    /**
     * Handle IQ response (result/error)
     */
    private ActorMessage handleIqResponse(IqStanza stanza, String sourceJid) {
        String requestId = stanza.id();
        IqStanza pendingRequest = pendingRequests.remove(requestId);
        
        if (pendingRequest == null) {
            logger.warn("Received IQ response for unknown request ID: {} from {}", requestId, sourceJid);
            return createResponse("iq-processing-response", false, "Unknown request ID");
        }
        
        if (stanza.isResult()) {
            logger.debug("Received IQ result for request {}", requestId);
        } else if (stanza.isError()) {
            logger.warn("Received IQ error for request {}: {}", requestId, stanza.type());
        }
        
        return createResponse("iq-processing-response", true, "IQ response processed");
    }
    
    /**
     * Handle ping request (XEP-0199)
     */
    private IqStanza handlePingRequest(IqStanza stanza) {
        logger.debug("Handling ping request from {}", stanza.from());
        return stanza.createResult();
    }
    
    /**
     * Handle version request (XEP-0092)
     */
    private IqStanza handleVersionRequest(IqStanza stanza) {
        logger.debug("Handling version request from {}", stanza.from());
        
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.newDocument();
            
            Element query = doc.createElementNS("jabber:iq:version", "query");
            
            Element name = doc.createElement("name");
            name.setTextContent(serverName);
            query.appendChild(name);
            
            Element version = doc.createElement("version");
            version.setTextContent(serverVersion);
            query.appendChild(version);
            
            Element os = doc.createElement("os");
            os.setTextContent(System.getProperty("os.name") + " " + System.getProperty("os.version"));
            query.appendChild(os);
            
            return stanza.createResult(query);
            
        } catch (Exception e) {
            logger.error("Error creating version response: {}", e.getMessage());
            return stanza.createError("cancel", "internal-server-error");
        }
    }
    
    /**
     * Handle service discovery info request (XEP-0030)
     */
    private IqStanza handleDiscoInfoRequest(IqStanza stanza) {
        logger.debug("Handling disco#info request from {}", stanza.from());
        
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.newDocument();
            
            Element query = doc.createElementNS("http://jabber.org/protocol/disco#info", "query");
            
            // Server identity
            Element identity = doc.createElement("identity");
            identity.setAttribute("category", "server");
            identity.setAttribute("type", "im");
            identity.setAttribute("name", serverName);
            query.appendChild(identity);
            
            // Supported features
            String[] features = {
                "http://jabber.org/protocol/disco#info",
                "http://jabber.org/protocol/disco#items",
                "urn:xmpp:ping",
                "jabber:iq:version",
                "urn:ietf:params:xml:ns:xmpp-bind",
                "urn:ietf:params:xml:ns:xmpp-session"
            };
            
            for (String feature : features) {
                Element featureElement = doc.createElement("feature");
                featureElement.setAttribute("var", feature);
                query.appendChild(featureElement);
            }
            
            return stanza.createResult(query);
            
        } catch (Exception e) {
            logger.error("Error creating disco#info response: {}", e.getMessage());
            return stanza.createError("cancel", "internal-server-error");
        }
    }
    
    /**
     * Handle service discovery items request (XEP-0030)
     */
    private IqStanza handleDiscoItemsRequest(IqStanza stanza) {
        logger.debug("Handling disco#items request from {}", stanza.from());
        
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.newDocument();
            
            Element query = doc.createElementNS("http://jabber.org/protocol/disco#items", "query");
            
            // For simplicity, return empty items list
            // In a full implementation, this would list available services
            
            return stanza.createResult(query);
            
        } catch (Exception e) {
            logger.error("Error creating disco#items response: {}", e.getMessage());
            return stanza.createError("cancel", "internal-server-error");
        }
    }
    
    /**
     * Handle roster request (RFC 6121)
     */
    private IqStanza handleRosterRequest(IqStanza stanza) {
        logger.debug("Handling roster request from {}", stanza.from());
        
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.newDocument();
            
            Element query = doc.createElementNS("jabber:iq:roster", "query");
            
            // For simplicity, return empty roster
            // In a full implementation, this would fetch user's roster from database
            
            return stanza.createResult(query);
            
        } catch (Exception e) {
            logger.error("Error creating roster response: {}", e.getMessage());
            return stanza.createError("cancel", "internal-server-error");
        }
    }
    
    /**
     * Handle resource binding request (RFC 6120 Section 7)
     */
    private IqStanza handleResourceBindRequest(IqStanza stanza) {
        logger.debug("Handling resource bind request from {}", stanza.from());
        
        // This should be handled by ResourceBindingActor, but we provide a fallback
        return stanza.createError("cancel", "feature-not-implemented");
    }
    
    /**
     * Handle session establishment request (RFC 3921 - deprecated in RFC 6121)
     */
    private IqStanza handleSessionRequest(IqStanza stanza) {
        logger.debug("Handling session request from {}", stanza.from());
        
        // Sessions are optional in XMPP 1.0, just return success
        return stanza.createResult();
    }
    
    /**
     * Handle unknown IQ request
     */
    private IqStanza handleUnknownRequest(IqStanza stanza) {
        logger.warn("Unknown IQ request namespace: {} from {}", stanza.queryNamespace(), stanza.from());
        return stanza.createError("cancel", "service-unavailable");
    }
    
    /**
     * Register connection for IQ responses
     */
    private Mono<ActorMessage> handleRegisterConnection(ActorMessage message) {
        GenericActorMessage genericMessage = (GenericActorMessage) message;
        String jid = genericMessage.getPayload("jid", String.class);
        XmppConnection connection = genericMessage.getPayload("connection", XmppConnection.class);
        
        if (jid == null || connection == null) {
            return Mono.just(createResponse("register-connection-response", false, "Invalid parameters"));
        }
        
        connectionsByJid.put(jid, connection);
        logger.info("Registered connection for IQ processing: {}", jid);
        return Mono.just(createResponse("register-connection-response", true, "Connection registered"));
    }
    
    /**
     * Unregister connection from IQ processing
     */
    private Mono<ActorMessage> handleUnregisterConnection(ActorMessage message) {
        GenericActorMessage genericMessage = (GenericActorMessage) message;
        String jid = genericMessage.getPayload("jid", String.class);
        
        if (jid == null) {
            return Mono.just(createResponse("unregister-connection-response", false, "No JID specified"));
        }
        
        connectionsByJid.remove(jid);
        logger.info("Unregistered connection from IQ processing: {}", jid);
        return Mono.just(createResponse("unregister-connection-response", true, "Connection unregistered"));
    }
    
    /**
     * Get IQ processing statistics
     */
    private Mono<ActorMessage> handleGetIqStats() {
        Map<String, Object> stats = Map.of(
            "activeConnections", connectionsByJid.size(),
            "pendingRequests", pendingRequests.size()
        );
        
        return Mono.just(createResponse("iq-stats-response", true, 
            "IQ statistics retrieved", stats));
    }
    
    /**
     * Send IQ request to another entity
     */
    public void sendIqRequest(IqStanza request, String targetJid) {
        if (request.id() != null) {
            pendingRequests.put(request.id(), request);
        }
        
        XmppConnection connection = connectionsByJid.get(targetJid);
        if (connection != null) {
            connection.sendStanza(request).subscribe(
                sent -> logger.debug("IQ request sent to {}: {}", targetJid, sent),
                error -> {
                    logger.error("Failed to send IQ request to {}: {}", targetJid, error.getMessage());
                    if (request.id() != null) {
                        pendingRequests.remove(request.id());
                    }
                }
            );
        } else {
            logger.warn("No connection found for IQ request to {}", targetJid);
            if (request.id() != null) {
                pendingRequests.remove(request.id());
            }
        }
    }
}