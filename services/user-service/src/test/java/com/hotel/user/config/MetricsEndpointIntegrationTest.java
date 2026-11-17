package com.hotel.user.config;

import com.hotel.user.security.JwtAuthenticationEntryPoint;
import com.hotel.user.security.JwtAuthenticationFilter;
import com.hotel.user.security.InternalServiceAuthenticationFilter;
import com.hotel.user.util.JwtUtil;
import com.hotel.user.util.InternalServiceUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureObservability
@SpringBootTest(classes = MetricsEndpointIntegrationTest.MetricsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false",
        "management.health.rabbit.enabled=false"
})
class MetricsEndpointIntegrationTest {

    @Autowired
    private TestRestTemplate client;

    @LocalManagementPort
    private int managementPort;


    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private InternalServiceUtil internalServiceUtil;

    @Test
    void exposesScrapeAndHistogramOnManagementPortWhileKeepingApplicationProtected() {
        assertThat(client.getForEntity("/healthz", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(client.getForEntity("/actuator/prometheus", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(client.getForEntity("/api/v1/users/me", String.class).getStatusCode().is4xxClientError())
                .isTrue();

        var scrape = client.getForEntity("http://localhost:" + managementPort + "/actuator/prometheus",
                String.class);
        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrape.getBody()).contains("jvm_memory_used_bytes",
                "http_server_requests_seconds_count", "http_server_requests_seconds_bucket",
                "application=\"user-service\"");
        assertThat(client.getForEntity("http://localhost:" + managementPort + "/actuator/env",
                String.class).getStatusCode().is4xxClientError()).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class,
            JwtAuthenticationFilter.class, InternalServiceAuthenticationFilter.class})
    static class MetricsApplication {
    }
}
