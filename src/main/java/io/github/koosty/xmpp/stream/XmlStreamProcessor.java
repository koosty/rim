package io.github.koosty.xmpp.stream;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Handles XML stream processing for XMPP according to RFC6120.
 * Processes incremental XML parsing and generation with proper UTF-8 encoding.
 */
@Component
public class XmlStreamProcessor {
    
    private final XMLInputFactory inputFactory;
    private final XMLOutputFactory outputFactory;
    
    public XmlStreamProcessor() {
        this.inputFactory = XMLInputFactory.newInstance();
        this.inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        this.inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        this.inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        
        this.outputFactory = XMLOutputFactory.newInstance();
    }
    
    /**
     * Generate XML stream header according to RFC6120 Section 4.2
     */
    public Mono<String> generateStreamHeader(String from, String to, String id) {
        return Mono.fromCallable(() -> {
            StringWriter writer = new StringWriter();
            XMLStreamWriter xmlWriter = outputFactory.createXMLStreamWriter(writer);
            
            xmlWriter.writeStartElement("stream", "stream", "http://etherx.jabber.org/streams");
            xmlWriter.writeNamespace("stream", "http://etherx.jabber.org/streams");
            xmlWriter.writeDefaultNamespace("jabber:client");
            
            if (from != null) {
                xmlWriter.writeAttribute("from", from);
            }
            if (to != null) {
                xmlWriter.writeAttribute("to", to);
            }
            if (id != null) {
                xmlWriter.writeAttribute("id", id);
            }
            
            xmlWriter.writeAttribute("version", "1.0");
            xmlWriter.writeAttribute("xml:lang", "en");
            
            xmlWriter.flush();
            return writer.toString();
        });
    }
    
    /**
     * Generate stream features according to RFC6120 Section 4.3.2
     */
    public Mono<String> generateStreamFeatures(boolean tlsRequired, boolean saslAvailable) {
        return Mono.fromCallable(() -> {
            StringWriter writer = new StringWriter();
            XMLStreamWriter xmlWriter = outputFactory.createXMLStreamWriter(writer);
            
            xmlWriter.writeStartElement("stream:features");
            
            if (tlsRequired) {
                xmlWriter.writeStartElement("starttls");
                xmlWriter.writeNamespace("", "urn:ietf:params:xml:ns:xmpp-tls");
                xmlWriter.writeEmptyElement("required");
                xmlWriter.writeEndElement(); // starttls
            }
            
            if (saslAvailable) {
                xmlWriter.writeStartElement("mechanisms");
                xmlWriter.writeNamespace("", "urn:ietf:params:xml:ns:xmpp-sasl");
                
                // Add SASL mechanisms
                xmlWriter.writeStartElement("mechanism");
                xmlWriter.writeCharacters("PLAIN");
                xmlWriter.writeEndElement();
                
                xmlWriter.writeStartElement("mechanism");
                xmlWriter.writeCharacters("SCRAM-SHA-1");
                xmlWriter.writeEndElement();
                
                xmlWriter.writeStartElement("mechanism");
                xmlWriter.writeCharacters("SCRAM-SHA-256");
                xmlWriter.writeEndElement();
                
                xmlWriter.writeEndElement(); // mechanisms
            }
            
            xmlWriter.writeEndElement(); // stream:features
            xmlWriter.flush();
            
            return writer.toString();
        });
    }
    
    /**
     * Generate stream close according to RFC6120 Section 4.4
     */
    public Mono<String> generateStreamClose() {
        return Mono.just("</stream:stream>");
    }
    
    /**
     * Generate stream error according to RFC6120 Section 4.9
     */
    public Mono<String> generateStreamError(String condition, String text) {
        return Mono.fromCallable(() -> {
            StringWriter writer = new StringWriter();
            XMLStreamWriter xmlWriter = outputFactory.createXMLStreamWriter(writer);
            
            xmlWriter.writeStartElement("stream:error");
            xmlWriter.writeStartElement(condition);
            xmlWriter.writeNamespace("", "urn:ietf:params:xml:ns:xmpp-streams");
            xmlWriter.writeEndElement();
            
            if (text != null) {
                xmlWriter.writeStartElement("text");
                xmlWriter.writeNamespace("", "urn:ietf:params:xml:ns:xmpp-streams");
                xmlWriter.writeAttribute("xml:lang", "en");
                xmlWriter.writeCharacters(text);
                xmlWriter.writeEndElement();
            }
            
            xmlWriter.writeEndElement(); // stream:error
            xmlWriter.flush();
            
            return writer.toString();
        });
    }
    
    /**
     * Parse XML stream data incrementally
     */
    public Flux<XmlStanza> parseXmlStream(Flux<String> xmlData) {
        return xmlData
            .map(this::parseXmlFragment)
            .filter(stanza -> stanza != null);
    }
    
    private XmlStanza parseXmlFragment(String xmlFragment) {
        try {
            XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(xmlFragment));
            
            if (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamReader.START_ELEMENT) {
                    String localName = reader.getLocalName();
                    String namespace = reader.getNamespaceURI();
                    
                    return new XmlStanza(localName, namespace, xmlFragment);
                }
            }
            
            return null;
        } catch (XMLStreamException e) {
            throw new XmlStreamProcessingException("Failed to parse XML fragment: " + xmlFragment, e);
        }
    }
    
    /**
     * Generate unique stream ID
     */
    public String generateStreamId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * Validate XML stream header according to RFC6120
     */
    public boolean isValidStreamHeader(String xmlData) {
        try {
            XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(xmlData));
            if (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamReader.START_ELEMENT) {
                    return "stream".equals(reader.getLocalName()) && 
                           "http://etherx.jabber.org/streams".equals(reader.getNamespaceURI());
                }
            }
            return false;
        } catch (XMLStreamException e) {
            return false;
        }
    }
    
    /**
     * Simple XML stanza representation
     */
    public record XmlStanza(String localName, String namespace, String rawXml) {}
    
    /**
     * Exception for XML stream processing errors
     */
    public static class XmlStreamProcessingException extends RuntimeException {
        public XmlStreamProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}