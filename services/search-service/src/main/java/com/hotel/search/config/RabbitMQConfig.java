package com.hotel.search.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMQConfig {
    
    public static final String HOTEL_CREATED_QUEUE = "hotel.created.queue";
    public static final String HOTEL_UPDATED_QUEUE = "hotel.updated.queue";
    public static final String HOTEL_DELETED_QUEUE = "hotel.deleted.queue";
    
    @Bean
    public Queue hotelCreatedQueue() {
        return QueueBuilder.durable(HOTEL_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", "hotel.dlx")
                .withArgument("x-dead-letter-routing-key", "hotel.created.dlq")
                .build();
    }
    
    @Bean
    public Queue hotelUpdatedQueue() {
        return QueueBuilder.durable(HOTEL_UPDATED_QUEUE)
                .withArgument("x-dead-letter-exchange", "hotel.dlx")
                .withArgument("x-dead-letter-routing-key", "hotel.updated.dlq")
                .build();
    }
    
    @Bean
    public Queue hotelDeletedQueue() {
        return QueueBuilder.durable(HOTEL_DELETED_QUEUE)
                .withArgument("x-dead-letter-exchange", "hotel.dlx")
                .withArgument("x-dead-letter-routing-key", "hotel.deleted.dlq")
                .build();
    }
    
    // Dead letter queues for failed message handling
    @Bean
    public Queue hotelCreatedDeadLetterQueue() {
        return QueueBuilder.durable("hotel.created.dlq").build();
    }
    
    @Bean
    public Queue hotelUpdatedDeadLetterQueue() {
        return QueueBuilder.durable("hotel.updated.dlq").build();
    }
    
    @Bean
    public Queue hotelDeletedDeadLetterQueue() {
        return QueueBuilder.durable("hotel.deleted.dlq").build();
    }
}