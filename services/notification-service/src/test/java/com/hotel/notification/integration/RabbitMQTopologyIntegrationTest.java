package com.hotel.notification.integration;

import com.hotel.notification.config.RabbitMQConfig;
import com.hotel.notification.service.NotificationService.UserRegisteredEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
class RabbitMQTopologyIntegrationTest {

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3-management-alpine");

    private static CachingConnectionFactory connectionFactory;
    private static RabbitTemplate rabbitTemplate;

    @BeforeAll
    static void setUpRabbit() {
        connectionFactory = new CachingConnectionFactory(
                RABBITMQ.getHost(),
                RABBITMQ.getAmqpPort());
        connectionFactory.setUsername(RABBITMQ.getAdminUsername());
        connectionFactory.setPassword(RABBITMQ.getAdminPassword());

        RabbitMQConfig config = new RabbitMQConfig();
        MessageConverter converter = config.jsonMessageConverter();
        AmqpTemplate amqpTemplate = config.amqpTemplate(connectionFactory);
        rabbitTemplate = (RabbitTemplate) amqpTemplate;
        rabbitTemplate.setMessageConverter(converter);

        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        declare(admin, config.bookingExchange(), config.userExchange(),
                config.bookingDeadLetterExchange(), config.userDeadLetterExchange(), config.emailDeadLetterExchange());
        declare(admin, config.bookingCreatedQueue(), config.bookingCancelledQueue(),
                config.userRegisteredQueue(), config.emailQueue(),
                config.bookingCreatedDeadLetterQueue(), config.bookingCancelledDeadLetterQueue(),
                config.userRegisteredDeadLetterQueue(), config.emailDeadLetterQueue());
        declare(admin, config.bookingCreatedBinding(), config.bookingCancelledBinding(),
                config.userRegisteredBinding(), config.bookingCreatedDLQBinding(),
                config.bookingCancelledDLQBinding(), config.userRegisteredDLQBinding(), config.emailDLQBinding());
        admin.purgeQueue(RabbitMQConfig.USER_REGISTERED_QUEUE, true);
    }

    @AfterAll
    static void tearDownRabbit() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void userRegisteredBinding_RoutesJsonEventThroughRabbitMqContainer() {
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setUserId(UUID.randomUUID());
        event.setEmail("integration@example.com");
        event.setFirstName("Integration");
        event.setLastName("User");
        event.setRegisteredAt(LocalDateTime.of(2026, 5, 13, 12, 0));

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.USER_EXCHANGE,
                RabbitMQConfig.USER_REGISTERED_ROUTING_KEY,
                event);

        Object received = rabbitTemplate.receiveAndConvert(RabbitMQConfig.USER_REGISTERED_QUEUE, 5000);

        assertNotNull(received);
        UserRegisteredEvent receivedEvent = (UserRegisteredEvent) received;
        assertEquals(event.getUserId(), receivedEvent.getUserId());
        assertEquals("Integration User", receivedEvent.getFullName());
        assertEquals("integration@example.com", receivedEvent.getEmail());
    }

    private static void declare(RabbitAdmin admin, Exchange... exchanges) {
        for (Exchange exchange : exchanges) {
            admin.declareExchange(exchange);
        }
    }

    private static void declare(RabbitAdmin admin, Queue... queues) {
        for (Queue queue : queues) {
            admin.declareQueue(queue);
        }
    }

    private static void declare(RabbitAdmin admin, Binding... bindings) {
        for (Binding binding : bindings) {
            admin.declareBinding(binding);
        }
    }
}
