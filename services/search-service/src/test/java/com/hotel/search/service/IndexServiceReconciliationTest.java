package com.hotel.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.DocumentsQuery;
import com.meilisearch.sdk.model.Results;
import com.meilisearch.sdk.model.Task;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TaskStatus;
import com.hotel.search.model.HotelDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndexServiceReconciliationTest {

    private Client meilisearchClient;
    private Index index;
    private ObjectMapper objectMapper;
    private Results<HotelDocument> existingDocuments;
    private TaskInfo upsertTask;
    private TaskInfo deleteTask;
    private Task upsertResult;
    private Task deleteResult;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        meilisearchClient = mock(Client.class);
        index = mock(Index.class);
        objectMapper = new ObjectMapper();
        existingDocuments = mock(Results.class);
        upsertTask = mock(TaskInfo.class);
        deleteTask = mock(TaskInfo.class);
        upsertResult = mock(Task.class);
        deleteResult = mock(Task.class);
        when(meilisearchClient.index(IndexService.HOTEL_INDEX)).thenReturn(index);
        when(existingDocuments.getResults()).thenReturn(new HotelDocument[0]);
        when(existingDocuments.getTotal()).thenReturn(0);
        when(index.getDocuments(any(DocumentsQuery.class), eq(HotelDocument.class)))
                .thenReturn(existingDocuments);
        when(index.addDocuments(anyString())).thenReturn(upsertTask);
        when(upsertTask.getTaskUid()).thenReturn(101);
        when(index.getTask(101)).thenReturn(upsertResult);
        when(upsertResult.getStatus()).thenReturn(TaskStatus.SUCCEEDED);
        when(index.deleteDocuments(any())).thenReturn(deleteTask);
        when(deleteTask.getTaskUid()).thenReturn(202);
        when(index.getTask(202)).thenReturn(deleteResult);
        when(deleteResult.getStatus()).thenReturn(TaskStatus.SUCCEEDED);
    }

    @Test
    void syncWithHotelService_UpsertsProjectionFromAuthenticatedExportContract() throws Exception {
        AtomicReference<ClientRequest> request = new AtomicReference<>();
        String responseBody = """
                {"content":[{
                    "id":"550e8400-e29b-41d4-a716-446655440001",
                    "name":"Grand Hotel",
                    "isActive":true,
                    "roomTypes":[
                      {"id":"660e8400-e29b-41d4-a716-446655440001","pricePerNight":120.00,"totalInventory":20,"availableRooms":5},
                      {"id":"660e8400-e29b-41d4-a716-446655440002","pricePerNight":300.00,"totalInventory":5,"availableRooms":2}
                    ]
                  }]}
                """;
        IndexService service = configuredService(webClientReturning(responseBody, request));
        long firstPossibleMinute = Instant.now().getEpochSecond() / 60;

        service.syncWithHotelService();

        long lastPossibleMinute = Instant.now().getEpochSecond() / 60;
        assertThat(request.get().url()).hasPath("/api/v1/hotels/export");
        assertThat(request.get().headers().getFirst("X-Internal-Service")).isEqualTo("search-service");
        assertThat(request.get().headers().getFirst("X-Internal-Token"))
                .isIn(LongStream.rangeClosed(firstPossibleMinute, lastPossibleMinute)
                        .mapToObj(this::expectedInternalToken)
                        .toList());
        verify(index, never()).deleteAllDocuments();
        ArgumentCaptor<String> documents = ArgumentCaptor.forClass(String.class);
        verify(index).addDocuments(documents.capture());
        verify(index).waitForTask(101);
        var indexedHotel = objectMapper.readTree(documents.getValue()).get(0);
        assertThat(indexedHotel.get("minPrice").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(indexedHotel.get("maxPrice").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(indexedHotel.get("totalRooms").intValue()).isEqualTo(25);
        assertThat(indexedHotel.get("availableRooms").intValue()).isEqualTo(7);
        assertThat(indexedHotel.get("roomTypes")).hasSize(2);
    }

    @Test
    void syncWithHotelService_PreservesUnknownAvailability() throws Exception {
        String responseBody = """
                [{
                    "id":"550e8400-e29b-41d4-a716-446655440001",
                    "name":"Grand Hotel",
                    "availableRooms":null,
                    "roomTypes":[
                      {"id":"660e8400-e29b-41d4-a716-446655440001","totalInventory":20,"availableRooms":null},
                      {"id":"660e8400-e29b-41d4-a716-446655440002","totalInventory":5,"availableRooms":null}
                    ]
                  }]
                """;
        IndexService service = configuredService(webClientReturning(responseBody, new AtomicReference<>()));

        service.syncWithHotelService();

        ArgumentCaptor<String> documents = ArgumentCaptor.forClass(String.class);
        verify(index).addDocuments(documents.capture());
        var indexedHotel = objectMapper.readTree(documents.getValue()).get(0);
        assertThat(indexedHotel.get("totalRooms").intValue()).isEqualTo(25);
        assertThat(indexedHotel.get("availableRooms").isNull()).isTrue();
    }

    @Test
    void syncWithHotelService_DeletesOnlyStaleDocumentsAfterSuccessfulSnapshot() throws Exception {
        UUID staleHotelId = UUID.randomUUID();
        HotelDocument staleHotel = HotelDocument.builder().id(staleHotelId).build();
        when(existingDocuments.getResults()).thenReturn(new HotelDocument[]{staleHotel});
        when(existingDocuments.getTotal()).thenReturn(1);
        IndexService service = configuredService(webClientReturning("[]", new AtomicReference<>()));

        service.syncWithHotelService();

        verify(index, never()).deleteAllDocuments();
        verify(index, never()).addDocuments(anyString());
        verify(index).deleteDocuments(List.of(staleHotelId.toString()));
        verify(index).waitForTask(202);
    }

    @Test
    void syncWithHotelService_FailedUpsertDoesNotDeleteExistingDocuments() throws Exception {
        UUID existingHotelId = UUID.randomUUID();
        HotelDocument existingHotel = HotelDocument.builder().id(existingHotelId).build();
        when(existingDocuments.getResults()).thenReturn(new HotelDocument[]{existingHotel});
        when(existingDocuments.getTotal()).thenReturn(1);
        when(upsertResult.getStatus()).thenReturn(TaskStatus.FAILED);
        String responseBody = """
                [{"id":"550e8400-e29b-41d4-a716-446655440001","name":"Grand Hotel"}]
                """;
        IndexService service = configuredService(webClientReturning(responseBody, new AtomicReference<>()));

        service.syncWithHotelService();

        verify(index, never()).deleteAllDocuments();
        verify(index, never()).deleteDocuments(any());
    }

    private String expectedInternalToken(long epochMinute) {
        try {
            String payload = "search-service:test-secret:" + epochMinute;
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest).substring(0, 32);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private IndexService configuredService(WebClient.Builder webClientBuilder) {
        IndexService service = new IndexService(meilisearchClient, objectMapper);
        ReflectionTestUtils.setField(service, "webClientBuilder", webClientBuilder);
        ReflectionTestUtils.setField(service, "hotelServiceUrl", "http://hotel-service:8082");
        ReflectionTestUtils.setField(service, "serviceName", "search-service");
        ReflectionTestUtils.setField(service, "serviceSecret", "test-secret");
        ReflectionTestUtils.setField(service, "serviceHeader", "X-Internal-Service");
        ReflectionTestUtils.setField(service, "tokenHeader", "X-Internal-Token");
        return service;
    }

    private WebClient.Builder webClientReturning(
            String responseBody,
            AtomicReference<ClientRequest> requestedRequest) {
        return WebClient.builder().exchangeFunction(request -> {
            requestedRequest.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(responseBody)
                    .build());
        });
    }
}
