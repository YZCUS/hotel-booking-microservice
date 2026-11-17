package com.hotel.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
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

    @Test
    void configuresScheduledRelayAfterJdbcTransactionManager() {
        autoConfiguredContextRunner().run(context -> {
            assertThat(context).hasSingleBean(DataSourceTransactionManager.class);
            assertThat(context).hasSingleBean(OutboxRelay.class);
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks())
                    .hasSize(2);
        });
    }

    @Test
    void configuresScheduledRelayAfterJpaTransactionManager() {
        autoConfiguredContextRunner()
                .withConfiguration(AutoConfigurations.of(HibernateJpaAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(JpaTransactionManager.class);
                    assertThat(context).hasSingleBean(OutboxRelay.class);
                    assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks())
                            .hasSize(2);
                });
    }

    private ApplicationContextRunner autoConfiguredContextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(TestApplication.class)
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        JdbcTemplateAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class,
                        OutboxConfiguration.class,
                        OutboxRelayConfiguration.class))
                .withPropertyValues("app.outbox.schema=public",
                        "app.outbox.initial-delay-ms=3600000",
                        "spring.datasource.generate-unique-name=true")
                .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new);
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage
    static class TestApplication {
    }
}
