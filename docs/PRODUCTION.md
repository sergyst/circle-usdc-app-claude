# Production Readiness — Circle USDC App

This guide covers what it takes to move this POC from Circle **sandbox** to a
**production** deployment. There are two separate tracks:

1. **Circle account setup** — onboarding, keys, and environment configuration you
   execute in the Circle Console. This is often the longer pole (especially
   Circle Mint), so start it early.
2. **Code & infrastructure** — hardening the Spring Boot backend and React
   frontend for real traffic and real money.

> The code changes are the smaller half. The gating factor is Circle account
> onboarding — particularly Circle Mint, which is KYB-gated and institution-grade.

---

## Part 1 — Circle account settings to execute

### 1.1 Programmable Wallets (the transfer + webhook flow already built)

Sandbox and production are **fully separate environments** — nothing carries
over between them.

- [ ] **Complete Circle business verification / KYB** to unlock production in the
      Console.
- [ ] **Generate a production API key** (`LIVE_API_KEY:...`, scoped to mainnet).
      Testnet keys and testnet wallets do **not** work on mainnet, and vice versa.
- [ ] **Generate and register a new entity secret in production.** It is
      environment-specific and produces its own recovery file. Store the hex
      secret and the recovery file yourself — Circle cannot recover them.
- [ ] **Create a new wallet set and wallets on mainnet.** Your Sepolia wallets
      are testnet-only.
- [ ] **Switch the chain:** `circle.blockchain` `ETH-SEPOLIA` → `ETH`. Base URL
      stays `https://api.circle.com`; only the API key changes.
- [ ] **Set up gas.** On mainnet every transaction costs real gas. Configure a
      **Circle Gas Station** gas policy to sponsor gas, or fund each wallet with
      native ETH. (The sandbox faucet does not exist on mainnet.)
- [ ] **Recreate the webhook subscription** against the production HTTPS endpoint
      (`POST /v2/notifications/subscriptions`, or the Console).
- [ ] **Lock down API keys:** IP allowlisting, restricted scopes, and a documented
      rotation policy.

### 1.2 Circle Mint (only if doing real USD ⇄ USDC buy/sell)

This is a separate, institution-grade onboarding, independent of Programmable
Wallets.

- [ ] Apply for a **Circle Mint account** and pass its KYB application (certificate
      of incorporation, beneficial-owner disclosures, source-of-funds, AML/CFT
      program).
- [ ] Maintain a **banking relationship at a Tier 1/2 institution** able to wire
      to Circle's settlement banks (no intermediary-bank redemptions).
- [ ] Expect Circle to look for a **real business reason / volume** (typically
      seven-figure monthly volume) to grant primary-issuance access.
- [ ] **Link the settlement bank account** and record its ID
      (`circle.mint.settlement-bank-account-id`).
- [ ] Once approved, set `circle.mint.live=true` and wire the **v1 (SNS) Mint
      webhook** subscription to `/api/webhooks/circle/mint`.

> ⚠️ **Legal gate.** If the goal is a **consumer-facing** buy/sell product,
> Circle Mint alone is not sufficient — reselling USDC to end users typically
> triggers **money-transmitter licensing** (or requires a licensed partner).
> Settle this compliance/legal question before engineering.
>
> **Scope note.** USDC is not issued on the native Bitcoin chain, so the "BTC"
> part of the original brief does not apply to USDC.

---

## Part 2 — Code & infrastructure changes

### 2.1 Highest risk — do these first

1. **Real authentication & authorization.** ✅ *Backend done* — `SecurityConfig`
   now runs a Spring Security **JWT resource server** in the `prod` profile
   (validates OIDC bearer tokens via `OAUTH2_ISSUER_URI`), while local dev keeps
   the `X-App-Api-Key` shared key. Health and webhook endpoints stay public.
   **Remaining:**
   - [ ] **Frontend:** switch from sending `X-App-Api-Key` to
     `Authorization: Bearer <token>` obtained from the IdP login. The current
     Vite-bundled key is **not** a secret and must not be used in prod.
   - [ ] **Per-user authorization:** add role/scope checks (method security or
     request matchers) once user roles exist in the IdP — the current setup only
     enforces *authentication*, not fine-grained *authorization*.
   - [ ] Wire the actual IdP (Auth0 / Cognito / Okta / Entra / Keycloak) and set
     `OAUTH2_ISSUER_URI` + `SPRING_PROFILES_ACTIVE=prod`.
2. **Secrets management.** The **entity secret controls every wallet** — treat it
   as the crown jewel. Move it and the API key out of `.env` into a managed store
   (AWS Secrets Manager / HashiCorp Vault / GCP Secret Manager) with encryption
   at rest, least-privilege access, rotation, and a securely backed-up recovery
   file.
3. **Database.** Swap H2-file → **PostgreSQL** (the driver is already a
   dependency) and replace `spring.jpa.hibernate.ddl-auto=update` with
   **Flyway or Liquibase** migrations. Add automated backups and HA.

### 2.2 Security hardening

- [ ] Disable the H2 console (`spring.h2.console.enabled=false`).
- [ ] Lock CORS (`app.cors.allowed-origins`) to the real production domain(s).
- [ ] Enforce TLS/HTTPS end to end and enable HSTS.
- [ ] Add rate limiting, request-size limits, and a WAF.
- [ ] Remove/disable the **testnet faucet** endpoint in production.
- [ ] Keep the per-request entity-secret ciphertext (your replay protection) and
      idempotency keys on all mutating Circle calls.

### 2.3 Webhooks for real traffic

- [ ] Persist processed `notificationId` (v2) / `MessageId` (v1) for **durable
      deduplication**, not just the in-memory upsert-by-resource-id.
- [ ] Return `2xx` immediately and process **asynchronously** (queue + dead-letter
      queue for failures).
- [ ] Add a **reconciliation job** that periodically polls Circle for state.
      Delivery is at-least-once and events can be missed, so webhooks alone are
      not a source of truth.
- [ ] Continue verifying signatures (already implemented for both v2 and v1) and
      monitor deliveries via the Console's Webhook Logs.

### 2.4 Reliability & operations

- [ ] Spring Actuator health / readiness / liveness probes.
- [ ] Metrics (Micrometer → Prometheus), structured JSON logging, distributed
      tracing, and alerting.
- [x] ✅ **Done** — Resilience4j retries + circuit breaker and explicit
      connect/read **timeouts** on the Circle `RestClient` (see `CircleApiClient`
      + `RestClientConfig`; reads retry, writes don't, 4xx fails fast).
- [ ] Containerize (Docker) and orchestrate (Kubernetes / ECS) with autoscaling
      and health probes.
- [ ] CI/CD with unit + integration tests, dependency/SAST scanning, and staged
      rollout. Use a dedicated `prod` config profile with externalized config.

### 2.5 Financial correctness & compliance

- [ ] Treat Circle as the **ledger source of truth** and reconcile against it.
- [ ] Add KYC/AML, sanctions screening, and transaction monitoring for end users.
- [ ] Evaluate **Travel Rule** obligations (Circle emits Travel Rule
      notifications).
- [ ] Maintain a complete, immutable audit trail for accounting and audit.

### 2.6 Frontend

- [ ] Replace the browser-exposed `X-App-Api-Key` with real auth tokens tied to
      the backend auth above.
- [ ] Add a Content Security Policy, secure cookie handling, and standard web
      hardening.

---

## Suggested sequencing

1. Kick off **Circle KYB / production onboarding** (Part 1) — longest lead time.
2. In parallel, close the **three highest-risk code gaps** (auth, secrets,
   Postgres + migrations).
3. Harden security, webhooks, and observability (2.2–2.4).
4. Layer in **compliance and reconciliation** (2.5) before touching real funds.
5. Only then flip mainnet keys / `mint.live=true` and run a limited pilot with
   real money and low limits.

---

## References

- Circle sandbox vs production: https://developers.circle.com/w3s/docs/circle-developer-account
- Onboard users to Developer-Controlled Wallets: https://developers.circle.com/wallets/dev-controlled/onboard-users
- Circle Mint overview: https://developers.circle.com/circle-mint/introducing-circle-mint
- Circle Mint bank / KYB requirements: https://help.circle.com/s/article/Information-required-to-link-your-bank-account
- Webhooks (this repo): ./WEBHOOKS.md
