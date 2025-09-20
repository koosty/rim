package io.github.koosty.xmpp.stanza;

import io.github.koosty.xmpp.jid.Jid;
import io.github.koosty.xmpp.jid.JidValidator;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * StanzaProcessor handles parsing and validation of XMPP stanzas.
 * Supports message, presence, and IQ stanzas according to RFC6120.
 */
@Component
public class StanzaProcessor {
    
    private final JidValidator jidValidator;
    private final DocumentBuilderFactory documentBuilderFactory;
    
    public StanzaProcessor(JidValidator jidValidator) {
        this.jidValidator = jidValidator;
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);
    }
    
    /**
     * Parse XML string into XmppStanza object
     */
    public Optional<XmppStanza> parseStanza(String xmlData) {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlData.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();
            
            return parseStanzaElement(root);
            
        } catch (ParserConfigurationException | SAXException | IOException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Parse DOM element into XmppStanza
     */
    private Optional<XmppStanza> parseStanzaElement(Element element) {
        String tagName = element.getLocalName();
        String namespace = element.getNamespaceURI();
        
        // Only process XMPP content namespace stanzas
        if (!"jabber:client".equals(namespace) && !"jabber:server".equals(namespace)) {
            return Optional.empty();
        }
        
        return switch (tagName) {
            case "message" -> parseMessageStanza(element);
            case "presence" -> parsePresenceStanza(element);
            case "iq" -> parseIqStanza(element);
            default -> Optional.empty();
        };
    }
    
    /**
     * Parse message stanza
     */
    private Optional<XmppStanza> parseMessageStanza(Element element) {
        String id = element.getAttribute("id");
        String from = element.getAttribute("from");
        String to = element.getAttribute("to");
        String type = element.getAttribute("type");
        
        // Default type is 'normal' if not specified
        if (type.isEmpty()) {
            type = "normal";
        }
        
        // Validate JIDs if present
        if (!isValidJidAttribute(from) || !isValidJidAttribute(to)) {
            return Optional.empty();
        }
        
        // Extract message body and subject
        String body = extractElementText(element, "body");
        String subject = extractElementText(element, "subject");
        String thread = extractElementText(element, "thread");
        
        return Optional.of(new MessageStanza(
            id.isEmpty() ? generateId() : id,
            from.isEmpty() ? null : from,
            to.isEmpty() ? null : to,
            type,
            body,
            subject,
            thread,
            element.cloneNode(true)
        ));
    }
    
    /**
     * Parse presence stanza
     */
    private Optional<XmppStanza> parsePresenceStanza(Element element) {
        String id = element.getAttribute("id");
        String from = element.getAttribute("from");
        String to = element.getAttribute("to");
        String type = element.getAttribute("type");
        
        // Default type is 'available' if not specified
        if (type.isEmpty()) {
            type = "available";
        }
        
        // Validate JIDs if present
        if (!isValidJidAttribute(from) || !isValidJidAttribute(to)) {
            return Optional.empty();
        }
        
        // Extract presence information
        String show = extractElementText(element, "show");
        String status = extractElementText(element, "status");
        String priorityStr = extractElementText(element, "priority");
        
        int priority = 0;
        if (!priorityStr.isEmpty()) {
            try {
                priority = Integer.parseInt(priorityStr);
                // Priority must be between -128 and 127
                if (priority < -128 || priority > 127) {
                    priority = 0;
                }
            } catch (NumberFormatException e) {
                priority = 0;
            }
        }
        
        return Optional.of(new PresenceStanza(
            id.isEmpty() ? generateId() : id,
            from.isEmpty() ? null : from,
            to.isEmpty() ? null : to,
            type,
            show.isEmpty() ? null : show,
            status.isEmpty() ? null : status,
            priority,
            element.cloneNode(true)
        ));
    }
    
    /**
     * Parse IQ stanza
     */
    private Optional<XmppStanza> parseIqStanza(Element element) {
        String id = element.getAttribute("id");
        String from = element.getAttribute("from");
        String to = element.getAttribute("to");
        String type = element.getAttribute("type");
        
        // IQ must have type and id
        if (type.isEmpty() || id.isEmpty()) {
            return Optional.empty();
        }
        
        // Validate IQ type
        if (!isValidIqType(type)) {
            return Optional.empty();
        }
        
        // Validate JIDs if present
        if (!isValidJidAttribute(from) || !isValidJidAttribute(to)) {
            return Optional.empty();
        }
        
        // Extract IQ payload
        Element payloadElement = null;
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                payloadElement = (Element) child;
                break;
            }
        }
        
        return Optional.of(new IqStanza(
            id,
            from.isEmpty() ? null : from,
            to.isEmpty() ? null : to,
            type,
            payloadElement != null ? payloadElement.getNamespaceURI() : null,
            payloadElement,
            element.cloneNode(true)
        ));
    }
    
    /**
     * Validate JID attribute if not empty
     */
    private boolean isValidJidAttribute(String jidStr) {
        if (jidStr == null || jidStr.isEmpty()) {
            return true; // Empty JID is valid (will be filled by server)
        }
        return jidValidator.parseJid(jidStr).isPresent();
    }
    
    /**
     * Check if IQ type is valid
     */
    private boolean isValidIqType(String type) {
        return "get".equals(type) || "set".equals(type) || "result".equals(type) || "error".equals(type);
    }
    
    /**
     * Extract text content from child element
     */
    private String extractElementText(Element parent, String tagName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && 
                tagName.equals(child.getLocalName())) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }
    
    /**
     * Generate unique stanza ID
     */
    private String generateId() {
        return "stanza-" + UUID.randomUUID().toString();
    }
    
    /**
     * Validate message stanza according to RFC6120
     */
    public boolean isValidMessageStanza(MessageStanza stanza) {
        // Message type validation
        String type = stanza.type();
        return "normal".equals(type) || "chat".equals(type) || 
               "groupchat".equals(type) || "headline".equals(type) || "error".equals(type);
    }
    
    /**
     * Validate presence stanza according to RFC6120
     */
    public boolean isValidPresenceStanza(PresenceStanza stanza) {
        // Presence type validation
        String type = stanza.type();
        return "available".equals(type) || "unavailable".equals(type) || 
               "subscribe".equals(type) || "subscribed".equals(type) ||
               "unsubscribe".equals(type) || "unsubscribed".equals(type) ||
               "probe".equals(type) || "error".equals(type);
    }
    
    /**
     * Validate IQ stanza according to RFC6120
     */
    public boolean isValidIqStanza(IqStanza stanza) {
        // IQ must have ID and valid type
        if (stanza.id() == null || stanza.id().isEmpty()) {
            return false;
        }
        
        String type = stanza.type();
        if (!isValidIqType(type)) {
            return false;
        }
        
        // Get/Set IQs must have payload, Result/Error may or may not
        if (("get".equals(type) || "set".equals(type)) && stanza.queryPayload() == null) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Create error response for a stanza
     */
    public XmppStanza createErrorResponse(XmppStanza originalStanza, String errorType, String errorCondition, String errorText) {
        String id = originalStanza.id();
        String from = originalStanza.to(); // Swap from/to
        String to = originalStanza.from();
        
        // Create error element
        String errorXml = String.format("""
            <error type='%s'>
                <%s xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>
                %s
            </error>""", 
            errorType, 
            errorCondition,
            errorText != null ? String.format("<text xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'>%s</text>", errorText) : ""
        );
        
        if (originalStanza instanceof MessageStanza) {
            return new MessageStanza(id, from, to, "error", null, null, null, null);
        } else if (originalStanza instanceof PresenceStanza) {
            return new PresenceStanza(id, from, to, "error", null, null, 0, null);
        } else if (originalStanza instanceof IqStanza) {
            return new IqStanza(id, from, to, "error", null, null, null);
        }
        
        throw new IllegalArgumentException("Unknown stanza type");
    }
}