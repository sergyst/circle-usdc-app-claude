package com.circle.usdcapp.client;

import com.circle.usdcapp.exception.CircleApiException;
import com.circle.usdcapp.exception.CircleTransientException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Thin wrapper around the Circle REST API (https://developers.circle.com).
 * Centralizes auth headers, JSON handling and error translation so service
 * classes can focus on business logic.
 *
 * <p><b>Resilience.</b> Both methods run behind a Resilience4j circuit breaker
 * ("circleApi") so a sustained Circle outage fails fast instead of piling up
 * blocked threads. Only {@link #get(String)} additionally retries, and only on
 * {@link CircleTransientException} (network/timeout or 5xx). {@link #post} is
 * deliberately NOT retried: Circle mutating calls carry a single-use entity
 * secret ciphertext / idempotency key, so a blind retry risks either a replay
 * rejection or a double execution. Retrying reads is safe; retrying writes is
 * the caller's decision.
 */
@Component
@Slf4j
public class CircleApiClient {

    private static final String RESILIENCE_INSTANCE = "circleApi";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CircleApiClient(RestClient circleRestClient, ObjectMapper objectMapper) {
        this.restClient = circleRestClient;
        this.objectMapper = objectMapper;
    }

    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public JsonNode get(String path) {
        return execute(restClient.get().uri(path));
    }

    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    public JsonNode post(String path, Map<String, Object> body) {
        return execute(restClient.post().uri(path).body(body));
    }

    private JsonNode execute(RestClient.RequestHeadersSpec<?> requestSpec) {
        try {
            String raw = requestSpec.retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        int status = res.getStatusCode().value();
                        String errorBody = new String(res.getBody().readAllBytes());
                        log.warn("Circle API error {} {}: {}", status, req.getURI(), errorBody);
                        String code = "unknown";
                        String message = errorBody;
                        try {
                            JsonNode node = objectMapper.readTree(errorBody);
                            if (node.has("code")) code = node.get("code").asText();
                            if (node.has("message")) message = node.get("message").asText();
                        } catch (Exception ignored) {
                            // fall back to raw body
                        }
                        // 5xx = Circle-side/transient -> retryable; 4xx = client
                        // error -> fail fast (won't succeed on retry).
                        if (res.getStatusCode().is5xxServerError()) {
                            throw new CircleTransientException(status, code, message);
                        }
                        throw new CircleApiException(status, code, message);
                    })
                    .body(String.class);
            return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        } catch (CircleApiException e) {
            // Already classified (includes CircleTransientException) - propagate as-is.
            throw e;
        } catch (ResourceAccessException e) {
            // Connect/read timeout or dropped connection - worth retrying.
            throw new CircleTransientException("Circle API connection error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new CircleApiException("Failed to call Circle API: " + e.getMessage(), e);
        }
    }
}
