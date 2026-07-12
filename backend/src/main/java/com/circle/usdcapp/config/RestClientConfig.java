package com.circle.usdcapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient circleRestClient(CircleProperties circleProperties) {
        return RestClient.builder()
                .baseUrl(circleProperties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + circleProperties.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
