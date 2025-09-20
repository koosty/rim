package io.github.koosty.xmpp.error;

import io.github.koosty.xmpp.stanza.IqStanza;
import io.github.koosty.xmpp.stanza.MessageStanza;
import io.github.koosty.xmpp.stanza.PresenceStanza;
import io.github.koosty.xmpp.stanza.XmppStanza;
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
import java.util.Optional;

/**
 * RFC6120 compliant error handler for generating stream and stanza errors.
 * Supports all defined error conditions with proper XML formatting.
 */
@Component
public class XmppErrorHandler {
    
    private static final String STREAMS_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-streams";
    private static final String STANZAS_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-stanzas";
    
    private final DocumentBuilderFactory documentBuilderFactory;
    private final TransformerFactory transformerFactory;
    
    public XmppErrorHandler() {
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);
        this.transformerFactory = TransformerFactory.newInstance();
    }
    
    /**
     * Generate a stream error according to RFC6120 Section 4.9
     */
    public String generateStreamError(StreamErrorCondition condition, Optional<String> descriptiveText) {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document document = builder.newDocument();
            
            Element errorElement = document.createElementNS("http://etherx.jabber.org/streams", "stream:error");
            document.appendChild(errorElement);
            
            // Add defined condition element
            Element conditionElement = document.createElementNS(STREAMS_NAMESPACE, condition.getElementName());
            errorElement.appendChild(conditionElement);
            
            // Add optional descriptive text
            if (descriptiveText.isPresent() && !descriptiveText.get().isEmpty()) {
                Element textElement = document.createElementNS(STREAMS_NAMESPACE, "text");
                textElement.setAttribute("xml:lang", "en");
                textElement.setTextContent(descriptiveText.get());
                errorElement.appendChild(textElement);
            }
            
            return documentToString(document);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate stream error", e);
        }
    }
    
    /**
     * Generate a stanza error according to RFC6120 Section 8.3
     */
    public XmppStanza generateStanzaError(XmppStanza originalStanza, StanzaErrorCondition condition, 
                                        StanzaErrorType errorType, Optional<String> descriptiveText) {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document document = builder.newDocument();
            
            // Create error stanza with swapped to/from
            String originalTo = originalStanza.to();
            String originalFrom = originalStanza.from();
            
            // Create error element
            Element errorElement = document.createElementNS(STANZAS_NAMESPACE, "error");
            errorElement.setAttribute("type", errorType.getValue());
            
            // Add defined condition element
            Element conditionElement = document.createElementNS(STANZAS_NAMESPACE, condition.getElementName());
            errorElement.appendChild(conditionElement);
            
            // Add optional descriptive text
            if (descriptiveText.isPresent() && !descriptiveText.get().isEmpty()) {
                Element textElement = document.createElementNS(STANZAS_NAMESPACE, "text");
                textElement.setAttribute("xml:lang", "en");
                textElement.setTextContent(descriptiveText.get());
                errorElement.appendChild(textElement);
            }
            
            String errorXml = elementToString(errorElement);
            
            // Generate appropriate error stanza based on original type
            if (originalStanza instanceof MessageStanza msg) {
                return new MessageStanza(
                    originalStanza.id(),
                    originalTo, // swap addresses
                    originalFrom,
                    "error",
                    null, // no body for error
                    null, // no subject for error
                    null, // no thread for error
                    originalStanza.originalNode()
                );
            } else if (originalStanza instanceof PresenceStanza pres) {
                return new PresenceStanza(
                    originalStanza.id(),
                    originalTo, // swap addresses
                    originalFrom,
                    "error", 
                    null, // no show for error
                    null, // no status for error
                    0, // default priority for error
                    originalStanza.originalNode()
                );
            } else if (originalStanza instanceof IqStanza iq) {
                return new IqStanza(
                    originalStanza.id() != null ? originalStanza.id() : "error",
                    originalTo, // swap addresses
                    originalFrom,
                    "error", // IQ error must have type
                    null, // no query namespace for error
                    null, // error payload handled separately
                    originalStanza.originalNode()
                );
            } else {
                throw new IllegalArgumentException("Unsupported stanza type: " + originalStanza.getClass());
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate stanza error", e);
        }
    }
    
    /**
     * Generate a stream error for common conditions
     */
    public String generateBadFormatError() {
        return generateStreamError(StreamErrorCondition.BAD_FORMAT, 
            Optional.of("The entity has sent XML that cannot be processed"));
    }
    
    public String generateNotWellFormedError() {
        return generateStreamError(StreamErrorCondition.NOT_WELL_FORMED, 
            Optional.of("The entity has sent XML that is not well-formed"));
    }
    
    public String generateInvalidNamespaceError() {
        return generateStreamError(StreamErrorCondition.INVALID_NAMESPACE, 
            Optional.of("The entity has sent a namespace that is invalid"));
    }
    
    public String generateNotAuthorizedError() {
        return generateStreamError(StreamErrorCondition.NOT_AUTHORIZED, 
            Optional.of("The entity has attempted to perform an unauthorized action"));
    }
    
    public String generateConnectionTimeoutError() {
        return generateStreamError(StreamErrorCondition.CONNECTION_TIMEOUT, 
            Optional.of("The connection has timed out"));
    }
    
    /**
     * Generate common stanza errors
     */
    public XmppStanza generateBadRequestError(XmppStanza originalStanza) {
        return generateStanzaError(originalStanza, StanzaErrorCondition.BAD_REQUEST, 
            StanzaErrorType.MODIFY, Optional.of("The request contains invalid data"));
    }
    
    public XmppStanza generateFeatureNotImplementedError(XmppStanza originalStanza) {
        return generateStanzaError(originalStanza, StanzaErrorCondition.FEATURE_NOT_IMPLEMENTED, 
            StanzaErrorType.CANCEL, Optional.of("The requested feature is not implemented"));
    }
    
    public XmppStanza generateServiceUnavailableError(XmppStanza originalStanza) {
        return generateStanzaError(originalStanza, StanzaErrorCondition.SERVICE_UNAVAILABLE, 
            StanzaErrorType.CANCEL, Optional.of("The service is temporarily unavailable"));
    }
    
    public XmppStanza generateInternalServerError(XmppStanza originalStanza) {
        return generateStanzaError(originalStanza, StanzaErrorCondition.INTERNAL_SERVER_ERROR, 
            StanzaErrorType.CANCEL, Optional.of("An internal server error occurred"));
    }
    
    public XmppStanza generateJidMalformedError(XmppStanza originalStanza) {
        return generateStanzaError(originalStanza, StanzaErrorCondition.JID_MALFORMED, 
            StanzaErrorType.MODIFY, Optional.of("The JID is malformed"));
    }
    
    public XmppStanza generateItemNotFoundError(XmppStanza originalStanza) {
        return generateStanzaError(originalStanza, StanzaErrorCondition.ITEM_NOT_FOUND, 
            StanzaErrorType.CANCEL, Optional.of("The requested item was not found"));
    }
    
    private String documentToString(Document document) throws Exception {
        Transformer transformer = transformerFactory.newTransformer();
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
    
    private String elementToString(Element element) throws Exception {
        Transformer transformer = transformerFactory.newTransformer();
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(element), new StreamResult(writer));
        return writer.toString();
    }
}