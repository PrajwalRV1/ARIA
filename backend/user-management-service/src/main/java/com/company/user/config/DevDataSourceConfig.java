package com.company.user.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
@Import({
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration.class
    // Removed SecurityAutoConfiguration - conflicts with custom SecurityConfig
})
public class DevDataSourceConfig {
    // This configuration explicitly imports DataSource and JPA auto-configurations for the dev profile
}
