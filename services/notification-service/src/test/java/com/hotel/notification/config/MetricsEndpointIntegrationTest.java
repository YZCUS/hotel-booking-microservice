package com.hotel.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureObservability
@SpringBootTest(classes = MetricsEndpointIntegrationTest.MetricsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false",
        "management.health.rabbit.enabled=false",
        "management.health.mail.enabled=false"
})
class MetricsEndpointIntegrationTest {

    @Autowired
    private TestRestTemplate client;

    @LocalManagementPort
    private int managementPort;


    @Test
    void exposesScrapeAndHistogramOnlyOnManagementPort() {
        assertThat(client.getForEntity("/healthz", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(client.getForEntity("/actuator/prometheus", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        var scrape = client.getForEntity("http://localhost:" + managementPort + "/actuator/prometheus",
                String.class);
        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrape.getBody()).contains("jvm_memory_used_bytes",
                "http_server_requests_seconds_count", "http_server_requests_seconds_bucket",
                "application=\"notification-service\"");
        assertThat(client.getForEntity("http://localhost:" + managementPort + "/actuator/env",
                String.class).getStatusCode().is4xxClientError()).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    static class MetricsApplication {
    }
}
