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
        System.out.println("DATABASE_URL: " + (System.getenv("DATABASE_URL") != null ? "SET" : "NOT SET"));
        System.out.println("Active profiles: " + System.getProperty("spring.profiles.active"));
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("Available memory: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + "MB");
        System.out.println("========================================");
        
        SpringApplication app = new SpringApplication(UserManagementServiceApplication.class);
        // Set render profile explicitly if not set
        if (System.getProperty("spring.profiles.active") == null && System.getenv("RENDER") != null) {
            app.setAdditionalProfiles("render");
            System.out.println("RENDER environment detected - setting 'render' profile");
        }
        
        System.out.println("Starting Spring Application...");
        app.run(args);
        System.out.println("Spring Application started successfully!");
    }
}
