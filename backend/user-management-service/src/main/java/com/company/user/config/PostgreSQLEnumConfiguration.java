package com.company.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

/**
 * PostgreSQL enum configuration to ensure proper handling of custom enum types.
 * This configuration ensures that PostgreSQL enum types are properly recognized by Hibernate.
 */
@Configuration
public class PostgreSQLEnumConfiguration {

    /**
     * Hibernate properties customizer to add PostgreSQL enum handling
     */
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return new HibernatePropertiesCustomizer() {
            @Override
            public void customize(Map<String, Object> hibernateProperties) {
                // Ensure proper PostgreSQL enum handling
                hibernateProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
                hibernateProperties.put("hibernate.jdbc.lob.non_contextual_creation", "true");
                hibernateProperties.put("hibernate.type.preferred_uuid_jdbc_type", "CHAR");
                hibernateProperties.put("hibernate.globally_quoted_identifiers", "false");
                hibernateProperties.put("hibernate.globally_quoted_identifiers_skip_column_definitions", "true");
            }
        };
    }
}
