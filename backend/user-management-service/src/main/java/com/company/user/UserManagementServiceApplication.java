package com.company.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(exclude = {
    UserDetailsServiceAutoConfiguration.class
})
@EnableJpaRepositories(basePackages = "com.company.user.repository")
public class UserManagementServiceApplication {
    public static void main(String[] args) {
        // Print environment info for Render debugging
        System.out.println("========================================");
        System.out.println("ARIA User Management Service Starting");
        System.out.println("PORT environment variable: " + System.getenv("PORT"));
        System.out.println("Active profiles: " + System.getProperty("spring.profiles.active"));
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("========================================");
        
        SpringApplication.run(UserManagementServiceApplication.class, args);
    }
}
