package com.circle.usdcapp.controller;

import com.circle.usdcapp.dto.BuyRequest;
import com.circle.usdcapp.dto.MintOrderResponse;
import com.circle.usdcapp.dto.SellRequest;
import com.circle.usdcapp.dto.WireInstructionsResponse;
import com.circle.usdcapp.service.CircleMintService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mint")
public class MintController {

    private final CircleMintService mintService;

    public MintController(CircleMintService mintService) {
        this.mintService = mintService;
    }

    @GetMapping("/wire-instructions")
    public WireInstructionsResponse wireInstructions() {
        return mintService.getWireInstructions();
    }

    @PostMapping("/buy")
    @ResponseStatus(HttpStatus.CREATED)
    public MintOrderResponse buy(@Valid @RequestBody BuyRequest request) {
        return mintService.initiateBuy(request);
    }

    @PostMapping("/sell")
    @ResponseStatus(HttpStatus.CREATED)
    public MintOrderResponse sell(@Valid @RequestBody SellRequest request) {
        return mintService.initiateSell(request);
    }

    @GetMapping("/orders")
    public List<MintOrderResponse> orders() {
        return mintService.listOrders();
    }
}
