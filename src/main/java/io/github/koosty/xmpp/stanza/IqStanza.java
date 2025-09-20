package io.github.koosty.xmpp.stanza;

import org.w3c.dom.Node;

/**
 * Represents an XMPP IQ (Info/Query) stanza.
 * Handles request-response communication patterns.
 */
public record IqStanza(
    String id,
    String from,
    String to,
    String type,
    String queryNamespace,
    Node queryPayload,
    Node originalNode
) implements XmppStanza {
    
    /**
     * Create IQ stanza with validation
     */
    public IqStanza {
        // IQ stanzas must have an ID per RFC 6120
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("IQ stanza must have an ID");
        }
        
        // IQ type must be one of: get, set, result, error
        if (type == null || !isValidType(type)) {
            throw new IllegalArgumentException("IQ type must be get, set, result, or error");
        }
    }
    
    /**
     * Validate IQ type
     */
    private static boolean isValidType(String type) {
        return "get".equals(type) || "set".equals(type) || 
               "result".equals(type) || "error".equals(type);
    }
    
    /**
     * Check if this is a request IQ (get or set)
     */
    public boolean isRequest() {
        return "get".equals(type) || "set".equals(type);
    }
    
    /**
     * Check if this is a response IQ (result or error)
     */
    public boolean isResponse() {
        return "result".equals(type) || "error".equals(type);
    }
    
    /**
     * Check if this is a get request
     */
    public boolean isGet() {
        return "get".equals(type);
    }
    
    /**
     * Check if this is a set request
     */
    public boolean isSet() {
        return "set".equals(type);
    }
    
    /**
     * Check if this is a result response
     */
    public boolean isResult() {
        return "result".equals(type);
    }
    
    /**
     * Check if this is an error response
     */
    public boolean isError() {
        return "error".equals(type);
    }
    
    /**
     * Check if IQ has query payload
     */
    public boolean hasQuery() {
        return queryPayload != null;
    }
    
    /**
     * Check if query matches specific namespace
     */
    public boolean isQueryNamespace(String namespace) {
        return namespace != null && namespace.equals(queryNamespace);
    }
    
    @Override
    public XmppStanza withAddressing(String newFrom, String newTo) {
        return new IqStanza(id, newFrom, newTo, type, queryNamespace, queryPayload, originalNode);
    }
    
    /**
     * Create result response to this IQ
     */
    public IqStanza createResult() {
        return createResult(null);
    }
    
    /**
     * Create result response with payload
     */
    public IqStanza createResult(Node resultPayload) {
        return new IqStanza(id, to, from, "result", null, resultPayload, null);
    }
    
    /**
     * Create error response to this IQ
     */
    public IqStanza createError(String errorType, String errorCondition) {
        return new IqStanza(id, to, from, "error", null, null, null);
    }
    
    /**
     * Create ping IQ (XEP-0199)
     */
    public static IqStanza createPing(String to) {
        return new IqStanza(
            "ping-" + System.currentTimeMillis(),
            null,
            to,
            "get",
            "urn:xmpp:ping",
            null,
            null
        );
    }
    
    /**
     * Create roster request IQ (RFC 6121)
     */
    public static IqStanza createRosterRequest() {
        return new IqStanza(
            "roster-" + System.currentTimeMillis(),
            null,
            null,
            "get",
            "jabber:iq:roster",
            null,
            null
        );
    }
    
    /**
     * Create version request IQ (XEP-0092)
     */
    public static IqStanza createVersionRequest(String to) {
        return new IqStanza(
            "version-" + System.currentTimeMillis(),
            null,
            to,
            "get",
            "jabber:iq:version",
            null,
            null
        );
    }
    
    /**
     * Create disco info request IQ (XEP-0030)
     */
    public static IqStanza createDiscoInfoRequest(String to) {
        return createDiscoInfoRequest(to, null);
    }
    
    /**
     * Create disco info request with node
     */
    public static IqStanza createDiscoInfoRequest(String to, String node) {
        return new IqStanza(
            "disco-info-" + System.currentTimeMillis(),
            null,
            to,
            "get",
            "http://jabber.org/protocol/disco#info",
            null,
            null
        );
    }
    
    /**
     * Create disco items request IQ (XEP-0030)
     */
    public static IqStanza createDiscoItemsRequest(String to) {
        return createDiscoItemsRequest(to, null);
    }
    
    /**
     * Create disco items request with node
     */
    public static IqStanza createDiscoItemsRequest(String to, String node) {
        return new IqStanza(
            "disco-items-" + System.currentTimeMillis(),
            null,
            to,
            "get",
            "http://jabber.org/protocol/disco#items",
            null,
            null
        );
    }
}