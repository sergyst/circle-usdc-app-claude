# Circle Webhooks — How they work and how to enable them (Sandbox POC)

This document explains how Circle delivers webhook notifications to **this**
application, what we have to do to switch them on in the **sandbox**, and how the
`Circle → our app` flow works end to end. It is written for the POC in this repo
(`backend/` Spring Boot app).

> **TL;DR**
> Circle has **two different notification systems**. We use both, on two
> separate URLs:
> - **Wallet activity** (USDC moving on-chain) → Circle Wallets **v2** webhooks →
>   `POST /api/webhooks/circle/wallets`
> - **Circle Mint** (fiat ⇄ USDC buy/sell) → **v1** webhooks over AWS SNS →
>   `POST /api/webhooks/circle/mint`
>
> To enable the wallet webhooks in sandbox you (1) expose the app on a public
> HTTPS URL, (2) create a subscription pointing at that URL, and (3) let Circle
> POST notifications to us. No polling required.

---

## 1. Why webhooks at all

Buying, selling and transferring USDC are **asynchronous**. When we submit a
transfer, Circle returns immediately with a transaction in an `INITIATED` /
`PENDING`-style state. The money actually lands on-chain (or the fiat settles)
seconds-to-minutes later. Rather than polling Circle's API in a loop, Circle
**pushes** a notification to an HTTPS endpoint we own each time a resource
changes state. Our app updates its local ledger from those pushes.

So the model Circle wants is:

```
  our request  ─────────────▶  Circle           (submit transfer / buy / sell)
                                  │
                                  │ state changes over time
                                  ▼
  our endpoint ◀─────────────  Circle webhook    (transactions.inbound, deposits, …)
       │
       └── verify signature → update ledger → return 2xx
```

---

## 2. The two notification systems

Circle exposes two notification API versions. Which one applies depends on the
product, **not** on us — we just have to handle each correctly.

| | **v2 — Circle Wallets (W3S)** | **v1 — Circle Mint** |
|---|---|---|
| Used for | On-chain wallet activity (USDC transfers in/out, challenges) | Fiat ⇄ USDC (Mint deposits, payouts, payment intents) |
| Delivery | **Direct HTTPS `POST`** from Circle | **AWS SNS** publishes to our endpoint |
| Our endpoint | `POST /api/webhooks/circle/wallets` | `POST /api/webhooks/circle/mint` |
| Body shape | `{ subscriptionId, notificationId, notificationType, notification, timestamp, version:2 }` | SNS envelope `{ Type, MessageId, Message, Signature, SigningCertURL, … }` with the Circle payload JSON-encoded inside `Message` |
| Signature | `X-Circle-Signature` + `X-Circle-Key-Id` headers, **ECDSA_SHA_256** over the raw body | Standard **AWS SNS** signature: fetch X.509 cert from `SigningCertURL`, verify canonical string |
| Setup | `POST /v2/notifications/subscriptions` (or Console → Notifications) | Subscribe the URL to the Mint topic in the Console; SNS sends a one-time `SubscriptionConfirmation` |
| Confirmation handshake | **None** — endpoint just needs to return 2xx | **Yes** — must visit `SubscribeURL` once (we auto-confirm) |

Reference: Circle "Webhooks" — https://developers.circle.com/api-reference/webhooks

> **Scope note.** This POC's wallet flow is configured for **Ethereum**
> (`ETH-SEPOLIA` in sandbox). USDC is **not** issued on the native Bitcoin
> chain, so there is no "USDC on Bitcoin" wallet/webhook flow to enable — the
> Bitcoin part of the brief can't be exercised for USDC. Circle Wallets can hold
> BTC as an asset, but that's a separate track from USDC and out of scope here.

---

## 3. How each flow works in this app

### 3.1 Wallet webhooks (v2) — the main POC path

```
  Circle                                   our Spring Boot app
    │                                              │
    │  POST /api/webhooks/circle/wallets           │
    │  headers: X-Circle-Signature, X-Circle-Key-Id│
    │  body: { notificationType:"transactions.     │
    │          inbound", notification:{ id, state, │
    │          walletId, txHash, amounts, … }, … }  │
    │─────────────────────────────────────────────▶│
    │                                              │ 1. read raw body (bytes)
    │                                              │ 2. GET /v2/notifications/publicKey/{keyId}
    │◀─────────────  fetch public key ─────────────│    (cached per keyId)
    │                                              │ 3. verify ECDSA_SHA_256(sig, rawBody)
    │                                              │ 4. if type startsWith "transactions."
    │                                              │    → CircleWalletService
    │                                              │      .applyTransactionNotification()
    │                                              │      upsert LedgerTransaction by
    │                                              │      Circle transaction id (idempotent)
    │              200 { "status": "ok" }          │
    │◀─────────────────────────────────────────────│
```

Code involved:
- `controller/WebhookController#walletsWebhook` — receives + routes.
- `service/CircleWebhookSignatureService` — verifies the ECDSA signature; fetches
  and caches the public key from `/v2/notifications/publicKey/{keyId}`.
- `service/CircleWalletService#applyTransactionNotification` — updates the ledger.

Key rule: **verify against the exact raw bytes** we received. We must not parse
the JSON and re-serialize before verifying — re-serialization reorders bytes and
the signature no longer matches. The controller binds the body as a raw `String`
for this reason.

### 3.2 Circle Mint webhooks (v1, over SNS)

```
  Circle Mint ──publishes──▶ AWS SNS ──POST──▶ /api/webhooks/circle/mint
                                                    │
   first ever message: Type=SubscriptionConfirmation│
   with a SubscribeURL  ───────────────────────────▶│ app GETs SubscribeURL
                                                    │ (host restricted to *.amazonaws.com)
                                                    │ → subscription becomes "confirmed"
                                                    │
   later messages: Type=Notification                │ verify SNS signature via cert
   Message = { notificationType:"deposits",         │ from SigningCertURL, then
               deposit:{ … } }                       │ CircleMintService.applyDeposit/Payout
```

Code involved:
- `controller/WebhookController#mintWebhook`
- `service/SnsSignatureVerificationService` — confirms the subscription and
  verifies SNS signatures (only ever trusting `sns.<region>.amazonaws.com`).
- `service/CircleMintService#applyDepositNotification` / `#applyPayoutNotification`.

> In this POC, Circle Mint runs in **simulation mode** (`circle.mint.live=false`)
> because live Mint requires a KYB-approved business account and linked bank.
> The `/mint` endpoint and its SNS verification are wired and ready, but you only
> get real `deposits`/`payouts` webhooks once the account is live. The wallet
> (v2) webhooks work fully in sandbox today.

---

## 4. Security: why these endpoints are "open"

Both webhook URLs are **excluded from the app's `X-App-Api-Key` guard**
(`config/ApiKeyAuthFilter`), because Circle and AWS don't send that header. They
are protected instead by **signature verification**:
- Wallets: ECDSA signature checked against Circle's published public key.
- Mint: AWS SNS signature checked against the Amazon signing certificate, and we
  refuse any `SigningCertURL` / `SubscribeURL` that isn't an `amazonaws.com` host.

A request that fails verification gets `401` and is never applied to the ledger.

---

## 5. Enabling wallet webhooks in the sandbox — step by step

### Step 0 — Prerequisites
- A Circle **sandbox** API key (`TEST_API_KEY:...`) set as `CIRCLE_API_KEY`.
- The backend running locally (`:8080`) with at least one developer-controlled
  wallet already created (so there's something to receive transfers).

### Step 1 — Expose the app on a public HTTPS URL
Circle must be able to reach our endpoint from the internet, and it must be
HTTPS. For local dev, tunnel it:

```bash
ngrok http 8080
# → https://abcd-1234.ngrok-free.app
```

Our wallet webhook URL is then:
`https://abcd-1234.ngrok-free.app/api/webhooks/circle/wallets`

### Step 2 — Create the subscription
Pick **one** of these.

**Option A — use the built-in runner** (added for this POC):

```bash
cd backend
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="\
    --circle.setup.register-webhook=true \
    --circle.webhook.endpoint=https://abcd-1234.ngrok-free.app/api/webhooks/circle/wallets"
```

It calls `POST /v2/notifications/subscriptions`, prints the created subscription
id, and exits. Optionally narrow the events with
`--circle.webhook.notification-types=transactions.inbound,transactions.outbound`
(default is `*` = all).

**Option B — call the API directly:**

```bash
curl -X POST https://api.circle.com/v2/notifications/subscriptions \
  -H "Authorization: Bearer $CIRCLE_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
        "endpoint": "https://abcd-1234.ngrok-free.app/api/webhooks/circle/wallets",
        "notificationTypes": ["*"]
      }'
```

**Option C — Circle Console:** Developers → **Notifications** → add a subscription
with the same URL.

> Note the base URL is `https://api.circle.com` for **both** sandbox and
> production in Circle Wallets (W3S); the environment is decided by the API key
> prefix (`TEST_API_KEY` vs `LIVE_API_KEY`), which is why this app keeps
> `circle.base-url: https://api.circle.com` and swaps only the key.

> ### ⚠️ `api.circle.com` vs `api-sandbox.circle.com`
> Circle's two product families disagree on how "sandbox" is selected — this
> trips people up:
>
> | Product family | Sandbox host | Production host | Sandbox selected by |
> | --- | --- | --- | --- |
> | **Web3 Services / Programmable Wallets (v2)** — what this app uses | `https://api.circle.com` | `https://api.circle.com` | **API key prefix** (`TEST_API_KEY`) |
> | **Circle Mint / classic Payments & Payouts (v1)** | `https://api-sandbox.circle.com` | `https://api.circle.com` | **the host** |
>
> So for wallets, transfers, and wallet webhooks, always use `api.circle.com`
> with a `TEST_API_KEY` — do **not** point them at `api-sandbox.circle.com`.
> You'd only use `api-sandbox.circle.com` when testing the **Circle Mint**
> (v1) buy/sell flow, which is currently in simulation mode in this app. If you
> ever split Mint onto its own base URL, keep it separate from
> `circle.base-url`.

### Step 3 — Trigger an event and watch it arrive
Send USDC to one of our wallets (e.g. from the Circle faucet or another wallet).
Within seconds Circle POSTs `transactions.inbound` (`CONFIRMED`, then `COMPLETE`)
to our endpoint. Confirm it worked by:
- watching the backend logs (`Wallets webhook notificationId=… type=transactions.inbound`),
- checking the ledger/History view updates, and
- viewing **Webhook Logs** in the Circle Console (shows payloads + our response
  code, and lets you resend).

### Step 4 — (Optional) Circle Mint webhooks
Only relevant once the account has live Mint access. In the Console, subscribe
`https://.../api/webhooks/circle/mint` to your Mint notification topic. The first
delivery is an SNS `SubscriptionConfirmation`; our `/mint` handler auto-confirms
it by fetching the `SubscribeURL`. After that, `deposits`/`payouts` notifications
flow in.

---

## 6. Rules Circle expects our handler to follow

These are baked into the code already, but they matter if you extend it:

1. **Return 2xx fast.** Circle treats a non-2xx (or a timeout) as failure and
   **retries**. Do heavy work asynchronously if it ever gets slow; acknowledge
   first.
2. **Be idempotent — "at least once" delivery.** The *same* notification can
   arrive more than once. Deduplicate on `notificationId` (v2) / `MessageId`
   (v1). Our handlers are naturally idempotent because they **upsert on the
   Circle resource id** (transaction id, order id) rather than inserting blindly.
3. **Don't assume ordering.** `CONFIRMED` and `COMPLETE` can arrive out of order.
   Act on the `state` carried in each notification, not on arrival sequence.
4. **Verify every signature.** Never mutate the ledger from an unverified body.
5. **Verify against the raw body** (v2) — no parse-then-reserialize before the
   signature check.

---

## 7. Troubleshooting (sandbox)

| Symptom | Likely cause / fix |
|---|---|
| No webhook ever arrives | Endpoint not public/HTTPS; subscription not created; tunnel URL changed (ngrok free URLs rotate on restart — recreate the subscription). |
| `401 invalid_signature` in logs | Body was altered before verify (proxy re-encoding), or wrong environment key. Ensure the raw body reaches the controller untouched. |
| Subscription stuck `PENDING` (v1/SNS) | The `SubscriptionConfirmation` wasn't confirmed. Our `/mint` handler auto-confirms; if it can't reach `SubscribeURL`, confirm manually. In sandbox, email support@circle.com to clear a stuck pending subscription. |
| Duplicate ledger effects | Shouldn't happen (idempotent upsert), but confirm dedup key is the Circle resource id. |
| Wallet webhook 404/`X-App-Api-Key` error | Path must be under `/api/webhooks/` so `ApiKeyAuthFilter` skips it. |

---

## 8. What changed in the code for this (POC)

- `WebhookController` — corrected the setup note (wallets subscribe via
  **`/v2`** `/notifications/subscriptions`, not v1) and now logs the
  `notificationId` for tracing/dedup.
- `NotificationSubscriptionRunner` (new) — one-shot runner to register the
  wallets (v2) subscription without touching the Console
  (`circle.setup.register-webhook=true`).
- `CircleProperties.Webhook` + `application.yml` — new `circle.webhook.endpoint`
  and `circle.webhook.notification-types` config for the runner.

Signature verification, the `/wallets` and `/mint` handlers, and the ledger
update logic were already correct against current Circle docs and were left as-is.

---

### References
- Webhooks overview: https://developers.circle.com/api-reference/webhooks
- Verify webhook signatures (v2): https://developers.circle.com/api-reference/verify-webhook-signatures
- Create a subscription (v2): https://developers.circle.com/api-reference/wallets/common/create-subscription
- Get notification public key: https://developers.circle.com/api-reference/w3s/common/get-notification-signature
- Notifications quickstart: https://developers.circle.com/w3s/docs/web3-services-notifications-quickstart
