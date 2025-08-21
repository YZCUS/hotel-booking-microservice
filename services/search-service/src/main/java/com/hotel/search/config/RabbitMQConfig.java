package com.hotel.search.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class RabbitMQConfig {
    
    // Exchange definitions
    public static final String HOTEL_EXCHANGE = "hotel.exchange";
    public static final String SEARCH_EXCHANGE = "search.exchange";
    
    // Queue definitions
    public static final String HOTEL_CREATED_QUEUE = "hotel.created.queue";
    public static final String HOTEL_UPDATED_QUEUE = "hotel.updated.queue";
    public static final String HOTEL_DELETED_QUEUE = "hotel.deleted.queue";
    public static final String SEARCH_HISTORY_QUEUE = "search.history.queue";
    
    // Routing keys
    public static final String HOTEL_CREATED_KEY = "hotel.created";
    public static final String HOTEL_UPDATED_KEY = "hotel.updated";
    public static final String HOTEL_DELETED_KEY = "hotel.deleted";
    public static final String SEARCH_HISTORY_KEY = "search.history";
    
    // Exchanges
    @Bean
    public TopicExchange hotelExchange() {
        return new TopicExchange(HOTEL_EXCHANGE);
    }
    
    @Bean
    public TopicExchange searchExchange() {
        return new TopicExchange(SEARCH_EXCHANGE);
    }
    
    // Queues for hotel events
    @Bean
    public Queue hotelCreatedQueue() {
        return QueueBuilder.durable(HOTEL_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", HOTEL_EXCHANGE + ".dlx")
                .withArgument("x-dead-letter-routing-key", "hotel.created.dlq")
                .withArgument("x-message-ttl", 3600000) // 1 hour TTL
                .build();
    }
    
    @Bean
    public Queue hotelUpdatedQueue() {
        return QueueBuilder.durable(HOTEL_UPDATED_QUEUE)
                .withArgument("x-dead-letter-exchange", HOTEL_EXCHANGE + ".dlx")
                .withArgument("x-dead-letter-routing-key", "hotel.updated.dlq")
                .withArgument("x-message-ttl", 3600000)
                .build();
    }
    
    @Bean
    public Queue hotelDeletedQueue() {
        return QueueBuilder.durable(HOTEL_DELETED_QUEUE)
                .withArgument("x-dead-letter-exchange", HOTEL_EXCHANGE + ".dlx")
                .withArgument("x-dead-letter-routing-key", "hotel.deleted.dlq")
                .withArgument("x-message-ttl", 3600000)
                .build();
    }
    
    @Bean
    public Queue searchHistoryQueue() {
        return QueueBuilder.durable(SEARCH_HISTORY_QUEUE)
                .withArgument("x-dead-letter-exchange", SEARCH_EXCHANGE + ".dlx")
                .withArgument("x-dead-letter-routing-key", "search.history.dlq")
                .withArgument("x-message-ttl", 7200000) // 2 hours TTL for search history (analytics data)
                .build();
    }
    
    // Bindings
    @Bean
    public Binding hotelCreatedBinding() {
        return BindingBuilder
                .bind(hotelCreatedQueue())
                .to(hotelExchange())
                .with(HOTEL_CREATED_KEY);
    }
    
    @Bean
    public Binding hotelUpdatedBinding() {
        return BindingBuilder
                .bind(hotelUpdatedQueue())
                .to(hotelExchange())
                .with(HOTEL_UPDATED_KEY);
    }
    
    @Bean
    public Binding hotelDeletedBinding() {
        return BindingBuilder
                .bind(hotelDeletedQueue())
                .to(hotelExchange())
                .with(HOTEL_DELETED_KEY);
    }
    
    @Bean
    public Binding searchHistoryBinding() {
        return BindingBuilder
                .bind(searchHistoryQueue())
                .to(searchExchange())
                .with(SEARCH_HISTORY_KEY);
    }
    
    // Dead Letter Queue setup
    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(HOTEL_EXCHANGE + ".dlx");
    }
    
    @Bean
    public FanoutExchange searchDeadLetterExchange() {
        return new FanoutExchange(SEARCH_EXCHANGE + ".dlx");
    }
    
    @Bean
    public Queue hotelCreatedDeadLetterQueue() {
        return new Queue("hotel.created.dlq", true);
    }
    
    @Bean
    public Queue hotelUpdatedDeadLetterQueue() {
        return new Queue("hotel.updated.dlq", true);
    }
    
    @Bean
    public Queue hotelDeletedDeadLetterQueue() {
        return new Queue("hotel.deleted.dlq", true);
    }
    
    @Bean
    public Binding hotelCreatedDLQBinding() {
        return BindingBuilder
                .bind(hotelCreatedDeadLetterQueue())
                .to(deadLetterExchange());
    }
    
    @Bean
    public Binding hotelUpdatedDLQBinding() {
        return BindingBuilder
                .bind(hotelUpdatedDeadLetterQueue())
                .to(deadLetterExchange());
    }
    
    @Bean
    public Binding hotelDeletedDLQBinding() {
        return BindingBuilder
                .bind(hotelDeletedDeadLetterQueue())
                .to(deadLetterExchange());
    }
    
    @Bean
    public Queue searchHistoryDeadLetterQueue() {
        return new Queue("search.history.dlq", true);
    }
    
    @Bean
    public Binding searchHistoryDLQBinding() {
        return BindingBuilder
                .bind(searchHistoryDeadLetterQueue())
                .to(searchDeadLetterExchange());
    }
    
    // Message converter
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}