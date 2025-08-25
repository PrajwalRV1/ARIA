package com.company.user.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@Profile("mysql")
@Import({
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration.class,
    org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class
    // Removed SecurityAutoConfiguration - conflicts with custom SecurityConfig
})
@EnableJpaRepositories(basePackages = "com.company.user.repository")
@EntityScan(basePackages = "com.company.user.model")
public class MySQLConfig {
    // This configuration explicitly imports required auto-configurations for the MySQL profile
}
