package com.circle.usdcapp.client;

import com.circle.usdcapp.exception.CircleApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Thin wrapper around the Circle REST API (https://developers.circle.com).
 * Centralizes auth headers, JSON handling and error translation so service
 * classes can focus on business logic.
 */
@Component
public class CircleApiClient {

    private static final Logger log = LoggerFactory.getLogger(CircleApiClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CircleApiClient(RestClient circleRestClient, ObjectMapper objectMapper) {
        this.restClient = circleRestClient;
        this.objectMapper = objectMapper;
    }

    public JsonNode get(String path) {
        return execute(restClient.get().uri(path));
    }

    public JsonNode post(String path, Map<String, Object> body) {
        return execute(restClient.post().uri(path).body(body));
    }

    private JsonNode execute(RestClient.RequestHeadersSpec<?> requestSpec) {
        try {
            String raw = requestSpec.retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String errorBody = new String(res.getBody().readAllBytes());
                        log.warn("Circle API error {} {}: {}", res.getStatusCode().value(), req.getURI(), errorBody);
                        String code = "unknown";
                        String message = errorBody;
                        try {
                            JsonNode node = new ObjectMapper().readTree(errorBody);
                            if (node.has("code")) code = node.get("code").asText();
                            if (node.has("message")) message = node.get("message").asText();
                        } catch (Exception ignored) {
                            // fall back to raw body
                        }
                        throw new CircleApiException(res.getStatusCode().value(), code, message);
                    })
                    .body(String.class);
            return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        } catch (CircleApiException e) {
            throw e;
        } catch (Exception e) {
            throw new CircleApiException("Failed to call Circle API: " + e.getMessage(), e);
        }
    }
}
