package com.hotel.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
    public static final String USER_EXCHANGE = "user.exchange";
    
    public static final String BOOKING_CREATED_QUEUE = "booking.created.queue.v2";
    public static final String BOOKING_CANCELLED_QUEUE = "booking.cancelled.queue.v2";
    public static final String USER_REGISTERED_QUEUE = "user.registered.queue.v2";
    
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String EMAIL_QUEUE = "email.queue.v2";
    
    // Routing keys
    public static final String BOOKING_CREATED_ROUTING_KEY = "booking.created.v2";
    public static final String BOOKING_CANCELLED_ROUTING_KEY = "booking.cancelled.v2";
    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered.v2";

    public static final String BOOKING_DLX = BOOKING_EXCHANGE + ".dlx.v2";
    public static final String USER_DLX = USER_EXCHANGE + ".dlx.v2";
    public static final String EMAIL_DLX = "email.exchange.dlx.v2";
    public static final String BOOKING_CREATED_DLQ = "booking.created.dlq.v2";
    public static final String BOOKING_CANCELLED_DLQ = "booking.cancelled.dlq.v2";
    public static final String USER_REGISTERED_DLQ = "user.registered.dlq.v2";
    public static final String EMAIL_DLQ = "email.dlq.v2";
    
    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE);
    }
    
    @Bean
    public TopicExchange userExchange() {
        return new TopicExchange(USER_EXCHANGE);
    }
    
    @Bean
    public Queue bookingCreatedQueue() {
        return QueueBuilder.durable(BOOKING_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", BOOKING_DLX)
                .withArgument("x-dead-letter-routing-key", BOOKING_CREATED_DLQ)
                .withArgument("x-message-ttl", 1800000) // 30 minutes TTL for booking notifications
                .build();
    }
    
    @Bean
    public Queue bookingCancelledQueue() {
        return QueueBuilder.durable(BOOKING_CANCELLED_QUEUE)
                .withArgument("x-dead-letter-exchange", BOOKING_DLX)
                .withArgument("x-dead-letter-routing-key", BOOKING_CANCELLED_DLQ)
                .withArgument("x-message-ttl", 1800000) // 30 minutes TTL for booking notifications
                .build();
    }
    
    @Bean
    public Queue userRegisteredQueue() {
        return QueueBuilder.durable(USER_REGISTERED_QUEUE)
                .withArgument("x-dead-letter-exchange", USER_DLX)
                .withArgument("x-dead-letter-routing-key", USER_REGISTERED_DLQ)
                .withArgument("x-message-ttl", 7200000) // 2 hours TTL for welcome emails (less critical)
                .build();
    }
    
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", EMAIL_DLX)
                .withArgument("x-dead-letter-routing-key", EMAIL_DLQ)
                .withArgument("x-message-ttl", 3600000) // 1 hour TTL for general emails
                .build();
    }
    
    // Bindings - 添加缺少的隊列綁定
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
    public Binding userRegisteredBinding() {
        return BindingBuilder
                .bind(userRegisteredQueue())
                .to(userExchange())
                .with(USER_REGISTERED_ROUTING_KEY);
    }
    
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }
    
    // Dead Letter Queue setup
    @Bean
    public DirectExchange bookingDeadLetterExchange() {
        return new DirectExchange(BOOKING_DLX);
    }
    
    @Bean
    public DirectExchange userDeadLetterExchange() {
        return new DirectExchange(USER_DLX);
    }
    
    @Bean
    public DirectExchange emailDeadLetterExchange() {
        return new DirectExchange(EMAIL_DLX);
    }
    
    @Bean
    public Queue bookingCreatedDeadLetterQueue() {
        return new Queue(BOOKING_CREATED_DLQ, true);
    }
    
    @Bean
    public Queue bookingCancelledDeadLetterQueue() {
        return new Queue(BOOKING_CANCELLED_DLQ, true);
    }
    
    @Bean
    public Queue userRegisteredDeadLetterQueue() {
        return new Queue(USER_REGISTERED_DLQ, true);
    }
    
    @Bean
    public Queue emailDeadLetterQueue() {
        return new Queue(EMAIL_DLQ, true);
    }
    
    // Dead Letter Queue Bindings
    @Bean
    public Binding bookingCreatedDLQBinding() {
        return BindingBuilder
                .bind(bookingCreatedDeadLetterQueue())
                .to(bookingDeadLetterExchange())
                .with(BOOKING_CREATED_DLQ);
    }
    
    @Bean
    public Binding bookingCancelledDLQBinding() {
        return BindingBuilder
                .bind(bookingCancelledDeadLetterQueue())
                .to(bookingDeadLetterExchange())
                .with(BOOKING_CANCELLED_DLQ);
    }
    
    @Bean
    public Binding userRegisteredDLQBinding() {
        return BindingBuilder
                .bind(userRegisteredDeadLetterQueue())
                .to(userDeadLetterExchange())
                .with(USER_REGISTERED_DLQ);
    }
    
    @Bean
    public Binding emailDLQBinding() {
        return BindingBuilder
                .bind(emailDeadLetterQueue())
                .to(emailDeadLetterExchange())
                .with(EMAIL_DLQ);
    }
    
    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
