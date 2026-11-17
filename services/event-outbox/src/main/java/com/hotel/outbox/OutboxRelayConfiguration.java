package com.hotel.outbox;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration(after = {OutboxConfiguration.class, RabbitAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
// Let JPA choose its transaction manager before the late JDBC fallback is evaluated.
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnBean({OutboxStore.class, RabbitTemplate.class, PlatformTransactionManager.class})
@EnableScheduling
public class OutboxRelayConfiguration {

    @Bean
    OutboxRelay outboxRelay(
            OutboxStore outboxStore,
            RabbitTemplate rabbitTemplate,
            PlatformTransactionManager transactionManager,
            @Value("${app.outbox.batch-size:50}") int batchSize,
            @Value("${app.outbox.confirm-timeout:5s}") String confirmTimeout,
            @Value("${app.outbox.max-backoff:5m}") String maxBackoff,
            @Value("${app.outbox.retention:7d}") String retention) {
        rabbitTemplate.setMandatory(true);
        return new OutboxRelay(
                outboxStore,
                rabbitTemplate,
                transactionManager,
                batchSize,
                DurationStyle.detectAndParse(confirmTimeout),
                DurationStyle.detectAndParse(maxBackoff),
                DurationStyle.detectAndParse(retention));
    }
}
