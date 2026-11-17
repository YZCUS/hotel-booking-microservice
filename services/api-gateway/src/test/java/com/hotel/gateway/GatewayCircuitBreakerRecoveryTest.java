package com.hotel.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.security.jwt.secret=gateway-test-secret-key-with-more-than-64-characters-1234567890abcdef",
        "management.server.port=0",
        "management.health.redis.enabled=false",
        "resilience4j.circuitbreaker.instances.hotelServiceCB.waitDurationInOpenState=500ms",
        "resilience4j.circuitbreaker.instances.bookingServiceCB.waitDurationInOpenState=500ms"
})
class GatewayCircuitBreakerRecoveryTest {

    @Autowired
    private ReactiveResilience4JCircuitBreakerFactory factory;

    @Autowired
    private CircuitBreakerRegistry registry;

    @MockBean
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @ParameterizedTest
    @ValueSource(strings = {"hotelServiceCB", "bookingServiceCB"})
    void automaticallyRecoversAfterDownstreamFailure(String name) throws InterruptedException {
        var reactiveBreaker = factory.create(name);
        CircuitBreaker breaker = registry.circuitBreaker(name);
        CountDownLatch halfOpen = new CountDownLatch(1);
        AtomicBoolean interruptedDuringRecovery = new AtomicBoolean();
        breaker.getEventPublisher().onStateTransition(event -> {
            if (event.getStateTransition().getToState() == CircuitBreaker.State.HALF_OPEN) {
                interruptedDuringRecovery.set(Thread.currentThread().isInterrupted());
                halfOpen.countDown();
            }
        });

        for (int i = 0; i < breaker.getCircuitBreakerConfig().getMinimumNumberOfCalls(); i++) {
            assertEquals("unavailable", reactiveBreaker.run(
                    Mono.<String>error(new IOException("Downstream database unavailable")),
                    error -> Mono.just("unavailable")).block(Duration.ofSeconds(2)));
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        assertTrue(halfOpen.await(5, TimeUnit.SECONDS), "Breaker must automatically leave OPEN");
        assertFalse(interruptedDuringRecovery.get(), "Automatic recovery must not interrupt its own thread");

        for (int i = 0; i < breaker.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState(); i++) {
            assertEquals("recovered", reactiveBreaker.run(Mono.just("recovered"),
                    error -> Mono.just("unavailable")).block(Duration.ofSeconds(2)));
        }
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }
}
