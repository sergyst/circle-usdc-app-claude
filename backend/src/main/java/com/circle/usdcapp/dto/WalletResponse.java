package com.circle.usdcapp.dto;

import java.time.Instant;

public record WalletResponse(
        String id,
        String circleWalletId,
        String address,
        String blockchain,
        String accountType,
        String label,
        Instant createdAt
) {
}
