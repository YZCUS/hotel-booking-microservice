package com.hotel.outbox;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    @Test
    void publishPendingMarksEventOnlyAfterBrokerAck() {
        OutboxStore store = mock(OutboxStore.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        OutboxRelay relay = relay(store, rabbitTemplate);
        OutboxStore.PendingOutboxEvent event = pendingEvent();
        when(store.lockPending(1)).thenReturn(List.of(event), List.of());
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(
                eq(event.exchange()), eq(event.routingKey()), any(Message.class), any(CorrelationData.class));

        relay.publishPending();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(event.exchange()),
                eq(event.routingKey()),
                messageCaptor.capture(),
                any(CorrelationData.class));
        assertThat(messageCaptor.getValue().getMessageProperties().getMessageId())
                .isEqualTo(event.id().toString());
        assertThat(messageCaptor.getValue().getMessageProperties().getHeader("eventType").toString())
                .isEqualTo("booking.created.v1");
        verify(store).markPublished(eq(event.id()), any(LocalDateTime.class));
        verify(store, never()).markFailed(any(), any(Integer.class), any(), any());
    }

    @Test
    void publishPendingKeepsEventPendingAfterBrokerNack() {
        OutboxStore store = mock(OutboxStore.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        OutboxRelay relay = relay(store, rabbitTemplate);
        OutboxStore.PendingOutboxEvent event = pendingEvent();
        when(store.lockPending(1)).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(
                    new CorrelationData.Confirm(false, "broker unavailable"));
            return null;
        }).when(rabbitTemplate).send(
                eq(event.exchange()), eq(event.routingKey()), any(Message.class), any(CorrelationData.class));

        relay.publishPending();

        verify(store, never()).markPublished(any(), any());
        verify(store).markFailed(
                eq(event.id()), eq(1), any(LocalDateTime.class), eq("Broker NACK: broker unavailable"));
    }

    private OutboxRelay relay(OutboxStore store, RabbitTemplate rabbitTemplate) {
        return new OutboxRelay(
                store,
                rabbitTemplate,
                transactionManager(),
                10,
                Duration.ofSeconds(1),
                Duration.ofMinutes(5),
                Duration.ofDays(7));
    }

    @Test
    void publishPendingCommitsEachAcknowledgedEventInItsOwnTransaction() {
        OutboxStore store = mock(OutboxStore.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        PlatformTransactionManager transactionManager = transactionManager();
        OutboxRelay relay = new OutboxRelay(
                store,
                rabbitTemplate,
                transactionManager,
                10,
                Duration.ofSeconds(1),
                Duration.ofMinutes(5),
                Duration.ofDays(7));
        OutboxStore.PendingOutboxEvent first = pendingEvent();
        OutboxStore.PendingOutboxEvent second = pendingEvent();
        when(store.lockPending(1))
                .thenReturn(List.of(first), List.of(second), List.of());
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));

        relay.publishPending();

        verify(store).markPublished(eq(first.id()), any(LocalDateTime.class));
        verify(store).markPublished(eq(second.id()), any(LocalDateTime.class));
        verify(transactionManager, times(3)).commit(any(TransactionStatus.class));
    }

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return transactionManager;
    }

    private OutboxStore.PendingOutboxEvent pendingEvent() {
        return new OutboxStore.PendingOutboxEvent(
                UUID.randomUUID(),
                "booking.exchange",
                "booking.created",
                "booking.created.v1",
                "{\"bookingId\":\"booking-1\"}",
                0);
    }
}
