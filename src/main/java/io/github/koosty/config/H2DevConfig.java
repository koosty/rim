package io.github.koosty.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import io.r2dbc.spi.ConnectionFactory;

/**
 * Development configuration for H2 database setup.
 * Initializes the database schema and provides development utilities.
 */
@Configuration
@Profile("dev")
public class H2DevConfig {

    /**
     * Initialize the H2 database with the schema when the application starts.
     * This ensures the tables are created and populated with test data.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.sql.init.mode", havingValue = "always")
    public ConnectionFactoryInitializer initializer(ConnectionFactory connectionFactory) {
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema-h2.sql"));
        initializer.setDatabasePopulator(populator);
        
        return initializer;
    }
}