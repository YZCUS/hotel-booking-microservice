package com.hotel.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(after = {DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@ConditionalOnBean(JdbcTemplate.class)
public class OutboxConfiguration {

    @Bean
    OutboxStore outboxStore(
            JdbcTemplate jdbcTemplate,
            @Value("${app.outbox.schema}") String schema) {
        return new OutboxStore(jdbcTemplate, schema);
    }

    @Bean
    public OutboxService outboxService(OutboxStore outboxStore, ObjectMapper objectMapper) {
        return new OutboxService(outboxStore, objectMapper);
    }

}
