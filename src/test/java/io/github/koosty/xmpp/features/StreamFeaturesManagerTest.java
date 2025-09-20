package io.github.koosty.xmpp.features;

import io.github.koosty.xmpp.config.XmppSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for StreamFeaturesManager functionality.
 */
class StreamFeaturesManagerTest {
    
    private StreamFeaturesManager featuresManager;
    private XmppSecurityProperties securityProperties;
    
    @BeforeEach
    void setUp() {
        securityProperties = new XmppSecurityProperties();
        featuresManager = new StreamFeaturesManager(securityProperties);
    }
    
    @Test
    void shouldGenerateInitialFeatures() {
        String features = featuresManager.generateInitialFeatures();
        
        assertNotNull(features);
        assertTrue(features.contains("<stream:features"));
        assertTrue(features.contains("starttls"));
        assertTrue(features.contains("required"));
        assertTrue(features.contains("urn:ietf:params:xml:ns:xmpp-tls"));
    }
    
    @Test
    void shouldGeneratePostTlsFeatures() {
        String features = featuresManager.generatePostTlsFeatures();
        
        assertNotNull(features);
        assertTrue(features.contains("<stream:features"));
        assertTrue(features.contains("mechanisms"));
        assertTrue(features.contains("SCRAM-SHA-256"));
        assertTrue(features.contains("SCRAM-SHA-1"));
        assertTrue(features.contains("PLAIN"));
        assertTrue(features.contains("urn:ietf:params:xml:ns:xmpp-sasl"));
    }
    
    @Test
    void shouldGeneratePostSaslFeatures() {
        String features = featuresManager.generatePostSaslFeatures();
        
        assertNotNull(features);
        assertTrue(features.contains("<stream:features"));
        assertTrue(features.contains("bind"));
        assertTrue(features.contains("session"));
        assertTrue(features.contains("urn:ietf:params:xml:ns:xmpp-bind"));
        assertTrue(features.contains("urn:ietf:params:xml:ns:xmpp-session"));
    }
    
    @Test
    void shouldGenerateCompleteFeatures() {
        String features = featuresManager.generateCompleteFeatures();
        
        assertNotNull(features);
        assertTrue(features.contains("<stream:features"));
        // Should be empty features
        assertFalse(features.contains("starttls"));
        assertFalse(features.contains("mechanisms"));
        assertFalse(features.contains("bind"));
    }
    
    @Test
    void shouldSupportTls() {
        assertTrue(featuresManager.isTlsSupported());
        assertTrue(featuresManager.isTlsMandatory());
    }
    
    @Test
    void shouldListSupportedSaslMechanisms() {
        String[] mechanisms = featuresManager.getSupportedSaslMechanisms();
        
        assertEquals(3, mechanisms.length);
        assertThat(mechanisms)
            .contains("SCRAM-SHA-256", "SCRAM-SHA-1", "PLAIN");
    }
    
    @Test
    void shouldSupportResourceBinding() {
        assertTrue(featuresManager.isResourceBindingSupported());
        assertTrue(featuresManager.isSessionSupported());
    }
}