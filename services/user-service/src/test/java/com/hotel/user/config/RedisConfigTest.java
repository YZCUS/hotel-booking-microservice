package com.hotel.user.config;

import com.hotel.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    private final RedisConfig config = new RedisConfig();
    private final RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

    @Test
    void userResponseRoundTripsThroughTemplateValues() {
        var template = config.redisTemplate(connectionFactory);
        assertUserRoundTrip(SerializationPair.fromSerializer(template.getValueSerializer()));
        assertUserRoundTrip(SerializationPair.fromSerializer(template.getHashValueSerializer()));
    }

    @Test
    void userResponseRoundTripsThroughCacheManager() {
        var manager = config.cacheManager(connectionFactory);
        manager.afterPropertiesSet();
        var cache = (RedisCache) manager.getCache("users");
        assertUserRoundTrip(cache.getCacheConfiguration().getValueSerializationPair());
    }

    private void assertUserRoundTrip(SerializationPair<?> pair) {
        var response = UserResponse.builder()
                .id(UUID.randomUUID())
                .email("cache-test@example.invalid")
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5))
                .updatedAt(LocalDateTime.of(2026, 1, 3, 4, 5, 6))
                .build();
        @SuppressWarnings("unchecked")
        var objectPair = (SerializationPair<Object>) pair;
        Object restored = objectPair.read(objectPair.write(response));
        assertThat(restored).isInstanceOf(UserResponse.class).isEqualTo(response);
    }
}
