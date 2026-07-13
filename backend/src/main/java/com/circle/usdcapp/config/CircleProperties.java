package com.circle.usdcapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "circle")
public class CircleProperties {

    /** Root of the Circle API, e.g. https://api.circle.com */
    private String baseUrl;

    /** Bearer token, e.g. TEST_API_KEY:xxxx:yyyy or LIVE_API_KEY:xxxx:yyyy */
    private String apiKey;

    /** 32-byte hex entity secret used to derive a fresh ciphertext per request. */
    private String entitySecret;

    /** Chain identifier used for wallet creation, e.g. ETH-SEPOLIA or ETH */
    private String blockchain;

    /** Circle walletSetId that all developer-controlled wallets belong to. */
    private String walletSetId;

    private final Mint mint = new Mint();

    private final Webhook webhook = new Webhook();

    /**
     * Settings for the optional startup runner that registers a v2 (Wallets)
     * notification subscription with Circle. See NotificationSubscriptionRunner.
     */
    public static class Webhook {
        /**
         * Public HTTPS URL Circle should POST wallet notifications to, e.g.
         * https://<your-ngrok-id>.ngrok-free.app/api/webhooks/circle/wallets
         * Must be publicly reachable and return 2xx.
         */
        private String endpoint;

        /**
         * Notification types to subscribe to. "*" (default) subscribes to
         * everything; narrow it to e.g. transactions.inbound, transactions.outbound.
         */
        private String notificationTypes = "*";

        /**
         * When true, WebhookController logs the full headers and pretty-printed
         * body of every incoming webhook. Handy in dev; turn off in production
         * to avoid verbose/sensitive logs.
         */
        private boolean logPayloads = true;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getNotificationTypes() {
            return notificationTypes;
        }

        public void setNotificationTypes(String notificationTypes) {
            this.notificationTypes = notificationTypes;
        }

        public boolean isLogPayloads() {
            return logPayloads;
        }

        public void setLogPayloads(boolean logPayloads) {
            this.logPayloads = logPayloads;
        }
    }

    public static class Mint {
        /** When false, buy/sell orders are processed in simulation mode. */
        private boolean live;
        private String settlementBankAccountId;

        public boolean isLive() {
            return live;
        }

        public void setLive(boolean live) {
            this.live = live;
        }

        public String getSettlementBankAccountId() {
            return settlementBankAccountId;
        }

        public void setSettlementBankAccountId(String settlementBankAccountId) {
            this.settlementBankAccountId = settlementBankAccountId;
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEntitySecret() {
        return entitySecret;
    }

    public void setEntitySecret(String entitySecret) {
        this.entitySecret = entitySecret;
    }

    public String getBlockchain() {
        return blockchain;
    }

    public void setBlockchain(String blockchain) {
        this.blockchain = blockchain;
    }

    public String getWalletSetId() {
        return walletSetId;
    }

    public void setWalletSetId(String walletSetId) {
        this.walletSetId = walletSetId;
    }

    public Mint getMint() {
        return mint;
    }

    public Webhook getWebhook() {
        return webhook;
    }
}
