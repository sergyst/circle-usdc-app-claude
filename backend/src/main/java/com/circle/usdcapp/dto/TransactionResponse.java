package com.circle.usdcapp.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String id,
        String circleTransactionId,
        String walletId,
        String type,
        String direction,
        BigDecimal amountUsdc,
        String counterparty,
        String state,
        String txHash,
        Instant createdAt
) {
}
