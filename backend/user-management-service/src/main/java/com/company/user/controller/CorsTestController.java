package com.company.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;

@RestController
@RequestMapping("/api/auth")
public class CorsTestController {

    private static final Logger logger = LoggerFactory.getLogger(CorsTestController.class);

    @GetMapping("/cors-test")
    public ResponseEntity<?> corsTest(HttpServletRequest request) {
        logger.info("CORS Test endpoint called from: {}", request.getHeader("Origin"));
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "CORS test successful");
        response.put("origin", request.getHeader("Origin"));
        response.put("userAgent", request.getHeader("User-Agent"));
        
        // Log all headers for debugging
        Enumeration<String> headerNames = request.getHeaderNames();
        Map<String, String> headers = new HashMap<>();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        
        logger.info("Request headers: {}", headers);
        response.put("headers", headers);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cors-test")
    public ResponseEntity<?> corsTestPost(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        logger.info("CORS POST Test endpoint called from: {}", request.getHeader("Origin"));
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "CORS POST test successful");
        response.put("origin", request.getHeader("Origin"));
        response.put("receivedBody", body);
        
        return ResponseEntity.ok(response);
    }
}
