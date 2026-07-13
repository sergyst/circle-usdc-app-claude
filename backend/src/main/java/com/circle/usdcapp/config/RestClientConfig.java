package com.circle.usdcapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    // Without explicit timeouts a stalled Circle connection would hang the
    // calling thread indefinitely, tying up the request-handling pool. These
    // bound how long we wait to establish a connection and to read a response.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    @Bean
    public RestClient circleRestClient(CircleProperties circleProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(circleProperties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + circleProperties.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
