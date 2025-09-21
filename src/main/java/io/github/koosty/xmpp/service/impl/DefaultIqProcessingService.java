package io.github.koosty.xmpp.service.impl;

import io.github.koosty.xmpp.service.IqProcessingService;
import io.github.koosty.xmpp.stream.XmlStreamProcessor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default implementation of IqProcessingService.
 * Handles various IQ stanzas including ping, version, disco, and roster according to XMPP RFCs and XEPs.
 * Addresses the critical gap in IQ stanza routing and processing.
 */
@Service
public class DefaultIqProcessingService implements IqProcessingService {
    
    private final XmlStreamProcessor xmlStreamProcessor;
    
    // Server information
    private static final String SERVER_NAME = "RIM XMPP Server";
    private static final String SERVER_VERSION = "1.0.0";
    
    // Connection state tracking
    private final ConcurrentMap<String, Long> connectionLastPing = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> pendingRequests = new ConcurrentHashMap<>();
    
    public DefaultIqProcessingService(XmlStreamProcessor xmlStreamProcessor) {
        this.xmlStreamProcessor = xmlStreamProcessor;
    }
    
    @Override
    public Mono<IqProcessingResult> processIq(String connectionId, String fullJid, String xmlData) {
        return Mono.fromCallable(() -> {
            try {
                // Extract IQ information
                String iqId = extractIqId(xmlData);
                IqType iqType = extractIqType(xmlData);
                IqNamespace namespace = extractNamespace(xmlData);
                
                // Route to appropriate handler based on namespace and type
                return switch (namespace) {
                    case PING -> processPingInternal(connectionId, fullJid, iqId, iqType, xmlData);
                    case VERSION -> processVersionInternal(connectionId, fullJid, iqId, iqType, xmlData);
                    case DISCO_INFO -> processDiscoInfoInternal(connectionId, fullJid, iqId, iqType, xmlData);
                    case DISCO_ITEMS -> processDiscoItemsInternal(connectionId, fullJid, iqId, iqType, xmlData);
                    case ROSTER -> processRosterInternal(connectionId, fullJid, iqId, iqType, xmlData);
                    case PRIVACY -> processPrivacyListInternal(connectionId, fullJid, iqId, iqType, xmlData);
                    case BIND, SESSION -> {
                        // These should be handled by ResourceBindingService
                        yield IqProcessingResult.error("cancel", "bad-request", 
                            "Resource binding and session establishment handled separately",
                            buildErrorIq(iqId, "bad-request", "Resource binding handled separately"), iqType, iqId);
                    }
                    case UNKNOWN -> processCustomIqInternal(connectionId, fullJid, xmlData);
                };
                
            } catch (Exception e) {
                String iqId = extractIqId(xmlData);
                IqType iqType = extractIqType(xmlData);
                return IqProcessingResult.error("cancel", "internal-server-error", 
                    "IQ processing failed: " + e.getMessage(),
                    buildErrorIq(iqId, "internal-server-error", "IQ processing failed: " + e.getMessage()), 
                    iqType, iqId);
            }
        });
    }
    
    @Override
    public Mono<IqProcessingResult> processPing(String connectionId, String fullJid, String iqId) {
        return Mono.fromCallable(() -> {
            connectionLastPing.put(connectionId, System.currentTimeMillis());
            String responseXml = buildPingResultIq(iqId);
            return IqProcessingResult.success(responseXml, IqType.RESULT, iqId);
        });
    }
    
    @Override
    public Mono<IqProcessingResult> processVersion(String connectionId, String fullJid, String iqId) {
        return Mono.fromCallable(() -> {
            String responseXml = buildVersionResultIq(iqId);
            return IqProcessingResult.success(responseXml, IqType.RESULT, iqId);
        });
    }
    
    @Override
    public Mono<IqProcessingResult> processDiscoInfo(String connectionId, String fullJid, String iqId, String targetJid) {
        return Mono.fromCallable(() -> {
            String responseXml = buildDiscoInfoResultIq(iqId);
            return IqProcessingResult.success(responseXml, IqType.RESULT, iqId);
        });
    }
    
    @Override
    public Mono<IqProcessingResult> processDiscoItems(String connectionId, String fullJid, String iqId, String targetJid) {
        return Mono.fromCallable(() -> {
            String responseXml = buildDiscoItemsResultIq(iqId);
            return IqProcessingResult.success(responseXml, IqType.RESULT, iqId);
        });
    }
    
    @Override
    public Mono<IqProcessingResult> processRoster(String connectionId, String fullJid, String xmlData) {
        return Mono.fromCallable(() -> {
            String iqId = extractIqId(xmlData);
            String responseXml = buildRosterResultIq(iqId);
            return IqProcessingResult.success(responseXml, IqType.RESULT, iqId);
        });
    }
    
    @Override
    public Mono<IqProcessingResult> processPrivacyList(String connectionId, String fullJid, String xmlData) {
        return Mono.fromCallable(() -> {
            String iqId = extractIqId(xmlData);
            // Privacy lists not implemented yet
            String responseXml = buildErrorIq(iqId, "feature-not-implemented", "Privacy lists not implemented");
            return IqProcessingResult.featureNotImplemented(iqId, responseXml);
        });
    }
    
    @Override
    public Mono<IqProcessingResult> processCustomIq(String connectionId, String fullJid, String xmlData) {
        return Mono.fromCallable(() -> {
            String iqId = extractIqId(xmlData);
            String responseXml = buildErrorIq(iqId, "feature-not-implemented", "Feature not implemented");
            return IqProcessingResult.featureNotImplemented(iqId, responseXml);
        });
    }
    
    @Override
    public void cleanupConnection(String connectionId) {
        connectionLastPing.remove(connectionId);
        // Clean up any pending requests for this connection
        pendingRequests.entrySet().removeIf(entry -> entry.getValue().equals(connectionId));
    }
    
    // Internal processing methods
    
    private IqProcessingResult processPingInternal(String connectionId, String fullJid, String iqId, 
                                                 IqType iqType, String xmlData) {
        if (iqType == IqType.GET) {
            connectionLastPing.put(connectionId, System.currentTimeMillis());
            String responseXml = buildPingResultIq(iqId);
            return IqProcessingResult.success(responseXml, IqType.RESULT, iqId);
        } else {
            String responseXml = buildErrorIq(iqId, "bad-request", "Ping requires GET request");
            return IqProcessingResult.error("modify", "bad-request", "Ping requires GET request", 
                responseXml, IqType.ERROR, iqId);
        }
    }
    
    private IqProcessingResult processVersionInternal(String connectionId, String fullJid, String iqId, 
                                                    IqType iqType, String xmlData) {
        if (iqType == IqType.GET) {
            String responseXml = buildVersionResultIq(iqId);
            return IqProcessingResult.success(responseXml, IqType.RESULT, iqId);
        } else {
            String responseXml = buildErrorIq(iqId, "bad-request", "Version requires GET request");
            return IqProcessingResult.error("modify", "bad-request", "Version requires GET request", 
                responseXml, IqType.ERROR, iqId);
        }
    }
    
    private IqProcessingResult processDiscoInfoInternal(String connectionId, String fullJid, String iqId, 
                                                      IqType iqType, String xmlData) {
        if (iqType == IqType.GET) {
            String responseXml = buildDiscoInfoResultIq(iqId);
            return IqProcessingResult.success(responseXml, IqType.RESULT, iqId);
        } else {
            String responseXml = buildErrorIq(iqId, "bad-request", "Discovery info requires GET request");
            return IqProcessingResult.error("modify", "bad-request", "Discovery info requires GET request", 
                responseXml, IqType.ERROR, iqId);
        }
    }
    
    private IqProcessingResult processDiscoItemsInternal(String connectionId, String fullJid, String iqId, 
                                                       IqType iqType, String xmlData) {
        if (iqType == IqType.GET) {
            String responseXml = buildDiscoItemsResultIq(iqId);
            return IqProcessingResult.success(responseXml, IqType.RESULT, iqId);
        } else {
            String responseXml = buildErrorIq(iqId, "bad-request", "Discovery items requires GET request");
            return IqProcessingResult.error("modify", "bad-request", "Discovery items requires GET request", 
                responseXml, IqType.ERROR, iqId);
        }
    }
    
    private IqProcessingResult processRosterInternal(String connectionId, String fullJid, String iqId, 
                                                   IqType iqType, String xmlData) {
        if (iqType == IqType.GET) {
            String responseXml = buildRosterResultIq(iqId);
            return IqProcessingResult.success(responseXml, IqType.RESULT, iqId);
        } else if (iqType == IqType.SET) {
            // Roster modifications not implemented yet
            String responseXml = buildErrorIq(iqId, "feature-not-implemented", "Roster modifications not implemented");
            return IqProcessingResult.featureNotImplemented(iqId, responseXml);
        } else {
            String responseXml = buildErrorIq(iqId, "bad-request", "Invalid roster request type");
            return IqProcessingResult.error("modify", "bad-request", "Invalid roster request type", 
                responseXml, IqType.ERROR, iqId);
        }
    }
    
    private IqProcessingResult processPrivacyListInternal(String connectionId, String fullJid, String iqId, 
                                                        IqType iqType, String xmlData) {
        // Privacy lists not implemented
        String responseXml = buildErrorIq(iqId, "feature-not-implemented", "Privacy lists not implemented");
        return IqProcessingResult.featureNotImplemented(iqId, responseXml);
    }
    
    private IqProcessingResult processCustomIqInternal(String connectionId, String fullJid, String xmlData) {
        String iqId = extractIqId(xmlData);
        String responseXml = buildErrorIq(iqId, "feature-not-implemented", "Feature not implemented");
        return IqProcessingResult.featureNotImplemented(iqId, responseXml);
    }
    
    // XML parsing and building utilities
    
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
                String idValue = extractAttributeFromXml(stanza.rawXml(), "id");
                if (idValue != null) {
                    return idValue;
                }
            }
        } catch (Exception e) {
            // Fall back to simple string parsing
        }
        
        // Fallback to original string-based parsing
        return extractIdFromXmlString(xmlData);
    }
    
    /**
     * Extract IQ type from XML data using XmlStreamProcessor.
     */
    private IqType extractIqType(String xmlData) {
        try {
            // Use XmlStreamProcessor to parse the XML stanza
            XmlStreamProcessor.XmlStanza stanza = xmlStreamProcessor.parseXmlStream(
                reactor.core.publisher.Flux.just(xmlData)
            ).blockFirst();
            
            if (stanza != null) {
                // Extract type attribute from the raw XML
                String typeValue = extractAttributeFromXml(stanza.rawXml(), "type");
                if (typeValue != null) {
                    return switch (typeValue.toLowerCase()) {
                        case "get" -> IqType.GET;
                        case "set" -> IqType.SET;
                        case "result" -> IqType.RESULT;
                        case "error" -> IqType.ERROR;
                        default -> IqType.UNKNOWN;
                    };
                }
            }
        } catch (Exception e) {
            // Fall back to simple string parsing
        }
        
        // Fallback to original string-based parsing
        return extractTypeFromXmlString(xmlData);
    }
    
    /**
     * Extract namespace from XML data using XmlStreamProcessor.
     */
    private IqNamespace extractNamespace(String xmlData) {
        try {
            // Use XmlStreamProcessor to parse the XML stanza
            XmlStreamProcessor.XmlStanza stanza = xmlStreamProcessor.parseXmlStream(
                reactor.core.publisher.Flux.just(xmlData)
            ).blockFirst();
            
            if (stanza != null && stanza.namespace() != null) {
                return IqNamespace.fromNamespace(stanza.namespace());
            }
            
            // Check for known namespaces in the raw XML
            for (IqNamespace namespace : IqNamespace.values()) {
                if (namespace != IqNamespace.UNKNOWN && xmlData.contains(namespace.getNamespace())) {
                    return namespace;
                }
            }
        } catch (Exception e) {
            // Fall back to simple string parsing
        }
        
        return IqNamespace.UNKNOWN;
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
            // Ignore parsing errors
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
        return "iq-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Fallback method for extracting type from XML string.
     */
    private IqType extractTypeFromXmlString(String xmlData) {
        try {
            if (xmlData.contains("type='get'") || xmlData.contains("type=\"get\"")) {
                return IqType.GET;
            } else if (xmlData.contains("type='set'") || xmlData.contains("type=\"set\"")) {
                return IqType.SET;
            } else if (xmlData.contains("type='result'") || xmlData.contains("type=\"result\"")) {
                return IqType.RESULT;
            } else if (xmlData.contains("type='error'") || xmlData.contains("type=\"error\"")) {
                return IqType.ERROR;
            }
        } catch (Exception e) {
            // Fall back to unknown
        }
        return IqType.UNKNOWN;
    }
    
    // Response builders
    
    private String buildPingResultIq(String iqId) {
        return String.format("""
            <iq type='result' id='%s'/>""", iqId);
    }
    
    private String buildVersionResultIq(String iqId) {
        return String.format("""
            <iq type='result' id='%s'>
                <query xmlns='jabber:iq:version'>
                    <name>%s</name>
                    <version>%s</version>
                    <os>%s</os>
                </query>
            </iq>""", iqId, SERVER_NAME, SERVER_VERSION, 
            System.getProperty("os.name") + " " + System.getProperty("os.version"));
    }
    
    private String buildDiscoInfoResultIq(String iqId) {
        return String.format("""
            <iq type='result' id='%s'>
                <query xmlns='http://jabber.org/protocol/disco#info'>
                    <identity category='server' type='im' name='%s'/>
                    <feature var='http://jabber.org/protocol/disco#info'/>
                    <feature var='http://jabber.org/protocol/disco#items'/>
                    <feature var='urn:xmpp:ping'/>
                    <feature var='jabber:iq:version'/>
                    <feature var='urn:ietf:params:xml:ns:xmpp-bind'/>
                    <feature var='urn:ietf:params:xml:ns:xmpp-session'/>
                </query>
            </iq>""", iqId, SERVER_NAME);
    }
    
    private String buildDiscoItemsResultIq(String iqId) {
        return String.format("""
            <iq type='result' id='%s'>
                <query xmlns='http://jabber.org/protocol/disco#items'/>
            </iq>""", iqId);
    }
    
    private String buildRosterResultIq(String iqId) {
        return String.format("""
            <iq type='result' id='%s'>
                <query xmlns='jabber:iq:roster'/>
            </iq>""", iqId);
    }
    
    private String buildErrorIq(String iqId, String errorCondition, String errorText) {
        return String.format("""
            <iq type='error' id='%s'>
                <error type='cancel'>
                    <%s xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>
                    <text xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'>%s</text>
                </error>
            </iq>""", iqId, errorCondition, errorText);
    }
}