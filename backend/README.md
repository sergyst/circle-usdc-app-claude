# USDC Ledger — Circle-powered buy / sell / manage app

A full-stack app for buying, selling, and managing USDC on Ethereum, built on
Circle's APIs:

- **Manage** — [Circle Developer-Controlled Wallets](https://developers.circle.com/w3s/docs/developer-controlled-wallets)
  create and custody MPC-secured wallets on Ethereum Sepolia; the backend can
  check balances, send USDC on-chain, and fund wallets from Circle's testnet
  faucet.
- **Buy / Sell** — [Circle Mint](https://www.circle.com/mint) fiat on/off-ramp:
  buy = wire USD in and Circle auto-mints USDC; sell = USDC out, USD payout
  by wire. This requires an approved, KYB'd Circle business account, so the
  backend ships against the real endpoints but defaults to a clearly-labeled
  **simulation mode** until you flip `CIRCLE_MINT_LIVE=true` with real Mint
  credentials.

```
backend/    Spring Boot 3 / Java 17 REST API
frontend/   React 18 + Vite UI
```

## 1. Prerequisites

- Java 17+ and Maven (or use the included `./mvnw` if you add a wrapper)
- Node.js 18+
- A free [Circle Developer account](https://console.circle.com) (sandbox is enough to start)

## 2. Get a Circle API key

1. Sign up / log in at [console.circle.com](https://console.circle.com).
2. Go to **Developers → API & Client Keys → Generate Key**. Copy the sandbox
   key (starts with `TEST_API_KEY:`).

## 3. Generate and register your entity secret

Developer-Controlled Wallets require a 32-byte "entity secret" that only you
know, registered once with Circle. This repo includes a bootstrap runner that
generates one and registers it for you:

```bash
cd backend
export CIRCLE_API_KEY="TEST_API_KEY:xxxx:yyyy"   # from step 2
mvn spring-boot:run -Dspring-boot.run.arguments=--circle.setup.register-entity-secret=true
```

This prints a hex secret (save it!) and writes
`entity-secret-recovery-file.b64` — store that file somewhere safe; Circle
Support needs it if you ever lose the secret. **Run this only once** per
Circle account/environment.

## 4. Configure the backend

```bash
cd backend
export CIRCLE_API_KEY="TEST_API_KEY:xxxx:yyyy"
export CIRCLE_ENTITY_SECRET="<the hex secret from step 3>"
export APP_API_KEY="pick-a-local-dev-secret"
```

See `backend/src/main/resources/application.yml` for every option
(wallet set id, blockchain, Mint live/simulate switch, CORS origins, etc).
All of them can also be set directly in that file instead of via env vars.

## 5. Run the backend

```bash
cd backend
mvn spring-boot:run
```

On first run it also creates a Circle `walletSet` automatically and logs its
id — copy that into `CIRCLE_WALLET_SET_ID` so restarts reuse the same set of
wallets instead of creating a new one each time.

The API listens on `http://localhost:8080`. Check `GET /api/health`.

## 6. Run the frontend

```bash
cd frontend
npm install
cp .env.example .env   # adjust VITE_APP_API_KEY to match APP_API_KEY above
npm run dev
```

Open `http://localhost:5173`.

## 7. Try it out

1. **Wallets** → create a wallet (this calls Circle to provision a Sepolia
   address custodied by your entity secret).
2. Click **Fund from faucet** to request free testnet ETH + USDC.
3. **Send USDC** → transfer testnet USDC on-chain to any Sepolia address.
4. **Buy / Sell** → try a simulated buy/sell order (Mint isn't live yet, so
   this exercises the full UI/API flow without moving real money).
5. **Ledger history** → see everything recorded so far.

## Webhooks

Circle runs two separate, differently-signed notification systems, and this
app has a dedicated, signature-verified receiver for each:

| Endpoint                          | Covers                                                             | Signing scheme |
|------------------------------------|---------------------------------------------------------------------|----------------|
| `POST /api/webhooks/circle/wallets` | Developer-Controlled Wallets: `transactions.inbound` / `transactions.outbound` (on-chain transfers landing or leaving a wallet) | `X-Circle-Signature` + `X-Circle-Key-Id` headers, ECDSA (verified against Circle's public key, fetched and cached automatically) |
| `POST /api/webhooks/circle/mint`    | Circle Mint: `deposits` (wire in → buy completes) and `payouts` (sell status) | AWS SNS envelope + signature (verified against the signing cert AWS provides, restricted to `*.amazonaws.com`) |

Both endpoints are excluded from the `X-App-Api-Key` check (Circle/AWS can't
send that header) - signature verification is what protects them instead. A
bad or missing signature gets a `401`, and the payload is never trusted or
processed.

**To wire these up:**

1. Expose your backend on a public HTTPS URL (use `ngrok http 8080` while
   developing locally).
2. In the Circle Dashboard, under **Notifications**, add a subscriber for
   `https://<your-host>/api/webhooks/circle/wallets` and select the
   `transactions.*` event category (or `*` for everything).
3. Separately, subscribe `https://<your-host>/api/webhooks/circle/mint` to
   your Mint account's payment/payout notification topic. The first request
   AWS sends will be a `SubscriptionConfirmation` message, which the endpoint
   auto-confirms (it fetches `SubscribeURL` for you) - check the backend logs
   to confirm it went through.
4. Send a test wire deposit or transfer and watch `GET /api/transactions` /
   `GET /api/mint/orders` update automatically instead of needing a manual
   refresh.

**Known limitation:** Circle's `deposits` notification doesn't echo back a
reference code, so matching an incoming wire to the right BUY order is done
by amount (oldest open order for that exact amount). If you need tighter
matching, check whether your account's deposit payload includes a
remittance/OBI field with the wire memo and match on that instead - see
`CircleMintService.applyDepositNotification()`.

## Going live with Circle Mint (buy/sell with real money)

1. Apply for and complete KYB approval for a Circle Mint business account
   (see [circle.com/mint](https://www.circle.com/mint)).
2. Link a bank account in the Circle Dashboard and note its bank account id.
3. Set up both webhook subscriptions described above.
4. Set `CIRCLE_MINT_LIVE=true`, `CIRCLE_MINT_BANK_ACCOUNT_ID=<id>`, and swap
   `CIRCLE_API_KEY` for your `LIVE_API_KEY:...` production key.
5. Re-run the entity secret setup step (§3) against production if you haven't
   already registered one there.

## Security notes

- The frontend/backend pairing uses a single shared `X-App-Api-Key` header
  (`app.api-key` / `APP_API_KEY`) as a lightweight guard suited to an
  internal, single-tenant tool. **Replace this with real authentication**
  (OAuth2/OIDC, per-user sessions, etc.) before exposing this to real,
  multiple external users.
- Never commit `CIRCLE_API_KEY`, `CIRCLE_ENTITY_SECRET`, or the recovery file
  to source control — they are already gitignored.
- Entity secret ciphertexts are generated fresh per API request as Circle
  requires (see `EntitySecretCipherService`); never cache or reuse one.
- Mainnet (`ETH` instead of `ETH-SEPOLIA`) moves real, financially valuable
  USDC — double- and triple-check addresses and amounts before pointing
  `circle.blockchain` at it.

## API reference (backend)

| Method | Path                          | Purpose                                    |
|--------|-------------------------------|---------------------------------------------|
| GET    | `/api/health`                 | Liveness + current chain / Mint mode        |
| GET    | `/api/wallets`                | List wallets                                |
| POST   | `/api/wallets`                | Create a wallet `{ "label": "..." }`        |
| GET    | `/api/wallets/{id}/balances`  | Token balances for a wallet                 |
| POST   | `/api/wallets/{id}/faucet`    | Request testnet ETH + USDC                  |
| POST   | `/api/transfers`               | Send USDC on-chain                          |
| POST   | `/api/transfers/{id}/refresh`  | Re-poll a transfer's on-chain status        |
| GET    | `/api/transactions`            | Full ledger history                         |
| GET    | `/api/mint/wire-instructions`  | Bank wire details for buy orders            |
| POST   | `/api/mint/buy`                | Start a fiat → USDC buy order               |
| POST   | `/api/mint/sell`               | Start a USDC → fiat sell order              |
| GET    | `/api/mint/orders`             | Buy/sell order history                      |
| POST   | `/api/webhooks/circle/wallets` | Wallets (W3S) transaction notifications     |
| POST   | `/api/webhooks/circle/mint`    | Circle Mint payment/payout notifications (SNS) |

All `/api/**` routes except `/api/health` and `/api/webhooks/**` require the
`X-App-Api-Key` header.
