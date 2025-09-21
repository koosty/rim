package io.github.koosty.xmpp.service.impl;

import io.github.koosty.xmpp.jid.Jid;
import io.github.koosty.xmpp.jid.JidValidator;
import io.github.koosty.xmpp.resource.ResourceManager;
import io.github.koosty.xmpp.service.ResourceBindingService;
import io.github.koosty.xmpp.stream.XmlStreamProcessor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default implementation of ResourceBindingService.
 * Handles resource binding and session establishment according to RFC6120.
 */
@Service
public class DefaultResourceBindingService implements ResourceBindingService {
    
    private final JidValidator jidValidator;
    private final ResourceManager resourceManager;
    private final XmlStreamProcessor xmlStreamProcessor;
    
    // Connection state tracking
    private final ConcurrentMap<String, String> connectionToFullJid = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> connectionResourceBound = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> connectionSessionEstablished = new ConcurrentHashMap<>();
    
    public DefaultResourceBindingService(JidValidator jidValidator, 
                                       ResourceManager resourceManager,
                                       XmlStreamProcessor xmlStreamProcessor) {
        this.jidValidator = jidValidator;
        this.resourceManager = resourceManager;
        this.xmlStreamProcessor = xmlStreamProcessor;
    }
    
    @Override
    public Mono<ResourceBindingResult> processResourceBinding(String connectionId, 
                                                             String authenticatedJid, 
                                                             String xmlData) {
        return Mono.fromCallable(() -> {
            try {
                // Parse the bare JID from authenticated user
                Optional<Jid> bareJidOpt = jidValidator.parseJid(authenticatedJid);
                if (bareJidOpt.isEmpty()) {
                    return ResourceBindingResult.error("bad-request", "bad-request", 
                        "Invalid JID format", buildBindingFailureIq("", "bad-request", "Invalid JID format"));
                }
                
                Jid bareJid = bareJidOpt.get();
                if (!bareJid.isBareJid()) {
                    // Convert to bare JID if full JID provided
                    bareJid = bareJid.toBareJid();
                }
                
                // Parse XML to extract IQ ID and requested resource
                String iqId = extractIqId(xmlData);
                String requestedResource = extractRequestedResource(xmlData);
                
                // Generate or validate requested resource
                String assignedResource = resourceManager.generateResource(
                    bareJid, 
                    requestedResource, 
                    connectionId
                );
                
                // Create full JID with assigned resource
                Optional<Jid> fullJidOpt = jidValidator.createFullJid(
                    bareJid.localpart(), 
                    bareJid.domainpart(), 
                    assignedResource
                );
                
                if (fullJidOpt.isEmpty()) {
                    return ResourceBindingResult.error("internal-server-error", "internal-server-error", 
                        "Failed to create full JID", 
                        buildBindingFailureIq(iqId, "internal-server-error", "Failed to create full JID"));
                }
                
                Jid fullJid = fullJidOpt.get();
                String fullJidString = fullJid.toString();
                
                // Update connection state
                connectionToFullJid.put(connectionId, fullJidString);
                connectionResourceBound.put(connectionId, true);
                
                // Build success response
                String responseXml = buildBindingSuccessIq(iqId, fullJidString);
                
                return ResourceBindingResult.success(fullJidString, responseXml);
                
            } catch (Exception e) {
                String iqId = extractIqId(xmlData);
                return ResourceBindingResult.error("internal-server-error", "internal-server-error", 
                    "Resource binding failed: " + e.getMessage(),
                    buildBindingFailureIq(iqId, "internal-server-error", "Resource binding failed: " + e.getMessage()));
            }
        });
    }
    
    @Override
    public Mono<SessionResult> processSessionEstablishment(String connectionId, String fullJid, String xmlData) {
        return Mono.fromCallable(() -> {
            try {
                // Check if resource is bound
                if (!isResourceBound(connectionId)) {
                    String iqId = extractIqId(xmlData);
                    return SessionResult.error("bad-request", "bad-request", 
                        "Resource must be bound before establishing session",
                        buildSessionFailureIq(iqId, "bad-request", "Resource must be bound before establishing session"));
                }
                
                String iqId = extractIqId(xmlData);
                
                // Mark session as established
                connectionSessionEstablished.put(connectionId, true);
                
                // Build success response
                String responseXml = buildSessionSuccessIq(iqId);
                
                return SessionResult.success(responseXml);
                
            } catch (Exception e) {
                String iqId = extractIqId(xmlData);
                return SessionResult.error("internal-server-error", "internal-server-error", 
                    "Session establishment failed: " + e.getMessage(),
                    buildSessionFailureIq(iqId, "internal-server-error", "Session establishment failed: " + e.getMessage()));
            }
        });
    }
    
    @Override
    public String generateResource(String connectionId, String requestedResource) {
        if (requestedResource != null && !requestedResource.trim().isEmpty()) {
            // Use requested resource if provided and valid
            String trimmedResource = requestedResource.trim();
            if (isValidResource(trimmedResource)) {
                return trimmedResource;
            }
        }
        
        // Generate unique resource
        return "resource-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    @Override
    public String getBoundJid(String connectionId) {
        return connectionToFullJid.get(connectionId);
    }
    
    @Override
    public boolean isResourceBound(String connectionId) {
        return connectionResourceBound.getOrDefault(connectionId, false);
    }
    
    @Override
    public void cleanupConnection(String connectionId) {
        connectionToFullJid.remove(connectionId);
        connectionResourceBound.remove(connectionId);
        connectionSessionEstablished.remove(connectionId);
    }
    
    /**
     * Extract IQ ID from XML data using XmlStreamProcessor.
     */
    private String extractIqId(String xmlData) {
        try {
            // Use XmlStreamProcessor to parse the XML stanza
            XmlStreamProcessor.XmlStanza stanza = xmlStreamProcessor.parseXmlStream(
                reactor.core.publisher.Flux.just(xmlData)
            ).blockFirst();
            
            if (stanza != null) {
                // Extract ID attribute from the raw XML
                return extractAttributeFromXml(stanza.rawXml(), "id");
            }
        } catch (Exception e) {
            // Fall back to simple string parsing
        }
        
        // Fallback to original string-based parsing
        return extractIdFromXmlString(xmlData);
    }
    
    /**
     * Extract requested resource from resource binding XML using XmlStreamProcessor.
     */
    private String extractRequestedResource(String xmlData) {
        try {
            // Look for <resource>...</resource> within bind element
            if (xmlData.contains("<resource>")) {
                int startIndex = xmlData.indexOf("<resource>") + 10;
                int endIndex = xmlData.indexOf("</resource>", startIndex);
                if (endIndex > startIndex) {
                    return xmlData.substring(startIndex, endIndex).trim();
                }
            }
        } catch (Exception e) {
            // No requested resource found
        }
        return null;
    }
    
    /**
     * Extract attribute value from XML string.
     */
    private String extractAttributeFromXml(String xmlData, String attributeName) {
        try {
            String searchPattern = attributeName + "='";
            if (xmlData.contains(searchPattern)) {
                int startIndex = xmlData.indexOf(searchPattern) + searchPattern.length();
                int endIndex = xmlData.indexOf("'", startIndex);
                if (endIndex > startIndex) {
                    return xmlData.substring(startIndex, endIndex);
                }
            }
            searchPattern = attributeName + "=\"";
            if (xmlData.contains(searchPattern)) {
                int startIndex = xmlData.indexOf(searchPattern) + searchPattern.length();
                int endIndex = xmlData.indexOf("\"", startIndex);
                if (endIndex > startIndex) {
                    return xmlData.substring(startIndex, endIndex);
                }
            }
        } catch (Exception e) {
            // Fall back to generated ID
        }
        return null;
    }
    
    /**
     * Fallback method for extracting ID from XML string.
     */
    private String extractIdFromXmlString(String xmlData) {
        String idValue = extractAttributeFromXml(xmlData, "id");
        if (idValue != null) {
            return idValue;
        }
        return "bind-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Validate resource string according to XMPP resource rules.
     */
    private boolean isValidResource(String resource) {
        if (resource == null || resource.isEmpty() || resource.length() > 1023) {
            return false;
        }
        
        // Basic validation - no control characters or invalid XMPP characters
        return !resource.contains("\0") && 
               !resource.contains("\r") && 
               !resource.contains("\n") &&
               !resource.contains("@") &&
               !resource.contains("/");
    }
    
    /**
     * Build successful resource binding IQ response.
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
     * Build resource binding failure IQ response.
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
     * Build successful session establishment IQ response.
     */
    private String buildSessionSuccessIq(String iqId) {
        return String.format("""
            <iq type='result' id='%s'>
                <session xmlns='urn:ietf:params:xml:ns:xmpp-session'/>
            </iq>""", iqId);
    }
    
    /**
     * Build session establishment failure IQ response.
     */
    private String buildSessionFailureIq(String iqId, String errorType, String errorMessage) {
        return String.format("""
            <iq type='error' id='%s'>
                <session xmlns='urn:ietf:params:xml:ns:xmpp-session'/>
                <error type='cancel'>
                    <%s xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>
                    <text xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'>%s</text>
                </error>
            </iq>""", iqId, errorType, errorMessage);
    }
}