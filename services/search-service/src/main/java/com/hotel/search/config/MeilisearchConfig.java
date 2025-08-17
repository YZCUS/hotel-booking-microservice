package com.hotel.search.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MeilisearchConfig {
    
    @Value("${meilisearch.host}")
    private String host;
    
    @Value("${meilisearch.api-key}")
    private String apiKey;
    
    @Bean
    public Client meilisearchClient() {
        try {
            Config config = new Config(host, apiKey);
            Client client = new Client(config);
            log.info("Meilisearch client configured for host: {}", host);
            return client;
        } catch (Exception e) {
            log.error("Failed to configure Meilisearch client", e);
            throw new RuntimeException("Meilisearch configuration failed", e);
        }
    }
}