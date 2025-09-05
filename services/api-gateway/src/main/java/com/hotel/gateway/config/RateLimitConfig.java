package com.hotel.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RateLimitConfig {
    
    @Bean
    @Primary // Make this bean the default, avoid confusion with other beans (spring autoconfiguration)
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        
        RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
            .<String, String>newSerializationContext(stringSerializer)
            .key(stringSerializer)
            .value(stringSerializer)
            .hashKey(stringSerializer)
            .hashValue(stringSerializer)
            .build();
        
        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}