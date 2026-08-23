package com.vineetha.career_network.service;

import com.vineetha.career_network.repository.GraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * On application startup, loads the seed dataset (src/main/resources/seed.cypher)
 * into CognoDB - but only if the database is currently empty, so restarts
 * don't duplicate data and an already-populated instance is left untouched.
 */
@Component
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private final GraphRepository graphRepository;

    @Value("${careernetwork.seed.enabled:true}")
    private boolean seedEnabled;

    public SeedDataLoader(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        try {
            if (graphRepository.countPeople() > 0) {
                log.info("CognoDB already has data - skipping seed.");
                return;
            }
        } catch (Exception ex) {
            log.warn("Could not check whether CognoDB already has data ({}). Skipping seed for now.",
                    ex.getMessage());
            return;
        }

        try {
            String script = readSeedScript();
            graphRepository.runScript(List.of(script));
            log.info("Seed data loaded into CognoDB.");
        } catch (Exception ex) {
            log.warn("Failed to load seed data: {}", ex.getMessage());
        }
    }

    private String readSeedScript() throws IOException {
        try (InputStream in = new ClassPathResource("seed.cypher").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

