package com.company.interview.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Custom Flyway configuration specifically for Supabase PostgreSQL pooler compatibility
 * 
 * The Supabase pooler (PgBouncer) causes prepared statement name conflicts during 
 * Flyway migrations due to connection reuse. This configuration ensures Flyway
 * operates without prepared statements to avoid "S_1 already exists" errors.
 */
@Configuration
@Profile("supabase")
public class FlywayConfiguration {

    @Value("${spring.datasource.url}")
    private String dataSourceUrl;
    
    @Value("${spring.datasource.username}")
    private String dataSourceUsername;
    
    @Value("${spring.datasource.password}")
    private String dataSourcePassword;

    /**
     * Custom Flyway configuration that disables prepared statements for Supabase compatibility
     */
    @Bean
    public FlywayConfigurationCustomizer flywayConfigurationCustomizer() {
        return configuration -> {
            // Force disable prepared statements at the JDBC level for Flyway
            Properties jdbcProperties = new Properties();
            jdbcProperties.setProperty("cachePrepStmts", "false");
            jdbcProperties.setProperty("useServerPrepStmts", "false");
            jdbcProperties.setProperty("rewriteBatchedStatements", "false");
            jdbcProperties.setProperty("prepareThreshold", "0");
            jdbcProperties.setProperty("preparedStatementCacheSize", "0");
            jdbcProperties.setProperty("preparedStatementCacheSqlLimit", "0");
            
            // Connection timeout and retry settings
            jdbcProperties.setProperty("connectTimeout", "30000");
            jdbcProperties.setProperty("socketTimeout", "30000");
            jdbcProperties.setProperty("loginTimeout", "30");
            
            // Disable connection validation for pooled connections
            jdbcProperties.setProperty("validationTimeout", "0");
            
            // Set program name for debugging
            jdbcProperties.setProperty("ApplicationName", "ARIA-Flyway-Migration");
            
            // Configure Flyway with the properties
            configuration.connectRetries(3);
            configuration.connectRetriesInterval(10);
            configuration.mixed(true);
            configuration.validateOnMigrate(false);
            configuration.cleanDisabled(true);
            configuration.baselineOnMigrate(true);
            configuration.outOfOrder(true);
            
            // Set JDBC properties via connection configuration
            try {
                // Build a new connection URL with disabled prepared statements
                String connectionUrl = dataSourceUrl;
                
                // Add JDBC parameters to disable prepared statements if not already present
                if (!connectionUrl.contains("cachePrepStmts")) {
                    String separator = connectionUrl.contains("?") ? "&" : "?";
                    connectionUrl += separator + "cachePrepStmts=false";
                    connectionUrl += "&useServerPrepStmts=false";
                    connectionUrl += "&rewriteBatchedStatements=false";
                    connectionUrl += "&prepareThreshold=0";
                    connectionUrl += "&preparedStatementCacheSize=0";
                    connectionUrl += "&preparedStatementCacheSqlLimit=0";
                    connectionUrl += "&ApplicationName=ARIA-Flyway-Migration";
                }
                
                // Configure Flyway to use the modified connection URL
                configuration.dataSource(connectionUrl, dataSourceUsername, dataSourcePassword);
                
            } catch (Exception e) {
                // Fallback to default configuration if URL modification fails
                System.err.println("Warning: Could not configure Flyway connection URL, using defaults: " + e.getMessage());
            }
        };
    }
}
