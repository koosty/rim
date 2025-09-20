package io.github.koosty.xmpp.error;

/**
 * RFC6120 Section 4.9.3 - Defined Stream Error Conditions
 */
public enum StreamErrorCondition {
    BAD_FORMAT("bad-format"),
    BAD_NAMESPACE_PREFIX("bad-namespace-prefix"), 
    CONFLICT("conflict"),
    CONNECTION_TIMEOUT("connection-timeout"),
    HOST_GONE("host-gone"),
    HOST_UNKNOWN("host-unknown"),
    IMPROPER_ADDRESSING("improper-addressing"),
    INTERNAL_SERVER_ERROR("internal-server-error"),
    INVALID_FROM("invalid-from"),
    INVALID_NAMESPACE("invalid-namespace"),
    INVALID_XML("invalid-xml"),
    NOT_AUTHORIZED("not-authorized"),
    NOT_WELL_FORMED("not-well-formed"),
    POLICY_VIOLATION("policy-violation"),
    REMOTE_CONNECTION_FAILED("remote-connection-failed"),
    RESET("reset"),
    RESOURCE_CONSTRAINT("resource-constraint"),
    RESTRICTED_XML("restricted-xml"),
    SEE_OTHER_HOST("see-other-host"),
    SYSTEM_SHUTDOWN("system-shutdown"),
    UNDEFINED_CONDITION("undefined-condition"),
    UNSUPPORTED_ENCODING("unsupported-encoding"),
    UNSUPPORTED_FEATURE("unsupported-feature"),
    UNSUPPORTED_STANZA_TYPE("unsupported-stanza-type"),
    UNSUPPORTED_VERSION("unsupported-version");
    
    private final String elementName;
    
    StreamErrorCondition(String elementName) {
        this.elementName = elementName;
    }
    
    public String getElementName() {
        return elementName;
    }
    
    public static StreamErrorCondition fromElementName(String elementName) {
        for (StreamErrorCondition condition : values()) {
            if (condition.elementName.equals(elementName)) {
                return condition;
            }
        }
        return UNDEFINED_CONDITION;
    }
}