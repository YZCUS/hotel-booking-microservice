package com.hotel.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OutboxAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OutboxConfiguration.class, OutboxRelayConfiguration.class))
            .withPropertyValues("app.outbox.schema=booking_svc")
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void configuresTransactionalStoreAndConfirmedRelay() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(OutboxService.class);
            assertThat(context).hasSingleBean(OutboxRelay.class);
        });
    }
}
