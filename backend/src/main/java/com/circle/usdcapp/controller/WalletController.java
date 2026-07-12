package com.circle.usdcapp.controller;

import com.circle.usdcapp.dto.BalanceResponse;
import com.circle.usdcapp.dto.CreateWalletRequest;
import com.circle.usdcapp.dto.WalletResponse;
import com.circle.usdcapp.service.CircleWalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final CircleWalletService walletService;

    public WalletController(CircleWalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public List<WalletResponse> list() {
        return walletService.listWallets();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse create(@Valid @RequestBody CreateWalletRequest request) {
        return walletService.createWallet(request.label());
    }

    @GetMapping("/{id}/balances")
    public BalanceResponse balances(@PathVariable String id) {
        return walletService.getBalances(id);
    }

    @PostMapping("/{id}/faucet")
    public Map<String, String> requestFaucet(@PathVariable String id) {
        walletService.requestFaucetFunds(id);
        return Map.of("status", "requested");
    }
}
