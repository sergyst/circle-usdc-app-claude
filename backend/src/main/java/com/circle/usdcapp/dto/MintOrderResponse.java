package com.circle.usdcapp.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MintOrderResponse(
        String id,
        String type,
        String walletId,
        BigDecimal amountUsd,
        String status,
        String referenceId,
        boolean live,
        String notes,
        Instant createdAt
) {
}
