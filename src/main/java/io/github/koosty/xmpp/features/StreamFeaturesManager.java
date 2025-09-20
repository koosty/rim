package io.github.koosty.xmpp.features;

import org.springframework.stereotype.Component;

/**
 * Manages XMPP stream features advertisement according to RFC6120.
 * Features are advertised after stream initiation and change based on connection state.
 */
@Component
public class StreamFeaturesManager {

    private static final String STARTTLS_FEATURE = 
        "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'><required/></starttls>";
    
    private static final String SASL_MECHANISMS = 
        "<mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>" +
        "<mechanism>SCRAM-SHA-256</mechanism>" +
        "<mechanism>SCRAM-SHA-1</mechanism>" +
        "<mechanism>PLAIN</mechanism>" +
        "</mechanisms>";
    
    private static final String BIND_FEATURE = 
        "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>";
    
    private static final String SESSION_FEATURE = 
        "<session xmlns='urn:ietf:params:xml:ns:xmpp-session'/>";

    /**
     * Generates stream features for initial connection (before TLS).
     * Only STARTTLS is offered as it's mandatory.
     */
    public String generateInitialFeatures() {
        return wrapFeatures(STARTTLS_FEATURE);
    }

    /**
     * Generates stream features after successful TLS negotiation.
     * SASL authentication mechanisms are offered.
     */
    public String generatePostTlsFeatures() {
        return wrapFeatures(SASL_MECHANISMS);
    }

    /**
     * Generates stream features after successful SASL authentication.
     * Resource binding and session establishment are offered.
     */
    public String generatePostSaslFeatures() {
        return wrapFeatures(BIND_FEATURE + SESSION_FEATURE);
    }

    /**
     * Generates empty features for completed stream negotiation.
     */
    public String generateCompleteFeatures() {
        return wrapFeatures("");
    }

    /**
     * Checks if TLS is supported by this server.
     */
    public boolean isTlsSupported() {
        return true;
    }

    /**
     * Checks if TLS is mandatory for this server.
     */
    public boolean isTlsMandatory() {
        return true;
    }

    /**
     * Gets supported SASL mechanisms in preference order.
     */
    public String[] getSupportedSaslMechanisms() {
        return new String[]{"SCRAM-SHA-256", "SCRAM-SHA-1", "PLAIN"};
    }

    /**
     * Checks if resource binding is supported.
     */
    public boolean isResourceBindingSupported() {
        return true;
    }

    /**
     * Checks if session establishment is supported.
     */
    public boolean isSessionSupported() {
        return true;
    }

    private String wrapFeatures(String features) {
        return "<stream:features xmlns:stream='http://etherx.jabber.org/streams'>" + 
               features + 
               "</stream:features>";
    }
}