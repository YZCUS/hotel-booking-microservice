package com.hotel.hotel.config;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String HOTEL_EXCHANGE = "hotel.exchange";
    public static final String HOTEL_CREATED_ROUTING_KEY = "hotel.created";
    public static final String HOTEL_UPDATED_ROUTING_KEY = "hotel.updated";
    public static final String HOTEL_DELETED_ROUTING_KEY = "hotel.deleted";

    @Bean
    public TopicExchange hotelExchange() {
        return new TopicExchange(HOTEL_EXCHANGE);
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
