package io.github.koosty.xmpp.config;

import io.github.koosty.xmpp.service.TlsNegotiationService;
import io.github.koosty.xmpp.service.SaslAuthenticationService;
import io.github.koosty.xmpp.service.ResourceBindingService;
import io.github.koosty.xmpp.service.IqProcessingService;
import io.github.koosty.xmpp.service.impl.DefaultTlsNegotiationService;
import io.github.koosty.xmpp.service.impl.DefaultSaslAuthenticationService;
import io.github.koosty.xmpp.service.impl.DefaultResourceBindingService;
import io.github.koosty.xmpp.service.impl.DefaultIqProcessingService;
import io.github.koosty.xmpp.stream.XmlStreamProcessor;
import io.github.koosty.xmpp.jid.JidValidator;
import io.github.koosty.xmpp.jid.Jid;
import io.github.koosty.xmpp.resource.ResourceManager;
import io.github.koosty.xmpp.auth.UserAuthenticationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import java.util.Optional;
import java.util.Set;

/**
 * Spring configuration for XMPP service layer components.
 * Ensures proper bean registration and dependency injection for service-based architecture.
 */
@Configuration
@ComponentScan({"io.github.koosty.xmpp.service", "io.github.koosty.xmpp.auth"})
@EnableR2dbcRepositories(basePackages = "io.github.koosty.xmpp.repository")
public class ServiceConfiguration {

    // Mock dependency implementations for testing
    @Bean
    public JidValidator jidValidator() {
        return new JidValidator() {
            @Override
            public Optional<Jid> parseJid(String jid) {
                return Optional.of(new Jid("test", "example.com", "resource"));
            }
            
            @Override
            public Optional<Jid> createFullJid(String localPart, String domainPart, String resourcePart) {
                return Optional.of(new Jid(localPart, domainPart, resourcePart));
            }
            
            @Override
            public boolean isValidLocalpart(String localpart) {
                return true;
            }
            
            @Override
            public boolean isValidDomainpart(String domainpart) {
                return true;
            }
            
            @Override
            public boolean isValidResourcepart(String resourcepart) {
                return true;
            }
        };
    }
    
    @Bean
    public ResourceManager resourceManager() {
        return new ResourceManager() {
            @Override
            public String generateResource(Jid jid, String connectionId, String requestedResource) {
                return "resource-" + System.currentTimeMillis();
            }
            
            @Override
            public boolean isResourceAvailable(Jid jid, String resource) {
                return true;
            }
            
            @Override
            public boolean releaseResource(Jid fullJid, String connectionId) {
                return true;
            }
            
            @Override
            public Set<String> getActiveResources(Jid bareJid) {
                return Set.of("test-resource");
            }
            
            @Override
            public String getResourceOwner(Jid fullJid) {
                return "test-connection";
            }
            
            @Override
            public boolean isResourceOwner(Jid fullJid, String connectionId) {
                return true;
            }
            
            @Override
            public int releaseConnectionResources(String connectionId) {
                return 1;
            }
            
            @Override
            public int getActiveResourceCount() {
                return 1;
            }
        };
    }
    
    @Bean
    public XmlStreamProcessor xmlStreamProcessor() {
        // Return the actual component - Spring will create it
        return new XmlStreamProcessor();
    }    /**
     * Configure TLS negotiation service bean.
     */
    @Bean
    public TlsNegotiationService tlsNegotiationService(XmppSecurityProperties securityProperties) {
        return new DefaultTlsNegotiationService(securityProperties);
    }

    /**
     * Configure SASL authentication service bean.
     */
    @Bean
    public SaslAuthenticationService saslAuthenticationService(UserAuthenticationService userAuthenticationService) {
        return new DefaultSaslAuthenticationService(userAuthenticationService);
    }

    /**
     * Configure resource binding service bean.
     */
    @Bean
    public ResourceBindingService resourceBindingService(JidValidator jidValidator,
                                                        ResourceManager resourceManager,
                                                        XmlStreamProcessor xmlStreamProcessor) {
        return new DefaultResourceBindingService(jidValidator, resourceManager, xmlStreamProcessor);
    }

    /**
     * Configure IQ processing service bean.
     */
    @Bean
    public IqProcessingService iqProcessingService(XmlStreamProcessor xmlStreamProcessor) {
        return new DefaultIqProcessingService(xmlStreamProcessor);
    }
}