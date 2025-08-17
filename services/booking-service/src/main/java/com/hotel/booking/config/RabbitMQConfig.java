package com.hotel.booking.config;

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
    
    public static final String BOOKING_EXCHANGE = "booking.exchange";
    public static final String BOOKING_CREATED_QUEUE = "booking.created.queue";
    public static final String BOOKING_CANCELLED_QUEUE = "booking.cancelled.queue";
    public static final String BOOKING_CREATED_ROUTING_KEY = "booking.created";
    public static final String BOOKING_CANCELLED_ROUTING_KEY = "booking.cancelled";
    
    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE);
    }
    
    @Bean
    public Queue bookingCreatedQueue() {
        return QueueBuilder.durable(BOOKING_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", BOOKING_EXCHANGE + ".dlx")
                .withArgument("x-dead-letter-routing-key", "booking.created.dlq")
                .build();
    }
    
    @Bean
    public Queue bookingCancelledQueue() {
        return QueueBuilder.durable(BOOKING_CANCELLED_QUEUE)
                .withArgument("x-dead-letter-exchange", BOOKING_EXCHANGE + ".dlx")
                .withArgument("x-dead-letter-routing-key", "booking.cancelled.dlq")
                .build();
    }
    
    @Bean
    public Binding bookingCreatedBinding() {
        return BindingBuilder
                .bind(bookingCreatedQueue())
                .to(bookingExchange())
                .with(BOOKING_CREATED_ROUTING_KEY);
    }
    
    @Bean
    public Binding bookingCancelledBinding() {
        return BindingBuilder
                .bind(bookingCancelledQueue())
                .to(bookingExchange())
                .with(BOOKING_CANCELLED_ROUTING_KEY);
    }
    
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