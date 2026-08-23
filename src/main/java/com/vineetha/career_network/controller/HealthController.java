package com.vineetha.career_network.controller;

import org.neo4j.driver.Driver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final Driver driver;

    public HealthController(Driver driver) {
        this.driver = driver;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        try {
            driver.verifyConnectivity();
            return Map.of("status", "UP");
        } catch (Exception ex) {
            return Map.of("status", "DOWN", "message", ex.getMessage());
        }
    }
}

