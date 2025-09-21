package io.github.koosty.xmpp.service;

import reactor.core.publisher.Mono;

/**
 * Service for processing IQ (Info/Query) stanzas in XMPP connections.
 * Handles various IQ types including ping, version, disco, and custom IQs.
 * Addresses the critical gap in IQ stanza routing and processing.
 */
public interface IqProcessingService {
    
    /**
     * Process incoming IQ stanza and generate appropriate response.
     * 
     * @param connectionId unique connection identifier
     * @param fullJid the full JID of the connection
     * @param xmlData the incoming IQ XML stanza
     * @return IQ processing result with response XML
     */
    Mono<IqProcessingResult> processIq(String connectionId, String fullJid, String xmlData);
    
    /**
     * Process ping IQ request (XEP-0199).
     * 
     * @param connectionId unique connection identifier
     * @param fullJid the full JID of the connection
     * @param iqId the IQ identifier
     * @return ping response result
     */
    Mono<IqProcessingResult> processPing(String connectionId, String fullJid, String iqId);
    
    /**
     * Process version IQ request (XEP-0092).
     * 
     * @param connectionId unique connection identifier
     * @param fullJid the full JID of the connection
     * @param iqId the IQ identifier
     * @return version response result
     */
    Mono<IqProcessingResult> processVersion(String connectionId, String fullJid, String iqId);
    
    /**
     * Process service discovery info request (XEP-0030).
     * 
     * @param connectionId unique connection identifier
     * @param fullJid the full JID of the connection
     * @param iqId the IQ identifier
     * @param targetJid the target JID for discovery
     * @return discovery info response result
     */
    Mono<IqProcessingResult> processDiscoInfo(String connectionId, String fullJid, String iqId, String targetJid);
    
    /**
     * Process service discovery items request (XEP-0030).
     * 
     * @param connectionId unique connection identifier
     * @param fullJid the full JID of the connection
     * @param iqId the IQ identifier
     * @param targetJid the target JID for discovery
     * @return discovery items response result
     */
    Mono<IqProcessingResult> processDiscoItems(String connectionId, String fullJid, String iqId, String targetJid);
    
    /**
     * Process roster management IQ request (RFC6121).
     * 
     * @param connectionId unique connection identifier
     * @param fullJid the full JID of the connection
     * @param xmlData the roster IQ XML
     * @return roster response result
     */
    Mono<IqProcessingResult> processRoster(String connectionId, String fullJid, String xmlData);
    
    /**
     * Process privacy list IQ request (XEP-0016).
     * 
     * @param connectionId unique connection identifier
     * @param fullJid the full JID of the connection
     * @param xmlData the privacy list IQ XML
     * @return privacy list response result
     */
    Mono<IqProcessingResult> processPrivacyList(String connectionId, String fullJid, String xmlData);
    
    /**
     * Process custom/unknown IQ request.
     * 
     * @param connectionId unique connection identifier
     * @param fullJid the full JID of the connection
     * @param xmlData the IQ XML
     * @return custom IQ response result (usually feature-not-implemented error)
     */
    Mono<IqProcessingResult> processCustomIq(String connectionId, String fullJid, String xmlData);
    
    /**
     * Clean up IQ processing state when connection closes.
     * 
     * @param connectionId unique connection identifier
     */
    void cleanupConnection(String connectionId);
    
    /**
     * Result of IQ processing operation.
     */
    record IqProcessingResult(
        boolean success,
        String responseXml,
        IqType iqType,
        String iqId,
        String errorType,
        String errorCondition,
        String errorText,
        boolean requiresRouting
    ) {
        
        public static IqProcessingResult success(String responseXml, IqType iqType, String iqId) {
            return new IqProcessingResult(true, responseXml, iqType, iqId, null, null, null, false);
        }
        
        public static IqProcessingResult successWithRouting(String responseXml, IqType iqType, String iqId) {
            return new IqProcessingResult(true, responseXml, iqType, iqId, null, null, null, true);
        }
        
        public static IqProcessingResult error(String errorType, String errorCondition, String errorText, 
                                             String responseXml, IqType iqType, String iqId) {
            return new IqProcessingResult(false, responseXml, iqType, iqId, errorType, errorCondition, errorText, false);
        }
        
        public static IqProcessingResult featureNotImplemented(String iqId, String responseXml) {
            return new IqProcessingResult(false, responseXml, IqType.ERROR, iqId, 
                "cancel", "feature-not-implemented", "Feature not implemented", false);
        }
    }
    
    /**
     * IQ stanza types as defined in RFC6120.
     */
    enum IqType {
        GET,
        SET,
        RESULT,
        ERROR,
        UNKNOWN
    }
    
    /**
     * Common IQ namespaces for identification.
     */
    enum IqNamespace {
        PING("urn:xmpp:ping"),
        VERSION("jabber:iq:version"),
        DISCO_INFO("http://jabber.org/protocol/disco#info"),
        DISCO_ITEMS("http://jabber.org/protocol/disco#items"),
        ROSTER("jabber:iq:roster"),
        PRIVACY("jabber:iq:privacy"),
        BIND("urn:ietf:params:xml:ns:xmpp-bind"),
        SESSION("urn:ietf:params:xml:ns:xmpp-session"),
        UNKNOWN("");
        
        private final String namespace;
        
        IqNamespace(String namespace) {
            this.namespace = namespace;
        }
        
        public String getNamespace() {
            return namespace;
        }
        
        public static IqNamespace fromNamespace(String namespace) {
            for (IqNamespace iqNs : values()) {
                if (iqNs.namespace.equals(namespace)) {
                    return iqNs;
                }
            }
            return UNKNOWN;
        }
    }
}