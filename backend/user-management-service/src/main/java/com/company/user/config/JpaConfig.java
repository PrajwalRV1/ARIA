package com.company.user.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@Profile("dev")
@EnableJpaRepositories(basePackages = "com.company.user.repository")
@EntityScan(basePackages = "com.company.user.model")
public class JpaConfig {
    // This configuration explicitly enables JPA repositories for the dev profile
}
