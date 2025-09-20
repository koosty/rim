package io.github.koosty.xmpp.error;

/**
 * RFC6120 Section 8.3.2 - Stanza Error Types
 */
public enum StanzaErrorType {
    AUTH("auth"),       // retry after providing credentials
    CANCEL("cancel"),   // do not retry (the error cannot be remedied)  
    CONTINUE("continue"), // proceed (the condition was only a warning)
    MODIFY("modify"),   // retry after changing the data sent
    WAIT("wait");       // retry after waiting (the error is temporary)
    
    private final String value;
    
    StanzaErrorType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static StanzaErrorType fromValue(String value) {
        for (StanzaErrorType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown stanza error type: " + value);
    }
}