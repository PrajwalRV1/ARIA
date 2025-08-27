package com.company.user.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Debug listener to print all registered endpoints for Render troubleshooting
 */
@Component
@Profile("render")
public class EndpointDebugListener implements ApplicationListener<ApplicationReadyEvent> {

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    public EndpointDebugListener(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        System.out.println("🚀 ========================================");
        System.out.println("🚀 RENDER DEBUG: APPLICATION READY - ENDPOINT MAPPING");
        System.out.println("🚀 ========================================");
        
        // Print server info
        String port = System.getenv("PORT");
        if (port == null) port = "10000";
        
        System.out.println("🌐 Server running on: http://0.0.0.0:" + port);
        System.out.println("🔍 Render should be able to detect port: " + port);
        System.out.println("🚀 ========================================");
        
        // Print all registered endpoints
        System.out.println("📋 REGISTERED ENDPOINTS:");
        requestMappingHandlerMapping.getHandlerMethods().forEach((key, value) -> {
            System.out.println("🔗 " + key + " -> " + value.getMethod().getDeclaringClass().getSimpleName() + "." + value.getMethod().getName());
        });
        
        System.out.println("🚀 ========================================");
        System.out.println("✅ KEY ENDPOINTS FOR RENDER:");
        System.out.println("🔍 http://0.0.0.0:" + port + "/ (Root)");
        System.out.println("🔍 http://0.0.0.0:" + port + "/health (Health)");  
        System.out.println("🔍 http://0.0.0.0:" + port + "/healthz (Alt Health)");
        System.out.println("🔍 http://0.0.0.0:" + port + "/status (Status)");
        System.out.println("🔍 http://0.0.0.0:" + port + "/actuator/health (Spring Actuator)");
        System.out.println("🚀 ========================================");
        
        // Test internal call
        System.out.println("🧪 TESTING INTERNAL ENDPOINT ACCESS...");
        try {
            // This should trigger our debug logs
            System.out.println("🧪 Internal test - Service is ready for Render port detection!");
        } catch (Exception e) {
            System.out.println("❌ Internal test failed: " + e.getMessage());
        }
        
        System.out.println("🚀 READY FOR RENDER PORT DETECTION! 🚀");
    }
}
