package io.github.koosty.xmpp.features;

import io.github.koosty.xmpp.config.XmppSecurityProperties;
import org.springframework.stereotype.Component;

/**
 * Manages XMPP stream features advertisement according to RFC6120.
 * Features are advertised after stream initiation and change based on connection state.
 */
@Component
public class StreamFeaturesManager {

    private final XmppSecurityProperties securityProperties;

    public StreamFeaturesManager(XmppSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    private static final String BIND_FEATURE = 
        "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>";
    
    private static final String SESSION_FEATURE = 
        "<session xmlns='urn:ietf:params:xml:ns:xmpp-session'/>";

    /**
     * Generates stream features for initial connection (before TLS).
     * STARTTLS is offered if TLS is enabled.
     * SASL mechanisms are also offered if TLS is not required.
     */
    public String generateInitialFeatures() {
        if (securityProperties.getTls().isEnabled()) {
            String starttlsFeature = securityProperties.getTls().isRequired() ?
                "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'><required/></starttls>" :
                "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>";
            
            // If TLS is not required, also offer SASL mechanisms in initial features
            if (!securityProperties.getTls().isRequired()) {
                String saslFeatures = generateSaslMechanisms();
                return wrapFeatures(starttlsFeature + saslFeatures);
            } else {
                return wrapFeatures(starttlsFeature);
            }
        } else {
            // If TLS is disabled, offer SASL mechanisms directly
            return generateSaslFeatures();
        }
    }

    /**
     * Generates stream features after successful TLS negotiation.
     * SASL authentication mechanisms are offered.
     */
    public String generatePostTlsFeatures() {
        return generateSaslFeatures();
    }

    /**
     * Generates SASL mechanism features based on configuration.
     */
    private String generateSaslFeatures() {
        return wrapFeatures(generateSaslMechanisms());
    }
    
    /**
     * Generates just the SASL mechanisms XML without the stream:features wrapper.
     */
    private String generateSaslMechanisms() {
        String[] mechanisms = securityProperties.getSasl().getMechanisms();
        if (mechanisms.length == 0) {
            return "";
        }

        StringBuilder saslBuilder = new StringBuilder();
        saslBuilder.append("<mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>");
        for (String mechanism : mechanisms) {
            saslBuilder.append("<mechanism>").append(mechanism).append("</mechanism>");
        }
        saslBuilder.append("</mechanisms>");

        return saslBuilder.toString();
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
        return securityProperties.getTls().isEnabled();
    }

    /**
     * Checks if TLS is mandatory for this server.
     */
    public boolean isTlsMandatory() {
        return securityProperties.getTls().isEnabled() && securityProperties.getTls().isRequired();
    }

    /**
     * Gets supported SASL mechanisms in preference order.
     */
    public String[] getSupportedSaslMechanisms() {
        return securityProperties.getSasl().getMechanisms();
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