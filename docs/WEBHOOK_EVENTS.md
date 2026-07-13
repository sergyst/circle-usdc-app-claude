# Circle Webhook Event Catalog

A reference of **every notification Circle can send by webhook**, with the data
structure of each and a link to the official Circle documentation. For setup and
signature verification, see [WEBHOOKS.md](./WEBHOOKS.md).

Circle has two notification systems (see
[Webhooks — notification API versions](https://developers.circle.com/api-reference/webhooks#notification-api-versions)):

- **v2 — Circle Wallets / W3S**: direct HTTPS POST, `X-Circle-Signature` (ECDSA).
  **This is the system this app's `/wallets` endpoint uses.**
- **v1 — Circle Mint**: delivered over AWS SNS. This app's `/mint` endpoint uses
  it (currently simulation mode until a live Mint account exists).

> **Legend:** ⭐ = consumed by this application today. Everything else belongs to
> Circle products this app does not use; you only receive them if you subscribe
> to them.

---

## Envelope structures

### v2 envelope (Circle Wallets)

Ref: [Webhooks — event model](https://developers.circle.com/api-reference/webhooks#event-model)

```json
{
  "subscriptionId": "uuid",
  "notificationId":  "uuid",            // dedupe on this (delivery is at-least-once)
  "notificationType":"transactions.inbound",
  "notification":    { /* the changed resource in its current state */ },
  "timestamp":       "2026-01-15T18:23:44Z",
  "version": 2
}
```

### v1 envelope (Circle Mint)

Ref: [Webhooks — v1 notifications](https://developers.circle.com/api-reference/webhooks#event-model),
[Verify webhook signatures](https://developers.circle.com/api-reference/verify-webhook-signatures)

Outer AWS SNS envelope (`Type`, `MessageId`, `Message`, `Signature`,
`SigningCertURL`, …); the Circle payload is a JSON string inside `Message`:

```json
{
  "clientId": "uuid",
  "notificationType": "deposits",
  "version": 1,
  "customAttributes": { "clientId": "uuid" },
  "deposit": { /* topic-specific resource; key name matches the topic */ }
}
```

---

## v2 events — Circle Wallets / W3S

The full set of subscribable types is defined by the `NotificationType` enum in
[Create a notification subscription](https://developers.circle.com/api-reference/wallets/common/create-subscription).
Subscribe with `"*"` (all), a category wildcard (e.g. `transactions.*`), or an
explicit list.

| Event type | What it reports | States | Docs |
|---|---|---|---|
| ⭐ `transactions.inbound` | Funds arriving at a wallet | `CONFIRMED`, `COMPLETE` | [Inbound transaction](https://developers.circle.com/api-reference/wallets/common/transactions-inbound) |
| ⭐ `transactions.outbound` | Funds leaving a wallet | `QUEUED`, `SENT`, `CONFIRMED`, `COMPLETE`, `CANCELED`, `FAILED` | [Outbound transaction](https://developers.circle.com/api-reference/wallets/common/transactions-outbound) |
| `challenges.*` | User-controlled wallet challenge/PIN outcomes | per challenge | [Create subscription (enum)](https://developers.circle.com/api-reference/wallets/common/create-subscription), [User-controlled wallets](https://developers.circle.com/w3s/programmable-wallets-user-controlled-create-your-first-wallet) |
| `contracts.eventLog` | A monitored smart-contract event log fired | — | [Smart Contract Platform](https://developers.circle.com/w3s/smart-contract-platform) |
| `modularWallet.userOperation` | Modular (smart-account) user operation update | — | [Modular Wallets](https://developers.circle.com/w3s/modular-wallets) |
| `modularWallet.inboundTransfer` | Inbound transfer to a modular wallet | — | [Modular Wallets](https://developers.circle.com/w3s/modular-wallets) |
| `modularWallet.outboundTransfer` | Outbound transfer from a modular wallet | — | [Modular Wallets](https://developers.circle.com/w3s/modular-wallets) |
| `travelRule.statusUpdate` | Travel Rule screening status change | — | [Compliance Engine / Travel Rule](https://developers.circle.com/w3s/compliance-engine) |
| `travelRule.approve` | Travel Rule counterparty approved | — | [Compliance Engine / Travel Rule](https://developers.circle.com/w3s/compliance-engine) |
| `travelRule.deny` | Travel Rule counterparty denied | — | [Compliance Engine / Travel Rule](https://developers.circle.com/w3s/compliance-engine) |
| `rampSession.completed` | On/off-ramp session completed | — | [Create subscription (enum)](https://developers.circle.com/api-reference/wallets/common/create-subscription) |
| `rampSession.depositReceived` | Ramp deposit received | — | [Create subscription (enum)](https://developers.circle.com/api-reference/wallets/common/create-subscription) |
| `rampSession.expired` | Ramp session expired | — | [Create subscription (enum)](https://developers.circle.com/api-reference/wallets/common/create-subscription) |
| `rampSession.failed` | Ramp session failed | — | [Create subscription (enum)](https://developers.circle.com/api-reference/wallets/common/create-subscription) |
| `rampSession.kycSubmitted` | Ramp KYC submitted | — | [Create subscription (enum)](https://developers.circle.com/api-reference/wallets/common/create-subscription) |
| `rampSession.kycApproved` | Ramp KYC approved | — | [Create subscription (enum)](https://developers.circle.com/api-reference/wallets/common/create-subscription) |
| `rampSession.kycRejected` | Ramp KYC rejected | — | [Create subscription (enum)](https://developers.circle.com/api-reference/wallets/common/create-subscription) |
| `webhooks.test` | Test ping sent when a subscription is created | — | [Webhooks](https://developers.circle.com/api-reference/webhooks) |

> `challenges.*` expands to: `challenges.createWallet`, `challenges.createTransaction`,
> `challenges.contractExecution`, `challenges.accelerateTransaction`,
> `challenges.cancelTransaction`, `challenges.initialize`, `challenges.setPin`,
> `challenges.changePin`, `challenges.restorePin`, `challenges.setSecurityQuestions`.

### `transactions.inbound` / `transactions.outbound` payload ⭐

The `notification` object mirrors the
[Get a transaction](https://developers.circle.com/api-reference/wallets/developer-controlled-wallets/get-transaction)
response:

```json
{
  "subscriptionId": "uuid",
  "notificationId": "uuid",
  "notificationType": "transactions.inbound",
  "notification": {
    "id": "uuid",
    "blockchain": "ETH-SEPOLIA",
    "walletId": "uuid",
    "tokenId": "uuid",
    "sourceAddress": "0x...",
    "destinationAddress": "0x...",
    "transactionType": "INBOUND",
    "state": "COMPLETE",
    "amounts": ["3.14"],
    "txHash": "0x...",
    "createDate": "2026-01-15T18:23:44Z",
    "updateDate": "2026-01-15T18:24:10Z"
  },
  "timestamp": "2026-01-15T18:24:10Z",
  "version": 2
}
```

Handled in this app by `WebhookController#walletsWebhook` →
`CircleWalletService#applyTransactionNotification`.

### Transaction state lifecycle (`state` field)

Circle sends **one webhook per state change**, so a single transaction produces
several notifications. Act on the `state` in each payload, not on arrival order.

Outbound (funds leaving) happy path:

```
INITIATED → CLEARED → QUEUED → SENT → CONFIRMED → COMPLETE
```

Inbound (funds arriving) happy path:

```
CONFIRMED → COMPLETE
```

| State | Meaning | Terminal? |
|---|---|---|
| `INITIATED` | Request accepted, not yet screened | no |
| `CLEARED` | Passed compliance / risk screening | no |
| `QUEUED` | Queued for submission to the blockchain | no |
| `SENT` | Broadcast on-chain, awaiting confirmation | no |
| `CONFIRMED` | Included in a block, awaiting finality | no |
| `COMPLETE` | Finalized on-chain — funds settled | **yes (success)** |
| `DENIED` | Rejected by risk screening (opposite of `CLEARED`) | **yes (failure)** |
| `FAILED` | Reverted / unrecoverable on-chain error | **yes (failure)** |
| `CANCELLED` | Cancelled before on-chain submission | **yes (failure)** |
| `STUCK` | Broadcast but not progressing (e.g. gas/nonce) | no (can still resolve) |

The compliance gate is `INITIATED → {CLEARED | DENIED}`; only a `CLEARED`
transaction proceeds on-chain.

**This app's mapping** (`CircleWalletService#mapState`, ledger has only
`PENDING / COMPLETE / FAILED`):

- `COMPLETE` → `COMPLETE`
- `FAILED`, `DENIED`, `CANCELLED` → `FAILED`
- everything else (`INITIATED`, `CLEARED`, `QUEUED`, `SENT`, `CONFIRMED`,
  `STUCK`, and any future state) → `PENDING`

`CONFIRMED` deliberately maps to `PENDING`, not `COMPLETE` — it's on-chain but
not final. A terminal state is never downgraded by a late/out-of-order webhook.

Docs: [Inbound states](https://developers.circle.com/api-reference/wallets/common/transactions-inbound),
[Outbound states](https://developers.circle.com/api-reference/wallets/common/transactions-outbound),
[Transaction lifecycle](https://developers.circle.com/wallets/transaction-limits-and-optimizations).

---

## v1 events — Circle Mint

Reference for all topics and example payloads:
[Circle Mint webhook notifications](https://developers.circle.com/circle-mint/references/webhook-notifications).

| Topic | Resource key | What it reports | States | Docs |
|---|---|---|---|---|
| ⭐ `deposits` | `deposit` | Fiat deposit (mint) settled to your balance | `pending`, `complete`, `failed` | [Webhook notifications › deposits](https://developers.circle.com/circle-mint/references/webhook-notifications) |
| ⭐ `payouts` | `payout` | Fiat redemption (burn) or stablecoin payout | `pending`, `complete`, `failed` | [Webhook notifications › payouts](https://developers.circle.com/circle-mint/references/webhook-notifications) |
| `transfers` | `transfer` | On-chain transfer status transitions | `pending`, `running`, `complete`, `failed` | [Webhook notifications › transfers](https://developers.circle.com/circle-mint/references/webhook-notifications) |
| `wire` | `wire` | Linked wire bank-account lifecycle | `pending`, `complete`, `failed` | [Webhook notifications › wire](https://developers.circle.com/circle-mint/references/webhook-notifications) |
| `paymentIntents` | `paymentIntent` | Stablecoin Payins intent lifecycle (address assignment, timeline) | `created`/`active` → `pending` → `complete`; `expired`, `failed`, `refunded` | [Webhook notifications › paymentIntents](https://developers.circle.com/circle-mint/references/webhook-notifications) |
| `payments` | `payment` | Settled Payin (`type:payment`) or Payout refund (`type:refund`) | `pending`, `confirmed`, `paid`, `failed`, `action_required` | [Webhook notifications › payments](https://developers.circle.com/circle-mint/references/webhook-notifications) |
| `addressBookRecipients` | `addressBookRecipient` | Payout recipient review + Travel Rule decision | `pending`, `inactive`, `active`, `denied` | [Webhook notifications › addressBookRecipients](https://developers.circle.com/circle-mint/references/webhook-notifications) |
| `externalEntities` | `externalEntity` | Institutional onboarding compliance outcome | `PENDING`, `ACCEPTED`, `REJECTED` | [Webhook notifications › externalEntities](https://developers.circle.com/circle-mint/references/webhook-notifications) |
| `creditTransfers` | `creditTransfer` | Settlement Advance / Line of Credit draw status | product-specific | [Webhook notifications › creditTransfers](https://developers.circle.com/circle-mint/references/webhook-notifications) |
| `creditFees` | `creditFee` | Fee accrual against a credit line | — | [Webhook notifications › creditFees](https://developers.circle.com/circle-mint/references/webhook-notifications) |
| `creditRepayments` | `creditRepayment` | Matched fiat/crypto repayment | `complete` | [Webhook notifications › creditRepayments](https://developers.circle.com/circle-mint/references/webhook-notifications) |
| `approvalWorkflowTransferApproved` | `approvalWorkflow` | Regional transfer-approval: approved | approved | [Webhook notifications › approval workflow](https://developers.circle.com/circle-mint/references/webhook-notifications) |
| `approvalWorkflowTransferRejected` | `approvalWorkflow` | Regional transfer-approval: rejected | rejected | [Webhook notifications › approval workflow](https://developers.circle.com/circle-mint/references/webhook-notifications) |

### `deposits` payload ⭐

```json
{
  "clientId": "uuid",
  "notificationType": "deposits",
  "version": 1,
  "customAttributes": { "clientId": "uuid" },
  "deposit": {
    "id": "uuid",
    "status": "complete",
    "amount": { "amount": "1000.00", "currency": "USD" },
    "fees":   { "amount": "0.00", "currency": "USD" },
    "source":      { "id": "uuid", "type": "wire" },
    "destination": { "id": "1000038499", "type": "wallet" },
    "createDate": "2026-01-15T18:23:44Z",
    "updateDate": "2026-01-15T18:26:02Z"
  }
}
```

### `payouts` payload ⭐

```json
{
  "clientId": "uuid",
  "notificationType": "payouts",
  "version": 1,
  "customAttributes": { "clientId": "uuid" },
  "payout": {
    "id": "uuid",
    "sourceWalletId": "1000038499",
    "destination": { "id": "uuid", "type": "address_book" },
    "amount":   { "amount": "100.00", "currency": "USD" },
    "toAmount": { "amount": "100.00", "currency": "USD" },
    "fees":     { "amount": "0.50", "currency": "USD" },
    "status": "complete",
    "createDate": "2026-01-15T18:23:44Z",
    "updateDate": "2026-01-15T18:26:02Z"
  }
}
```

Both handled in this app by `WebhookController#mintWebhook` →
`CircleMintService#applyDepositNotification` / `#applyPayoutNotification`.

---

## Rules that apply to every event

Ref: [Webhooks — delivery, ordering, idempotency](https://developers.circle.com/api-reference/webhooks#delivery-ordering-and-idempotency)

1. **Deduplicate.** Delivery is at-least-once; the same notification can arrive
   more than once. Dedupe on `notificationId` (v2) / `MessageId` (v1).
2. **Don't assume ordering.** `CONFIRMED` and `COMPLETE` can arrive out of order.
   Act on the `state`/`status` in the payload, not on arrival sequence.
3. **Return 2xx fast**, verify the signature on every request, and monitor
   deliveries in the Circle Console's Webhook Logs.

---

## What this application uses

Only four events are relevant today:

- ⭐ `transactions.inbound`, `transactions.outbound` — live in sandbox now.
- ⭐ `deposits`, `payouts` — wired but simulated until a live Circle Mint account
  exists.

If you subscribe with an explicit `notificationTypes` list (e.g.
`transactions.inbound,transactions.outbound`) instead of `"*"`, you won't receive
any of the other event types.

---

## References

- Webhooks overview & versions: https://developers.circle.com/api-reference/webhooks
- Verify webhook signatures (v2): https://developers.circle.com/api-reference/verify-webhook-signatures
- Create subscription (v2 event enum): https://developers.circle.com/api-reference/wallets/common/create-subscription
- Inbound transaction: https://developers.circle.com/api-reference/wallets/common/transactions-inbound
- Outbound transaction: https://developers.circle.com/api-reference/wallets/common/transactions-outbound
- Circle Mint webhook notifications (all v1 topics): https://developers.circle.com/circle-mint/references/webhook-notifications
- Setup & signature verification (this repo): ./WEBHOOKS.md
