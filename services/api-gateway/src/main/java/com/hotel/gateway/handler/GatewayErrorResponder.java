package com.hotel.gateway.handler;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

@Component
public class GatewayErrorResponder {
    public Mono<Void> createErrorResponse(ServerWebExchange exchange, HttpStatus httpStatus, String message) {
        return createErrorResponse(exchange, httpStatus, message, Map.of());
    }

    public Mono<Void> createErrorResponse(ServerWebExchange exchange, HttpStatus httpStatus, String message, Map<String, String> additionalHeaders) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // add additional headers
        additionalHeaders.forEach(response.getHeaders()::add);

        String errorResponse = String.format(
                "{\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                httpStatus.getReasonPhrase(),
                message,
                Instant.now().toString()
        );

        byte[] bytes = errorResponse.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
