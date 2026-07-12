package com.circle.usdcapp.controller;

import com.circle.usdcapp.dto.TransactionResponse;
import com.circle.usdcapp.service.CircleWalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final CircleWalletService walletService;

    public TransactionController(CircleWalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public List<TransactionResponse> list() {
        return walletService.listLedger();
    }
}
