package com.hotel.search.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void deadLetterExchangesUseRoutingKeys() {
        assertThat(config.deadLetterExchange()).isInstanceOf(DirectExchange.class);
        assertThat(config.searchDeadLetterExchange()).isInstanceOf(DirectExchange.class);

        assertThat(config.hotelCreatedDLQBinding().getRoutingKey()).isEqualTo("hotel.created.dlq.v2");
        assertThat(config.hotelUpdatedDLQBinding().getRoutingKey()).isEqualTo("hotel.updated.dlq.v2");
        assertThat(config.hotelDeletedDLQBinding().getRoutingKey()).isEqualTo("hotel.deleted.dlq.v2");
        assertThat(config.searchHistoryDLQBinding().getRoutingKey()).isEqualTo("search.history.dlq.v2");
    }

    @Test
    void consumerBindingsMatchProducerV2Contracts() {
        assertThat(config.hotelCreatedQueue().getName()).isEqualTo("hotel.created.queue.v2");
        assertThat(config.hotelUpdatedQueue().getName()).isEqualTo("hotel.updated.queue.v2");
        assertThat(config.hotelDeletedQueue().getName()).isEqualTo("hotel.deleted.queue.v2");

        assertThat(config.hotelCreatedBinding().getRoutingKey()).isEqualTo("hotel.created.v2");
        assertThat(config.hotelUpdatedBinding().getRoutingKey()).isEqualTo("hotel.updated.v2");
        assertThat(config.hotelDeletedBinding().getRoutingKey()).isEqualTo("hotel.deleted.v2");
    }
}
