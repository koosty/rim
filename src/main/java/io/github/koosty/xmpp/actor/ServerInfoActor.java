package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.ActorMessage;
import io.github.koosty.xmpp.actor.message.GenericActorMessage;
import io.github.koosty.xmpp.actor.message.MessageType;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.time.Instant;
import java.util.*;

/**
 * Actor responsible for server information and service discovery (XEP-0030).
 * Handles disco#info and disco#items queries.
 */
@Component
public class ServerInfoActor extends AbstractActor {
    
    private static final Logger logger = LoggerFactory.getLogger(ServerInfoActor.class);
    
    private static final String DISCO_INFO_NAMESPACE = "http://jabber.org/protocol/disco#info";
    private static final String DISCO_ITEMS_NAMESPACE = "http://jabber.org/protocol/disco#items";
    
    private final String serverName;
    private final String serverVersion;
    private final Set<String> supportedFeatures;
    private final Map<String, String> serverIdentities;
    
    private final DocumentBuilderFactory documentBuilderFactory;
    private final TransformerFactory transformerFactory;
    
    public ServerInfoActor(
            @Value("${xmpp.server.name:localhost}") String serverName,
            @Value("${xmpp.server.version:1.0.0}") String serverVersion) {
        super("ServerInfoActor");
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.supportedFeatures = initializeSupportedFeatures();
        this.serverIdentities = initializeServerIdentities();
        
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);
        this.transformerFactory = TransformerFactory.newInstance();
        
        logger.info("ServerInfoActor initialized for server: {} version: {}", serverName, serverVersion);
    }
    
    @Override
    protected Mono<ActorMessage> processMessage(ActorMessage message) {
        return switch (message.getType()) {
            case DISCO_INFO_REQUEST -> handleDiscoInfoRequest((GenericActorMessage) message);
            case DISCO_ITEMS_REQUEST -> handleDiscoItemsRequest((GenericActorMessage) message);
            case SERVER_INFO_REQUEST -> handleServerInfoRequest((GenericActorMessage) message);
            case FEATURE_QUERY -> handleFeatureQuery((GenericActorMessage) message);
            default -> Mono.error(new IllegalArgumentException("Unknown message type: " + message.getType()));
        };
    }
    
    private Mono<ActorMessage> handleDiscoInfoRequest(GenericActorMessage message) {
        try {
            String requestId = (String) message.payload().get("requestId");
            String fromJid = (String) message.payload().get("fromJid");
            String node = (String) message.payload().get("node");
            
            String discoInfoResponse = generateDiscoInfoResponse(node);
            
            ActorMessage response = createResponse(
                MessageType.DISCO_INFO_RESPONSE.name(),
                true,
                "Disco info response generated",
                Map.of(
                    "requestId", requestId,
                    "fromJid", fromJid,
                    "discoInfo", discoInfoResponse
                )
            );
            
            logger.debug("Generated disco#info response for node: {} to: {}", node, fromJid);
            return Mono.just(response);
            
        } catch (Exception e) {
            logger.error("Failed to handle disco#info request", e);
            return Mono.just(createErrorResponse(message, "internal-server-error"));
        }
    }
    
    private Mono<ActorMessage> handleDiscoItemsRequest(GenericActorMessage message) {
        try {
            String requestId = (String) message.payload().get("requestId");
            String fromJid = (String) message.payload().get("fromJid");
            String node = (String) message.payload().get("node");
            
            String discoItemsResponse = generateDiscoItemsResponse(node);
            
            ActorMessage response = createResponse(
                MessageType.DISCO_ITEMS_RESPONSE.name(),
                true,
                "Disco items response generated",
                Map.of(
                    "requestId", requestId,
                    "fromJid", fromJid,
                    "discoItems", discoItemsResponse
                )
            );
            
            logger.debug("Generated disco#items response for node: {} to: {}", node, fromJid);
            return Mono.just(response);
            
        } catch (Exception e) {
            logger.error("Failed to handle disco#items request", e);
            return Mono.just(createErrorResponse(message, "internal-server-error"));
        }
    }
    
    private Mono<ActorMessage> handleServerInfoRequest(GenericActorMessage message) {
        try {
            Map<String, Object> serverInfo = Map.of(
                "name", serverName,
                "version", serverVersion,
                "features", new ArrayList<>(supportedFeatures),
                "identities", new HashMap<>(serverIdentities)
            );
            
            ActorMessage response = createResponse(
                MessageType.SERVER_INFO_RESPONSE.name(),
                true,
                "Server info response generated",
                Map.of("serverInfo", serverInfo)
            );
            
            logger.debug("Generated server info response");
            return Mono.just(response);
            
        } catch (Exception e) {
            logger.error("Failed to handle server info request", e);
            return Mono.just(createErrorResponse(message, "internal-server-error"));
        }
    }
    
    private Mono<ActorMessage> handleFeatureQuery(GenericActorMessage message) {
        try {
            String feature = (String) message.payload().get("feature");
            boolean supported = supportedFeatures.contains(feature);
            
            ActorMessage response = createResponse(
                MessageType.FEATURE_QUERY_RESPONSE.name(),
                true,
                "Feature query processed",
                Map.of(
                    "feature", feature,
                    "supported", supported
                )
            );
            
            logger.debug("Feature query for '{}': {}", feature, supported);
            return Mono.just(response);
            
        } catch (Exception e) {
            logger.error("Failed to handle feature query", e);
            return Mono.just(createErrorResponse(message, "internal-server-error"));
        }
    }
    
    private String generateDiscoInfoResponse(String node) throws Exception {
        DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
        Document document = builder.newDocument();
        
        Element queryElement = document.createElementNS(DISCO_INFO_NAMESPACE, "query");
        document.appendChild(queryElement);
        
        if (node != null) {
            queryElement.setAttribute("node", node);
        }
        
        // Add server identities
        for (Map.Entry<String, String> identity : serverIdentities.entrySet()) {
            Element identityElement = document.createElement("identity");
            String[] parts = identity.getKey().split("/");
            identityElement.setAttribute("category", parts[0]);
            identityElement.setAttribute("type", parts[1]);
            identityElement.setAttribute("name", identity.getValue());
            queryElement.appendChild(identityElement);
        }
        
        // Add supported features
        for (String feature : supportedFeatures) {
            Element featureElement = document.createElement("feature");
            featureElement.setAttribute("var", feature);
            queryElement.appendChild(featureElement);
        }
        
        return documentToString(document);
    }
    
    private String generateDiscoItemsResponse(String node) throws Exception {
        DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
        Document document = builder.newDocument();
        
        Element queryElement = document.createElementNS(DISCO_ITEMS_NAMESPACE, "query");
        document.appendChild(queryElement);
        
        if (node != null) {
            queryElement.setAttribute("node", node);
        }
        
        // For now, return empty items list (server has no sub-services)
        // In a real implementation, this would include MUC services, gateways, etc.
        
        return documentToString(document);
    }
    
    private Set<String> initializeSupportedFeatures() {
        Set<String> features = new HashSet<>();
        
        // Core XMPP features
        features.add("http://jabber.org/protocol/disco#info");
        features.add("http://jabber.org/protocol/disco#items");
        
        // RFC 6120 - XMPP Core
        features.add("urn:ietf:params:xml:ns:xmpp-sasl");
        features.add("urn:ietf:params:xml:ns:xmpp-bind");
        features.add("urn:ietf:params:xml:ns:xmpp-session");
        features.add("urn:ietf:params:xml:ns:xmpp-tls");
        
        // RFC 6121 - XMPP IM
        features.add("jabber:iq:roster");
        features.add("jabber:x:roster");
        
        // XEP-0199 - XMPP Ping
        features.add("urn:xmpp:ping");
        
        // XEP-0092 - Software Version
        features.add("jabber:iq:version");
        
        // XEP-0012 - Last Activity
        features.add("jabber:iq:last");
        
        // XEP-0202 - Entity Time
        features.add("urn:xmpp:time");
        
        return features;
    }
    
    private Map<String, String> initializeServerIdentities() {
        Map<String, String> identities = new HashMap<>();
        identities.put("server/im", serverName + " XMPP Server");
        return identities;
    }
    
    /**
     * Add a supported feature to the server's capability list
     */
    public void addSupportedFeature(String feature) {
        supportedFeatures.add(feature);
        logger.debug("Added supported feature: {}", feature);
    }
    
    /**
     * Remove a supported feature from the server's capability list
     */
    public void removeSupportedFeature(String feature) {
        supportedFeatures.remove(feature);
        logger.debug("Removed supported feature: {}", feature);
    }
    
    /**
     * Add a server identity
     */
    public void addServerIdentity(String category, String type, String name) {
        serverIdentities.put(category + "/" + type, name);
        logger.debug("Added server identity: {}/{} = {}", category, type, name);
    }
    
    /**
     * Get all supported features
     */
    public Set<String> getSupportedFeatures() {
        return new HashSet<>(supportedFeatures);
    }
    
    /**
     * Get server identities
     */
    public Map<String, String> getServerIdentities() {
        return new HashMap<>(serverIdentities);
    }
    
    
    private ActorMessage createErrorResponse(GenericActorMessage originalMessage, String errorCondition) {
        return createResponse(
            MessageType.ERROR_RESPONSE.name(),
            false,
            "Failed to process server info request",
            Map.of(
                "originalMessage", originalMessage,
                "errorCondition", errorCondition,
                "errorText", "Failed to process server info request"
            )
        );
    }
    
    private String documentToString(Document document) throws Exception {
        Transformer transformer = transformerFactory.newTransformer();
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}