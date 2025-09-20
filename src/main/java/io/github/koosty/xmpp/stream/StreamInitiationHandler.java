package io.github.koosty.xmpp.stream;

import io.github.koosty.xmpp.actor.message.StreamInitiationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles XMPP stream initiation and termination according to RFC6120 Section 4.
 * Processes stream opening, feature negotiation, and proper stream closure.
 */
@Component
public class StreamInitiationHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(StreamInitiationHandler.class);
    private static final String STREAM_NAMESPACE = "http://etherx.jabber.org/streams";
    private static final String CLIENT_NAMESPACE = "jabber:client";
    
    private final XMLInputFactory inputFactory;
    
    public StreamInitiationHandler() {
        this.inputFactory = XMLInputFactory.newInstance();
        this.inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        this.inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        this.inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    }
    
    /**
     * Parse stream initiation from client according to RFC6120 Section 4.2
     */
    public Mono<StreamInitiationMessage> parseStreamInitiation(String connectionId, String xmlData) {
        return Mono.fromCallable(() -> {
            try {
                XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(xmlData));
                
                if (reader.hasNext() && reader.next() == XMLStreamReader.START_ELEMENT) {
                    if (!"stream".equals(reader.getLocalName()) || 
                        !STREAM_NAMESPACE.equals(reader.getNamespaceURI())) {
                        throw new StreamInitiationException("Invalid stream element");
                    }
                    
                    Map<String, String> attributes = extractAttributes(reader);
                    
                    // Validate required attributes
                    String to = attributes.get("to");
                    String from = attributes.get("from");
                    String version = attributes.get("version");
                    
                    if (to == null) {
                        throw new StreamInitiationException("Missing 'to' attribute in stream header");
                    }
                    
                    // Version should be 1.0 for XMPP compliance
                    if (version == null || !version.startsWith("1.")) {
                        logger.warn("Client sent unsupported version: {}", version);
                    }
                    
                    // Generate stream ID for this connection
                    String streamId = generateStreamId();
                    
                    logger.info("Stream initiation from {} to {} for connection {}", from, to, connectionId);
                    
                    return StreamInitiationMessage.of(connectionId, from, to, streamId);
                }
                
                throw new StreamInitiationException("No stream element found");
                
            } catch (XMLStreamException e) {
                throw new StreamInitiationException("Failed to parse stream initiation", e);
            }
        });
    }
    
    /**
     * Validate stream header according to RFC6120 requirements
     */
    public Mono<Boolean> validateStreamHeader(String xmlData) {
        return Mono.fromCallable(() -> {
            try {
                XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(xmlData));
                
                if (reader.hasNext() && reader.next() == XMLStreamReader.START_ELEMENT) {
                    // Check element name and namespace
                    if (!"stream".equals(reader.getLocalName())) {
                        logger.debug("Invalid stream element name: {}", reader.getLocalName());
                        return false;
                    }
                    
                    if (!STREAM_NAMESPACE.equals(reader.getNamespaceURI())) {
                        logger.debug("Invalid stream namespace: {}", reader.getNamespaceURI());
                        return false;
                    }
                    
                    // Check default namespace
                    String defaultNamespace = reader.getNamespaceURI("");
                    if (!CLIENT_NAMESPACE.equals(defaultNamespace)) {
                        logger.debug("Invalid default namespace: {}", defaultNamespace);
                        return false;
                    }
                    
                    return true;
                }
                
                return false;
                
            } catch (XMLStreamException e) {
                logger.debug("XML parsing error in stream header validation: {}", e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Check if XML data represents a stream close
     */
    public boolean isStreamClose(String xmlData) {
        String trimmed = xmlData.trim();
        return "</stream:stream>".equals(trimmed) || "</stream>".equals(trimmed);
    }
    
    /**
     * Generate stream error for invalid initiation
     */
    public Mono<String> generateInitiationError(String condition, String text) {
        return Mono.fromCallable(() -> {
            StringBuilder error = new StringBuilder();
            error.append("<stream:error>");
            error.append("<").append(condition).append(" xmlns='urn:ietf:params:xml:ns:xmpp-streams'/>");
            
            if (text != null) {
                error.append("<text xmlns='urn:ietf:params:xml:ns:xmpp-streams' xml:lang='en'>")
                     .append(escapeXml(text))
                     .append("</text>");
            }
            
            error.append("</stream:error>");
            error.append("</stream:stream>");
            
            return error.toString();
        });
    }
    
    private Map<String, String> extractAttributes(XMLStreamReader reader) {
        Map<String, String> attributes = new HashMap<>();
        
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String localName = reader.getAttributeLocalName(i);
            String value = reader.getAttributeValue(i);
            attributes.put(localName, value);
        }
        
        return attributes;
    }
    
    private String generateStreamId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
    
    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }
    
    /**
     * Exception for stream initiation errors
     */
    public static class StreamInitiationException extends RuntimeException {
        public StreamInitiationException(String message) {
            super(message);
        }
        
        public StreamInitiationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}