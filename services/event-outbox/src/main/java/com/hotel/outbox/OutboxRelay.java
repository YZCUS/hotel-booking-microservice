package com.hotel.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
public class OutboxRelay {

    private final OutboxStore outboxStore;
    private final RabbitTemplate rabbitTemplate;
    private final int batchSize;
    private final Duration confirmTimeout;
    private final Duration maxBackoff;
    private final Duration retention;
    private final TransactionTemplate transactionTemplate;

    OutboxRelay(
            OutboxStore outboxStore,
            RabbitTemplate rabbitTemplate,
            PlatformTransactionManager transactionManager,
            int batchSize,
            Duration confirmTimeout,
            Duration maxBackoff,
            Duration retention) {
        this.outboxStore = outboxStore;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.confirmTimeout = confirmTimeout;
        this.maxBackoff = maxBackoff;
        this.retention = retention;
    }

    @Scheduled(
            initialDelayString = "${app.outbox.initial-delay-ms:1000}",
            fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        for (int publishedCount = 0; publishedCount < batchSize; publishedCount++) {
            PublishResult result = transactionTemplate.execute(status -> publishNext());
            if (result != PublishResult.PUBLISHED) {
                return;
            }
        }
    }

    private PublishResult publishNext() {
        var pending = outboxStore.lockPending(1);
        if (pending.isEmpty()) {
            return PublishResult.EMPTY;
        }
        return publish(pending.getFirst()) ? PublishResult.PUBLISHED : PublishResult.FAILED;
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanupPublished() {
        outboxStore.deletePublishedBefore(LocalDateTime.now().minus(retention));
    }

    private boolean publish(OutboxStore.PendingOutboxEvent event) {
        CorrelationData correlationData = new CorrelationData(event.id().toString());
        try {
            rabbitTemplate.send(
                    event.exchange(),
                    event.routingKey(),
                    toMessage(event),
                    correlationData);

            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("Broker NACK: " + confirm.getReason());
            }
            if (correlationData.getReturned() != null) {
                throw new IllegalStateException(
                        "Message was unroutable: " + correlationData.getReturned().getReplyText());
            }

            outboxStore.markPublished(event.id(), LocalDateTime.now());
            log.debug("Published outbox event {} ({})", event.id(), event.eventType());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(event, e);
            return false;
        } catch (Exception e) {
            markFailed(event, e);
            return false;
        }
    }

    private Message toMessage(OutboxStore.PendingOutboxEvent event) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(event.id().toString());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setHeader("eventType", event.eventType());
        properties.setHeader("eventId", event.id().toString());
        return new Message(event.payload().getBytes(StandardCharsets.UTF_8), properties);
    }

    private void markFailed(OutboxStore.PendingOutboxEvent event, Exception failure) {
        int attempts = event.attempts() + 1;
        long exponentialSeconds = 1L << Math.min(attempts, 20);
        Duration backoff = Duration.ofSeconds(exponentialSeconds).compareTo(maxBackoff) > 0
                ? maxBackoff
                : Duration.ofSeconds(exponentialSeconds);
        outboxStore.markFailed(
                event.id(), attempts, LocalDateTime.now().plus(backoff), failure.getMessage());
        log.warn("Outbox event {} publication failed; retry {} scheduled in {}",
                event.id(), attempts, backoff, failure);
    }

    private enum PublishResult {
        PUBLISHED,
        FAILED,
        EMPTY
    }
}
