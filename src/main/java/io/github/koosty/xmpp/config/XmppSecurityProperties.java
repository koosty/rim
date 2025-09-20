package io.github.koosty.xmpp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for XMPP security settings.
 * Reads from application.yml xmpp.security section.
 */
@ConfigurationProperties(prefix = "xmpp.security")
public class XmppSecurityProperties {

    private final Tls tls = new Tls();
    private final Sasl sasl = new Sasl();

    public Tls getTls() {
        return tls;
    }

    public Sasl getSasl() {
        return sasl;
    }

    public static class Tls {
        /**
         * Enable TLS support on the server
         */
        private boolean enabled = true;

        /**
         * Require TLS for all connections
         */
        private boolean required = true;

        /**
         * Path to the keystore file
         */
        private String keystorePath = "classpath:keystore.jks";

        /**
         * Password for the keystore
         */
        private String keystorePassword = "changeit";

        /**
         * Type of keystore (JKS, PKCS12, etc.)
         */
        private String keystoreType = "JKS";

        /**
         * TLS protocol versions to support
         */
        private String[] supportedProtocols = {"TLSv1.2", "TLSv1.3"};

        /**
         * TLS cipher suites to support (empty = use JVM defaults)
         */
        private String[] supportedCipherSuites = {};

        /**
         * Client certificate authentication mode
         * Options: none, want, need
         */
        private String clientAuth = "none";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getKeystorePath() {
            return keystorePath;
        }

        public void setKeystorePath(String keystorePath) {
            this.keystorePath = keystorePath;
        }

        public String getKeystorePassword() {
            return keystorePassword;
        }

        public void setKeystorePassword(String keystorePassword) {
            this.keystorePassword = keystorePassword;
        }

        public String getKeystoreType() {
            return keystoreType;
        }

        public void setKeystoreType(String keystoreType) {
            this.keystoreType = keystoreType;
        }

        public String[] getSupportedProtocols() {
            return supportedProtocols;
        }

        public void setSupportedProtocols(String[] supportedProtocols) {
            this.supportedProtocols = supportedProtocols;
        }

        public String[] getSupportedCipherSuites() {
            return supportedCipherSuites;
        }

        public void setSupportedCipherSuites(String[] supportedCipherSuites) {
            this.supportedCipherSuites = supportedCipherSuites;
        }

        public String getClientAuth() {
            return clientAuth;
        }

        public void setClientAuth(String clientAuth) {
            this.clientAuth = clientAuth;
        }
    }

    public static class Sasl {
        /**
         * SASL mechanisms supported by the server
         */
        private String[] mechanisms = {"PLAIN", "SCRAM-SHA-1", "SCRAM-SHA-256"};

        public String[] getMechanisms() {
            return mechanisms;
        }

        public void setMechanisms(String[] mechanisms) {
            this.mechanisms = mechanisms;
        }
    }
}