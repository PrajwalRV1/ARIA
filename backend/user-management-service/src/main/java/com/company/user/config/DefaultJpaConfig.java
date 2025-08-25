package com.company.user.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@Profile("default")
@EnableJpaRepositories(basePackages = "com.company.user.repository")
@EntityScan(basePackages = "com.company.user.model")
@EnableTransactionManagement
public class DefaultJpaConfig {
    // This configuration enables JPA repositories for the default profile
}
