package io.github.koosty.xmpp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;

/**
 * Configuration properties for XMPP server and Actor system.
 * Supports runtime configuration updates and validation.
 */
@Configuration
@ConfigurationProperties(prefix = "xmpp")
@Validated
public class XmppServerConfiguration {
    
    /**
     * Server configuration
     */
    private Server server = new Server();
    
    /**
     * Actor system configuration
     */
    private ActorSystem actorSystem = new ActorSystem();
    
    /**
     * Connection configuration
     */
    private Connection connection = new Connection();
    
    /**
     * Monitoring configuration
     */
    private Monitoring monitoring = new Monitoring();
    
    /**
     * Security configuration
     */
    private Security security = new Security();
    
    public static class Server {
        @NotBlank
        private String name = "localhost";
        
        @NotBlank
        private String version = "1.0.0";
        
        @Min(1024)
        @Max(65535)
        private int port = 5222;
        
        @NotBlank
        private String domain = "localhost";
        
        private List<String> virtualHosts = List.of("localhost");
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
        
        public List<String> getVirtualHosts() { return virtualHosts; }
        public void setVirtualHosts(List<String> virtualHosts) { this.virtualHosts = virtualHosts; }
    }
    
    public static class ActorSystem {
        @Min(1)
        @Max(10000)
        private int mailboxSize = 1000;
        
        @Min(1)
        @Max(100)
        private int maxActors = 50;
        
        @NotNull
        private Duration healthCheckInterval = Duration.ofSeconds(30);
        
        @NotNull
        private Duration actorStartupTimeout = Duration.ofSeconds(10);
        
        @NotNull
        private Duration messageProcessingTimeout = Duration.ofSeconds(5);
        
        private boolean enableSupervision = true;
        
        private String supervisionStrategy = "RESTART";
        
        // Getters and setters
        public int getMailboxSize() { return mailboxSize; }
        public void setMailboxSize(int mailboxSize) { this.mailboxSize = mailboxSize; }
        
        public int getMaxActors() { return maxActors; }
        public void setMaxActors(int maxActors) { this.maxActors = maxActors; }
        
        public Duration getHealthCheckInterval() { return healthCheckInterval; }
        public void setHealthCheckInterval(Duration healthCheckInterval) { 
            this.healthCheckInterval = healthCheckInterval; 
        }
        
        public Duration getActorStartupTimeout() { return actorStartupTimeout; }
        public void setActorStartupTimeout(Duration actorStartupTimeout) { 
            this.actorStartupTimeout = actorStartupTimeout; 
        }
        
        public Duration getMessageProcessingTimeout() { return messageProcessingTimeout; }
        public void setMessageProcessingTimeout(Duration messageProcessingTimeout) { 
            this.messageProcessingTimeout = messageProcessingTimeout; 
        }
        
        public boolean isEnableSupervision() { return enableSupervision; }
        public void setEnableSupervision(boolean enableSupervision) { 
            this.enableSupervision = enableSupervision; 
        }
        
        public String getSupervisionStrategy() { return supervisionStrategy; }
        public void setSupervisionStrategy(String supervisionStrategy) { 
            this.supervisionStrategy = supervisionStrategy; 
        }
    }
    
    public static class Connection {
        @Min(1)
        @Max(100000)
        private int maxConnections = 10000;
        
        @NotNull
        private Duration connectionTimeout = Duration.ofMinutes(5);
        
        @NotNull
        private Duration idleTimeout = Duration.ofMinutes(10);
        
        @Min(1)
        @Max(1000)
        private int maxConnectionsPerIp = 10;
        
        private boolean enableCompression = true;
        
        private boolean requireTls = false;
        
        @Min(1024)
        @Max(1048576)
        private int receiveBufferSize = 65536;
        
        @Min(1024)
        @Max(1048576)
        private int sendBufferSize = 65536;
        
        // Getters and setters
        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
        
        public Duration getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(Duration connectionTimeout) { 
            this.connectionTimeout = connectionTimeout; 
        }
        
        public Duration getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(Duration idleTimeout) { this.idleTimeout = idleTimeout; }
        
        public int getMaxConnectionsPerIp() { return maxConnectionsPerIp; }
        public void setMaxConnectionsPerIp(int maxConnectionsPerIp) { 
            this.maxConnectionsPerIp = maxConnectionsPerIp; 
        }
        
        public boolean isEnableCompression() { return enableCompression; }
        public void setEnableCompression(boolean enableCompression) { 
            this.enableCompression = enableCompression; 
        }
        
        public boolean isRequireTls() { return requireTls; }
        public void setRequireTls(boolean requireTls) { this.requireTls = requireTls; }
        
        public int getReceiveBufferSize() { return receiveBufferSize; }
        public void setReceiveBufferSize(int receiveBufferSize) { 
            this.receiveBufferSize = receiveBufferSize; 
        }
        
        public int getSendBufferSize() { return sendBufferSize; }
        public void setSendBufferSize(int sendBufferSize) { this.sendBufferSize = sendBufferSize; }
    }
    
    public static class Monitoring {
        @NotNull
        private Duration metricsCollectionInterval = Duration.ofSeconds(60);
        
        @NotNull
        private Duration healthCheckInterval = Duration.ofSeconds(30);
        
        private boolean enableMetrics = true;
        
        private boolean enableHealthChecks = true;
        
        private boolean enableActuator = true;
        
        @Min(1)
        @Max(10000)
        private int metricsHistorySize = 1000;
        
        // Getters and setters
        public Duration getMetricsCollectionInterval() { return metricsCollectionInterval; }
        public void setMetricsCollectionInterval(Duration metricsCollectionInterval) { 
            this.metricsCollectionInterval = metricsCollectionInterval; 
        }
        
        public Duration getHealthCheckInterval() { return healthCheckInterval; }
        public void setHealthCheckInterval(Duration healthCheckInterval) { 
            this.healthCheckInterval = healthCheckInterval; 
        }
        
        public boolean isEnableMetrics() { return enableMetrics; }
        public void setEnableMetrics(boolean enableMetrics) { this.enableMetrics = enableMetrics; }
        
        public boolean isEnableHealthChecks() { return enableHealthChecks; }
        public void setEnableHealthChecks(boolean enableHealthChecks) { 
            this.enableHealthChecks = enableHealthChecks; 
        }
        
        public boolean isEnableActuator() { return enableActuator; }
        public void setEnableActuator(boolean enableActuator) { this.enableActuator = enableActuator; }
        
        public int getMetricsHistorySize() { return metricsHistorySize; }
        public void setMetricsHistorySize(int metricsHistorySize) { 
            this.metricsHistorySize = metricsHistorySize; 
        }
    }
    
    public static class Security {
        private List<String> supportedSaslMechanisms = List.of("PLAIN", "SCRAM-SHA-1", "SCRAM-SHA-256");
        
        private boolean requireAuthentication = true;
        
        private boolean allowAnonymous = false;
        
        @Min(8)
        @Max(128)
        private int minPasswordLength = 8;
        
        @Min(1)
        @Max(10)
        private int maxAuthenticationAttempts = 3;
        
        @NotNull
        private Duration authenticationTimeout = Duration.ofSeconds(30);
        
        // Getters and setters
        public List<String> getSupportedSaslMechanisms() { return supportedSaslMechanisms; }
        public void setSupportedSaslMechanisms(List<String> supportedSaslMechanisms) { 
            this.supportedSaslMechanisms = supportedSaslMechanisms; 
        }
        
        public boolean isRequireAuthentication() { return requireAuthentication; }
        public void setRequireAuthentication(boolean requireAuthentication) { 
            this.requireAuthentication = requireAuthentication; 
        }
        
        public boolean isAllowAnonymous() { return allowAnonymous; }
        public void setAllowAnonymous(boolean allowAnonymous) { this.allowAnonymous = allowAnonymous; }
        
        public int getMinPasswordLength() { return minPasswordLength; }
        public void setMinPasswordLength(int minPasswordLength) { 
            this.minPasswordLength = minPasswordLength; 
        }
        
        public int getMaxAuthenticationAttempts() { return maxAuthenticationAttempts; }
        public void setMaxAuthenticationAttempts(int maxAuthenticationAttempts) { 
            this.maxAuthenticationAttempts = maxAuthenticationAttempts; 
        }
        
        public Duration getAuthenticationTimeout() { return authenticationTimeout; }
        public void setAuthenticationTimeout(Duration authenticationTimeout) { 
            this.authenticationTimeout = authenticationTimeout; 
        }
    }
    
    // Main getters and setters
    public Server getServer() { return server; }
    public void setServer(Server server) { this.server = server; }
    
    public ActorSystem getActorSystem() { return actorSystem; }
    public void setActorSystem(ActorSystem actorSystem) { this.actorSystem = actorSystem; }
    
    public Connection getConnection() { return connection; }
    public void setConnection(Connection connection) { this.connection = connection; }
    
    public Monitoring getMonitoring() { return monitoring; }
    public void setMonitoring(Monitoring monitoring) { this.monitoring = monitoring; }
    
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    
    /**
     * Validate configuration after changes
     */
    public boolean isValid() {
        try {
            // Server validation
            if (server.name == null || server.name.trim().isEmpty()) return false;
            if (server.port < 1024 || server.port > 65535) return false;
            
            // Actor system validation
            if (actorSystem.mailboxSize < 1 || actorSystem.mailboxSize > 10000) return false;
            if (actorSystem.maxActors < 1 || actorSystem.maxActors > 100) return false;
            
            // Connection validation  
            if (connection.maxConnections < 1 || connection.maxConnections > 100000) return false;
            if (connection.maxConnectionsPerIp < 1 || connection.maxConnectionsPerIp > 1000) return false;
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get configuration summary for logging
     */
    public String getConfigurationSummary() {
        return String.format(
            "XMPP Server Config: %s:%d, MaxConn:%d, Actors:%d, Mailbox:%d", 
            server.domain, 
            server.port, 
            connection.maxConnections,
            actorSystem.maxActors,
            actorSystem.mailboxSize
        );
    }
}