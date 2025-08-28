package com.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class TestApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
    
    @GetMapping("/")
    public String home() {
        return "Test service is running on port: " + System.getProperty("server.port", "unknown");
    }
    
    @GetMapping("/health")
    public String health() {
        return "UP";
    }
}
