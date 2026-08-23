package com.vineetha.career_network.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jDriverConfig {

    private static final Logger log = LoggerFactory.getLogger(Neo4jDriverConfig.class);

    @Value("${cognodb.uri}")
    private String uri;

    @Value("${cognodb.username}")
    private String username;

    @Value("${cognodb.password}")
    private String password;

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver() {
        Config config = Config.builder()
                .withMaxConnectionPoolSize(20)
                .build();
        Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
        try {
            driver.verifyConnectivity();
            log.info("Connected to CognoDB at {}", uri);
        } catch (Exception ex) {
            log.warn("Could not verify connectivity to CognoDB at {} on startup: {}. " +
                    "The app will keep trying on each request.", uri, ex.getMessage());
        }
        return driver;
    }
}



