package com.hotel.search.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.json.JacksonJsonHandler;
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
            ObjectMapper objectMapper = JsonMapper.builder()
                    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();
            Config config = new Config(host, apiKey, new JacksonJsonHandler(objectMapper));
            Client client = new Client(config);
            log.info("Meilisearch client configured for host: {}", host);
            return client;
        } catch (Exception e) {
            log.error("Failed to configure Meilisearch client", e);
            throw new RuntimeException("Meilisearch configuration failed", e);
        }
    }
}
