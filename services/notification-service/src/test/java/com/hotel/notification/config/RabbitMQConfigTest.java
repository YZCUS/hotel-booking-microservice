package com.hotel.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void deadLetterExchangesUseRoutingKeys() {
        assertThat(config.bookingDeadLetterExchange()).isInstanceOf(DirectExchange.class);
        assertThat(config.userDeadLetterExchange()).isInstanceOf(DirectExchange.class);
        assertThat(config.emailDeadLetterExchange()).isInstanceOf(DirectExchange.class);

        assertThat(config.bookingCreatedDLQBinding().getRoutingKey()).isEqualTo("booking.created.dlq.v2");
        assertThat(config.bookingCancelledDLQBinding().getRoutingKey()).isEqualTo("booking.cancelled.dlq.v2");
        assertThat(config.userRegisteredDLQBinding().getRoutingKey()).isEqualTo("user.registered.dlq.v2");
        assertThat(config.emailDLQBinding().getRoutingKey()).isEqualTo("email.dlq.v2");
    }

    @Test
    void consumerBindingsMatchProducerV2Contracts() {
        assertThat(config.bookingCreatedQueue().getName()).isEqualTo("booking.created.queue.v2");
        assertThat(config.bookingCancelledQueue().getName()).isEqualTo("booking.cancelled.queue.v2");
        assertThat(config.userRegisteredQueue().getName()).isEqualTo("user.registered.queue.v2");

        assertThat(config.bookingCreatedBinding().getRoutingKey()).isEqualTo("booking.created.v2");
        assertThat(config.bookingCancelledBinding().getRoutingKey()).isEqualTo("booking.cancelled.v2");
        assertThat(config.userRegisteredBinding().getRoutingKey()).isEqualTo("user.registered.v2");
    }
}
