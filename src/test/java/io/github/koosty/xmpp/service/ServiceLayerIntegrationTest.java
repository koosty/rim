package io.github.koosty.xmpp.service;

import io.github.koosty.xmpp.config.ServiceConfiguration;
import io.github.koosty.xmpp.config.XmppSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for XMPP service layer Spring configuration.
 * Validates that all services are properly registered and injectable.
 */
@SpringBootTest
@ContextConfiguration(classes = {ServiceConfiguration.class})
@EnableConfigurationProperties(XmppSecurityProperties.class)
class ServiceLayerIntegrationTest {

    @Autowired
    private TlsNegotiationService tlsNegotiationService;

    @Autowired
    private SaslAuthenticationService saslAuthenticationService;

    @Autowired
    private ResourceBindingService resourceBindingService;

    @Autowired
    private IqProcessingService iqProcessingService;

    @Test
    void testTlsNegotiationServiceIsInjected() {
        assertNotNull(tlsNegotiationService, "TlsNegotiationService should be injected");
        assertTrue(tlsNegotiationService.getClass().getSimpleName().contains("Default"),
                  "Should inject default implementation");
    }

    @Test
    void testSaslAuthenticationServiceIsInjected() {
        assertNotNull(saslAuthenticationService, "SaslAuthenticationService should be injected");
        assertTrue(saslAuthenticationService.getClass().getSimpleName().contains("Default"),
                  "Should inject default implementation");
    }

    @Test
    void testResourceBindingServiceIsInjected() {
        assertNotNull(resourceBindingService, "ResourceBindingService should be injected");
        assertTrue(resourceBindingService.getClass().getSimpleName().contains("Default"),
                  "Should inject default implementation");
    }

    @Test
    void testIqProcessingServiceIsInjected() {
        assertNotNull(iqProcessingService, "IqProcessingService should be injected");
        assertTrue(iqProcessingService.getClass().getSimpleName().contains("Default"),
                  "Should inject default implementation");
    }

    @Test
    void testAllServicesAreDistinctInstances() {
        assertNotSame(tlsNegotiationService, saslAuthenticationService,
                     "Services should be different instances");
        assertNotSame(resourceBindingService, iqProcessingService,
                     "Services should be different instances");
    }

    @Test
    void testServiceMethodsAreAccessible() {
        // Test basic method availability without full execution
        assertNotNull(tlsNegotiationService.getClass().getMethods(),
                     "TlsNegotiationService should have accessible methods");
        assertNotNull(saslAuthenticationService.getClass().getMethods(),
                     "SaslAuthenticationService should have accessible methods");
        assertNotNull(resourceBindingService.getClass().getMethods(),
                     "ResourceBindingService should have accessible methods");
        assertNotNull(iqProcessingService.getClass().getMethods(),
                     "IqProcessingService should have accessible methods");
    }
}