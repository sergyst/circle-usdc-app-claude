package com.circle.usdcapp.util;

public final class Constants {

    private Constants() {
    }

    /** Circle's official USDC contract on Ethereum Sepolia testnet. Used only as a
     *  fallback when a wallet has no existing USDC token balance entry to read a
     *  tokenId from. See https://developers.circle.com/stablecoins/usdc-contract-addresses */
    public static final String USDC_SEPOLIA_CONTRACT_ADDRESS = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238";

    public static final String USDC_SYMBOL = "USDC";
}
