package io.github.koosty.xmpp.error;

/**
 * RFC6120 Section 8.3.3 - Defined Stanza Error Conditions
 */
public enum StanzaErrorCondition {
    BAD_REQUEST("bad-request"),
    CONFLICT("conflict"),
    FEATURE_NOT_IMPLEMENTED("feature-not-implemented"),
    FORBIDDEN("forbidden"),
    GONE("gone"),
    INTERNAL_SERVER_ERROR("internal-server-error"),
    ITEM_NOT_FOUND("item-not-found"),
    JID_MALFORMED("jid-malformed"),
    NOT_ACCEPTABLE("not-acceptable"),
    NOT_ALLOWED("not-allowed"),
    NOT_AUTHORIZED("not-authorized"),
    POLICY_VIOLATION("policy-violation"),
    RECIPIENT_UNAVAILABLE("recipient-unavailable"),
    REDIRECT("redirect"),
    REGISTRATION_REQUIRED("registration-required"),
    REMOTE_SERVER_NOT_FOUND("remote-server-not-found"),
    REMOTE_SERVER_TIMEOUT("remote-server-timeout"),
    RESOURCE_CONSTRAINT("resource-constraint"),
    SERVICE_UNAVAILABLE("service-unavailable"),
    SUBSCRIPTION_REQUIRED("subscription-required"),
    UNDEFINED_CONDITION("undefined-condition"),
    UNEXPECTED_REQUEST("unexpected-request");
    
    private final String elementName;
    
    StanzaErrorCondition(String elementName) {
        this.elementName = elementName;
    }
    
    public String getElementName() {
        return elementName;
    }
    
    public static StanzaErrorCondition fromElementName(String elementName) {
        for (StanzaErrorCondition condition : values()) {
            if (condition.elementName.equals(elementName)) {
                return condition;
            }
        }
        return UNDEFINED_CONDITION;
    }
}