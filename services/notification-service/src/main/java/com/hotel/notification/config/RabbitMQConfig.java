package com.hotel.notification.config;

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
    
    public static final String BOOKING_CREATED_QUEUE = "booking.created.queue";
    public static final String BOOKING_CANCELLED_QUEUE = "booking.cancelled.queue";
    public static final String USER_REGISTERED_QUEUE = "user.registered.queue";
    
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String EMAIL_QUEUE = "email.queue";
    
    // Routing keys
    public static final String BOOKING_CREATED_ROUTING_KEY = "booking.created";
    public static final String BOOKING_CANCELLED_ROUTING_KEY = "booking.cancelled";
    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";
    
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
                .withArgument("x-dead-letter-exchange", BOOKING_EXCHANGE + ".dlx")
                .withArgument("x-dead-letter-routing-key", "booking.created.dlq")
                .withArgument("x-message-ttl", 1800000) // 30 minutes TTL for booking notifications
                .build();
    }
    
    @Bean
    public Queue bookingCancelledQueue() {
        return QueueBuilder.durable(BOOKING_CANCELLED_QUEUE)
                .withArgument("x-dead-letter-exchange", BOOKING_EXCHANGE + ".dlx")
                .withArgument("x-dead-letter-routing-key", "booking.cancelled.dlq")
                .withArgument("x-message-ttl", 1800000) // 30 minutes TTL for booking notifications
                .build();
    }
    
    @Bean
    public Queue userRegisteredQueue() {
        return QueueBuilder.durable(USER_REGISTERED_QUEUE)
                .withArgument("x-dead-letter-exchange", USER_EXCHANGE + ".dlx")
                .withArgument("x-dead-letter-routing-key", "user.registered.dlq")
                .withArgument("x-message-ttl", 7200000) // 2 hours TTL for welcome emails (less critical)
                .build();
    }
    
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", "email.exchange.dlx")
                .withArgument("x-dead-letter-routing-key", "email.dlq")
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
        return new Jackson2JsonMessageConverter();
    }
    
    // Dead Letter Queue setup
    @Bean
    public FanoutExchange bookingDeadLetterExchange() {
        return new FanoutExchange(BOOKING_EXCHANGE + ".dlx");
    }
    
    @Bean
    public FanoutExchange userDeadLetterExchange() {
        return new FanoutExchange(USER_EXCHANGE + ".dlx");
    }
    
    @Bean
    public FanoutExchange emailDeadLetterExchange() {
        return new FanoutExchange("email.exchange.dlx");
    }
    
    @Bean
    public Queue bookingCreatedDeadLetterQueue() {
        return new Queue("booking.created.dlq", true);
    }
    
    @Bean
    public Queue bookingCancelledDeadLetterQueue() {
        return new Queue("booking.cancelled.dlq", true);
    }
    
    @Bean
    public Queue userRegisteredDeadLetterQueue() {
        return new Queue("user.registered.dlq", true);
    }
    
    @Bean
    public Queue emailDeadLetterQueue() {
        return new Queue("email.dlq", true);
    }
    
    // Dead Letter Queue Bindings
    @Bean
    public Binding bookingCreatedDLQBinding() {
        return BindingBuilder
                .bind(bookingCreatedDeadLetterQueue())
                .to(bookingDeadLetterExchange());
    }
    
    @Bean
    public Binding bookingCancelledDLQBinding() {
        return BindingBuilder
                .bind(bookingCancelledDeadLetterQueue())
                .to(bookingDeadLetterExchange());
    }
    
    @Bean
    public Binding userRegisteredDLQBinding() {
        return BindingBuilder
                .bind(userRegisteredDeadLetterQueue())
                .to(userDeadLetterExchange());
    }
    
    @Bean
    public Binding emailDLQBinding() {
        return BindingBuilder
                .bind(emailDeadLetterQueue())
                .to(emailDeadLetterExchange());
    }
    
    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}