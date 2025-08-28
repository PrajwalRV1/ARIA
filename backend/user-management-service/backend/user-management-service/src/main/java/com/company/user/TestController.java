package com.company.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    
    @GetMapping("/test")
    public String test() {
        return "Hello Railway!";
    }
    
    @GetMapping("/")
    public String root() {
        return "User Management Service is running!";
    }
}
