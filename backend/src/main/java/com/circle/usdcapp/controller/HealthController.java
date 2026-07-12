package com.circle.usdcapp.controller;

import com.circle.usdcapp.config.CircleProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final CircleProperties circleProperties;

    public HealthController(CircleProperties circleProperties) {
        this.circleProperties = circleProperties;
    }

    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "blockchain", circleProperties.getBlockchain(),
                "mintLive", circleProperties.getMint().isLive()
        );
    }
}
