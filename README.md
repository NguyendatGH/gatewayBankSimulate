# Payment Lab

The project stores the Mock Payment Gateway configuration plane and exposes a small local runtime used by Encore's existing backend. Payment state is intentionally kept in memory; restart the simulator to clear it.

```text
Merchant -> Terminal -> Capabilities -> Routing Profile -> Acquirer Config
```

## Required environment

```text
DB_URL=jdbc:postgresql://localhost:5432/payment_lab
DB_USERNAME=payment_lab
DB_PASSWORD=payment_lab
GATEWAY_ADMIN_KEY=local-development-admin-key
GATEWAY_MASTER_KEY=replace-with-a-secret
```

`GATEWAY_MASTER_KEY` is hashed to an AES-256 key and used for AES-GCM encryption. Merchant secrets are returned only by create/rotate responses and are never returned by GET APIs.

Runtime APIs are authenticated with `X-Merchant-No`, `X-Terminal-Id`, and `X-Merchant-Secret`:

```text
POST /api/v1/gateway/payments
GET  /api/v1/gateway/payments/{id}
POST /api/v1/gateway/payments/{id}/cancel
POST /api/v1/gateway/refunds
GET  /api/v1/gateway/refunds/{id}
GET  /api/v1/gateway/refunds/by-reference?referenceId=...
GET  /api/v1/gateway/payout/balance
```

`checkoutUrl` opens the simulator checkout page. The Pay button posts a signed webhook to the Merchant webhook URL and redirects to the Merchant return URL.

Runtime bank profiles are separate Spring beans and are resolved by routed acquirer code:

```text
ACQUIRER_A     CARD_ACQUIRER  BIN 970422
ACQUIRER_B     CARD_ACQUIRER  BIN 970415
QR_PROVIDER_A  QR_PROVIDER    BIN 970436
```

The configuration-plane `acquirers` table controls routing and merchant MID/TID configuration; the runtime `BankProfile` controls simulated bank capabilities and authorization behavior.

Refunds use the request's `toBin` to resolve the matching runtime bank profile (and fall back to `ACQUIRER_A` when the BIN is omitted). A failed checkout is represented as `CANCELLED` for compatibility with the backend adapter, but remains retryable on the same checkout link until it is explicitly cancelled, paid, or expired.

For `CARD`, the terminal's `threeDsPolicy` controls checkout behavior: `REQUIRED` shows a simulated 3DS challenge, `OPTIONAL` lets the buyer choose 3DS or non-3DS, and `DISABLED` completes as non-3DS. `PAYNOW`, `GOOGLE_PAY`, `APPLE_PAY`, and `QR` are always non-3DS. QR returns a deterministic mock VietQR payload and routes through an acquirer of type `QR_PROVIDER`; the other methods route through `CARD_ACQUIRER`. API callers may pass `threeDs: true|false`; omitting it lets the checkout page choose when the policy is `OPTIONAL`.

## Start

```bash
docker compose up -d postgres
export GATEWAY_ADMIN_KEY=local-development-admin-key
export GATEWAY_MASTER_KEY=local-development-master-key
./mvnw spring-boot:run
```

Set `GATEWAY_SEED_ENABLED=true` to seed `ACQUIRER_A`, `ACQUIRER_B`, and `QR_PROVIDER_A` on startup. Otherwise create them through the API.

All configuration APIs require:

```http
X-Admin-Key: local-development-admin-key
```

## Example setup flow

```bash
export API=http://localhost:8080/api/v1/admin
export H='X-Admin-Key: local-development-admin-key'

# 1-2. Acquirers
curl -sS -X POST "$API/acquirers" -H "$H" -H 'Content-Type: application/json' \
  -d '{"code":"ACQUIRER_A","name":"Mock Acquirer A","type":"CARD_ACQUIRER"}'
curl -sS -X POST "$API/acquirers" -H "$H" -H 'Content-Type: application/json' \
  -d '{"code":"ACQUIRER_B","name":"Mock Acquirer B","type":"CARD_ACQUIRER"}'

# 3. Merchant; save merchantSecret from this response
curl -sS -X POST "$API/merchants" -H "$H" -H 'Content-Type: application/json' \
  -d '{"name":"Encore Ticketing","webhookUrl":"http://localhost:8080/webhooks/mock-gateway/payment"}'

# 4. Configure both downstream credentials
curl -sS -X POST "$API/merchants/MER000001/acquirer-configs" -H "$H" -H 'Content-Type: application/json' \
  -d '{"acquirerCode":"ACQUIRER_A","mid":"MID_A","tid":"TID_A"}'
curl -sS -X POST "$API/merchants/MER000001/acquirer-configs" -H "$H" -H 'Content-Type: application/json' \
  -d '{"acquirerCode":"ACQUIRER_B","mid":"MID_B","tid":"TID_B"}'

# 5-6. Routing profile and ordered rules
curl -sS -X POST "$API/routing-profiles" -H "$H" -H 'Content-Type: application/json' \
  -d '{"code":"VN_WEB_DEFAULT","name":"Vietnam Web Default"}'
curl -sS -X POST "$API/routing-profiles/VN_WEB_DEFAULT/rules" -H "$H" -H 'Content-Type: application/json' \
  -d '{"paymentMethod":"CARD","acquirerCode":"ACQUIRER_A","priority":1}'
curl -sS -X POST "$API/routing-profiles/VN_WEB_DEFAULT/rules" -H "$H" -H 'Content-Type: application/json' \
  -d '{"paymentMethod":"CARD","acquirerCode":"ACQUIRER_B","priority":2}'

# 7-8. Terminal and reads
curl -sS -X POST "$API/merchants/MER000001/terminals" -H "$H" -H 'Content-Type: application/json' \
  -d '{"name":"Encore Web VND","channel":"WEB","currency":"VND","paymentMethods":["CARD"],"threeDsPolicy":"REQUIRED","routingProfileCode":"VN_WEB_DEFAULT"}'
curl -sS "$API/merchants/MER000001" -H "$H"
curl -sS "$API/merchants/MER000001/terminals" -H "$H"
curl -sS "$API/routing-profiles/VN_WEB_DEFAULT" -H "$H"
```

Errors use `application/problem+json` and stable `code` values. `X-Request-Id` is accepted or generated and returned on every response.
