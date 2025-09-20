package io.github.koosty.xmpp.config;

import io.github.koosty.xmpp.features.StreamFeaturesManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for XMPP Security Properties configuration and behavior.
 * Tests the configuration classes without full Spring context.
 */
class XmppSecurityPropertiesTest {

    @Test
    void testDefaultConfiguration() {
        XmppSecurityProperties properties = new XmppSecurityProperties();
        
        // Test default TLS settings
        assertTrue(properties.getTls().isEnabled(), "TLS should be enabled by default");
        assertTrue(properties.getTls().isRequired(), "TLS should be required by default");
        assertEquals("classpath:keystore.jks", properties.getTls().getKeystorePath());
        assertEquals("changeit", properties.getTls().getKeystorePassword());
        assertEquals("JKS", properties.getTls().getKeystoreType());
        assertEquals("none", properties.getTls().getClientAuth());
        assertArrayEquals(new String[]{"TLSv1.2", "TLSv1.3"}, properties.getTls().getSupportedProtocols());
        
        // Test default SASL settings
        assertArrayEquals(new String[]{"PLAIN", "SCRAM-SHA-1", "SCRAM-SHA-256"}, 
                         properties.getSasl().getMechanisms());
    }

    @Test
    void testTlsDisabledConfiguration() {
        XmppSecurityProperties disabledTlsProps = new XmppSecurityProperties();
        disabledTlsProps.getTls().setEnabled(false);
        disabledTlsProps.getTls().setRequired(false);
        
        assertFalse(disabledTlsProps.getTls().isEnabled(), "TLS should be disabled");
        assertFalse(disabledTlsProps.getTls().isRequired(), "TLS should not be required");
        
        // Test StreamFeaturesManager behavior with TLS disabled
        StreamFeaturesManager manager = new StreamFeaturesManager(disabledTlsProps);
        assertFalse(manager.isTlsSupported(), "TLS should not be supported when disabled");
        assertFalse(manager.isTlsMandatory(), "TLS should not be mandatory when disabled");
        
        // Should generate SASL features directly (not TLS)
        String features = manager.generateInitialFeatures();
        assertFalse(features.contains("starttls"), "STARTTLS should not be offered when disabled");
        assertTrue(features.contains("mechanisms"), "SASL mechanisms should be offered directly");
    }

    @Test
    void testOptionalTlsConfiguration() {
        XmppSecurityProperties optionalTlsProps = new XmppSecurityProperties();
        optionalTlsProps.getTls().setEnabled(true);
        optionalTlsProps.getTls().setRequired(false);
        
        assertTrue(optionalTlsProps.getTls().isEnabled(), "TLS should be enabled");
        assertFalse(optionalTlsProps.getTls().isRequired(), "TLS should not be required");
        
        // Test StreamFeaturesManager behavior with optional TLS
        StreamFeaturesManager manager = new StreamFeaturesManager(optionalTlsProps);
        assertTrue(manager.isTlsSupported(), "TLS should be supported when enabled");
        assertFalse(manager.isTlsMandatory(), "TLS should not be mandatory when optional");
        
        // Should offer optional STARTTLS (without <required/>)
        String features = manager.generateInitialFeatures();
        assertTrue(features.contains("starttls"), "STARTTLS should be offered");
        assertFalse(features.contains("<required/>"), "STARTTLS should not be required");
    }

    @Test
    void testRequiredTlsConfiguration() {
        XmppSecurityProperties requiredTlsProps = new XmppSecurityProperties();
        requiredTlsProps.getTls().setEnabled(true);
        requiredTlsProps.getTls().setRequired(true);
        
        assertTrue(requiredTlsProps.getTls().isEnabled(), "TLS should be enabled");
        assertTrue(requiredTlsProps.getTls().isRequired(), "TLS should be required");
        
        // Test StreamFeaturesManager behavior with required TLS
        StreamFeaturesManager manager = new StreamFeaturesManager(requiredTlsProps);
        assertTrue(manager.isTlsSupported(), "TLS should be supported");
        assertTrue(manager.isTlsMandatory(), "TLS should be mandatory");
        
        // Should offer required STARTTLS
        String features = manager.generateInitialFeatures();
        assertTrue(features.contains("starttls"), "STARTTLS should be offered");
        assertTrue(features.contains("<required/>"), "STARTTLS should be required");
    }

    @Test
    void testCustomSaslMechanisms() {
        XmppSecurityProperties customProps = new XmppSecurityProperties();
        String[] customMechanisms = {"SCRAM-SHA-256", "PLAIN"};
        customProps.getSasl().setMechanisms(customMechanisms);
        
        assertArrayEquals(customMechanisms, customProps.getSasl().getMechanisms());
        
        // Test that StreamFeaturesManager uses custom mechanisms
        StreamFeaturesManager manager = new StreamFeaturesManager(customProps);
        assertArrayEquals(customMechanisms, manager.getSupportedSaslMechanisms());
        
        // Test SASL feature generation
        String saslFeatures = manager.generatePostTlsFeatures();
        assertTrue(saslFeatures.contains("SCRAM-SHA-256"), "Should contain SCRAM-SHA-256");
        assertTrue(saslFeatures.contains("PLAIN"), "Should contain PLAIN");
        assertFalse(saslFeatures.contains("SCRAM-SHA-1"), "Should not contain SCRAM-SHA-1");
    }

    @Test
    void testKeystoreConfiguration() {
        XmppSecurityProperties props = new XmppSecurityProperties();
        
        // Test custom keystore settings
        props.getTls().setKeystorePath("/custom/path/keystore.p12");
        props.getTls().setKeystorePassword("custompass");
        props.getTls().setKeystoreType("PKCS12");
        
        assertEquals("/custom/path/keystore.p12", props.getTls().getKeystorePath());
        assertEquals("custompass", props.getTls().getKeystorePassword());
        assertEquals("PKCS12", props.getTls().getKeystoreType());
    }

    @Test
    void testTlsProtocolConfiguration() {
        XmppSecurityProperties props = new XmppSecurityProperties();
        
        // Test custom protocols
        String[] customProtocols = {"TLSv1.3"};
        props.getTls().setSupportedProtocols(customProtocols);
        
        assertArrayEquals(customProtocols, props.getTls().getSupportedProtocols());
        
        // Test client authentication modes
        props.getTls().setClientAuth("want");
        assertEquals("want", props.getTls().getClientAuth());
    }
}