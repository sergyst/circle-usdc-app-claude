package com.circle.usdcapp.dto;

import java.util.List;

public record BalanceResponse(String walletId, String address, List<TokenBalance> balances) {
}
