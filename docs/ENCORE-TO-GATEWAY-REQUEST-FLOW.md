# Một request từ Encore đi qua gateway như thế nào

Ngày viết: 2026-10-09. Viết bằng cách đọc code, **chưa chạy thật**.

**Đã đọc code:** `GatewayRuntimeController`, `GatewayRuntimeAuth`, `GatewayRuntimeService`, `CardPaymentService`, `CardBankRouter`, `QrPaymentService`, `MerchantOnboardingService`, `MerchantOnboardingController`, `GatewayCheckoutController`, `CardPaymentController`, `CardCheckoutController`, `CardReturnController`, `BankCallbackController`, phía Encore là `BankSimPaymentGateway.createPaymentLink`.

**Chưa đọc code (lấy từ CLAUDE.md / `GATEWAY-BANK-FLOW.md`):** `ThreeDsAcquirerClient`, `DirectAcquirerClient`, mock bank, `MerchantWebhookSender`, `AdminKeyFilter`, `GatewayMoneyService.capture`.

Đường dẫn Java dưới đây tính từ `bankSimulate/src/main/java/com/bankSimulate/`.

---

## 1. Bản đồ controller: ai gọi, khi nào

Mỗi controller phục vụ một người gọi khác nhau.

### 1.1 Encore BE gọi, xác thực bằng bộ ba header merchant

Bộ ba header là `X-Merchant-No`, `X-Terminal-Id`, `X-Merchant-Secret`. `GatewayRuntimeAuth.authenticate` kiểm tra chúng.

| Controller | Endpoint | Khi nào chạy |
|---|---|---|
| `GatewayRuntimeController` | `POST /api/v1/gateway/payments`, `GET /payments/{id}`, `POST /payments/{id}/cancel`, `/refunds`, `/refunds/{id}`, `/refunds/by-reference`, `/payout/balance` | Khách bấm thanh toán, job reconcile, hoàn tiền. |
| `GatewayTerminalController` | `GET /api/v1/gateway/terminal` | Khách mở trang checkout. Encore hỏi terminal của BTC trả được method nào. Adapter cache 15 giây. |
| `GatewayBankController` | `GET /api/v1/gateway/banks` | Encore hỏi ngân hàng nào gateway chi tiền tới được (đích hoàn tiền). Cache 60 giây. |
| `CardPaymentController` | `POST /api/v1/gateway/card-payments`, `GET /{id}`, `POST /{id}/cancel` | Cửa cũ chuyên cho thẻ. **Encore hiện không gọi** (grep `be/` chỉ thấy `/api/v1/gateway/payments`). |

### 1.2 Trình duyệt của khách gọi (trang HTML, không có header)

| Controller | Endpoint | Khi nào chạy |
|---|---|---|
| `CardCheckoutController` | `GET/POST /card-checkout/{id}` | Khách bị redirect đến sau khi tạo payment thẻ, để nhập số thẻ. |
| `GatewayCheckoutController` | `GET /checkout/{tradeNo}`, `POST /checkout/{tradeNo}/{succeed\|fail\|expire}` | Trang QR/PAYNOW/ví. Nút "Xác nhận thanh toán" = ngân hàng của khách duyệt. |
| `CardReturnController` | `GET /gateway-return/{id}` | Sau khi xong thẻ hoặc OTP, redirect về `returnUrl` của Encore. |

### 1.3 Mock bank gọi ngược lại

| Controller | Endpoint | Khi nào chạy |
|---|---|---|
| `BankCallbackController` | `POST /gateway-callback/{bank}` (ký HMAC) | Chỉ nhánh thẻ **có 3DS**, sau khi khách nhập OTP. |

### 1.4 Admin gọi, cần `X-Admin-Key` (`AdminKeyFilter`)

`X-Admin-Key` do **Encore BE** gắn phía server. Browser không bao giờ thấy key này.

| Controller | Khi nào chạy |
|---|---|
| `MerchantOnboardingController` (`POST /api/v1/admin/merchant-onboarding`) | **Lúc một người trở thành BTC.** Xem mục 3. |
| `MerchantAdminController` | Xem merchant. `PUT …/settlement-account` chạy khi BTC lưu tài khoản nhận tiền. `credentials/rotate` chạy khi admin rotate secret. |
| `TerminalAdminController` | Admin chỉnh terminal. `…/configuration` chạy khi BTC chọn ngân hàng và phương thức. |
| `AcquirerAdminController` | Thêm hoặc sửa acquirer, `bank_bin`, cờ 3DS. Đây là nguồn danh sách ngân hàng BTC chọn được. |
| `RoutingAdminController` | Tạo routing profile và rule (chỉ dùng cho terminal chưa chọn ngân hàng). |

### 1.5 Vòng đời một BTC

```
tạo BTC ──► merchant-onboarding ──► BTC chọn ngân hàng + khai tài khoản ──► mỗi lần khách mua
            (nhóm admin)            (admin/terminal + admin/settlement)     (nhóm runtime)
```

Hai cơ chế xác thực khác nhau. Runtime dùng bộ ba merchant. Admin dùng một khóa chung `X-Admin-Key`. Nhóm 1.2 và 1.3 không có xác thực kiểu header, vì người gọi là trình duyệt hoặc mock bank. Chúng dựa vào `gwTxnId` khó đoán, và callback có thêm chữ ký HMAC.

---

## 2. Luồng chính: `POST /api/v1/gateway/payments`

### 2.1 Phía Encore (`be/`)

```
CheckoutServiceImpl:141              gateway.createPaymentLink(command)      ← NGOÀI TX
  └─ BankSimPaymentGateway:82        createPaymentLink()
       ├─ credentials.forOrganizer(organizerId)   → merNo + terminalId + secret của BTC đó
       └─ post(ctx, "/api/v1/gateway/payments")   → HTTP + bộ ba header merchant
```

Kết quả `PaymentLink(providerPaymentId, checkoutUrl, qrCode, merchantNo, terminalId)`. Hai trường cuối được lưu vào payment (V16) để lệnh sau này tra theo merchant gốc.

### 2.2 Phía gateway, theo thứ tự

1. **`RequestIdFilter`** (`infrastructure/logging/`) gắn request id, log `<- POST /path`.
2. **`GatewayRuntimeController.createPayment`** (`infrastructure/web/`) là lớp mỏng:
   `service.createPayment(auth.authenticate(merNo, terminalId, secret), request)`
3. **`GatewayRuntimeAuth.authenticate`** (`application/`), chạy trước service. Thứ tự kiểm:
   - merchant tồn tại, ACTIVE, nếu không thì 409 `MERCHANT_INACTIVE`;
   - terminal tồn tại, thuộc merchant (403 `TERMINAL_NOT_OWNED`), ACTIVE;
   - credential ACTIVE, giải mã AES, so secret bằng `MessageDigest.isEqual`, sai thì 401 `INVALID_MERCHANT_CREDENTIAL`.

   Kết quả là `Access(merchant, terminal, secret)`.
4. **`GatewayRuntimeService.createPayment`** (`application/`):
   - đọc `paymentMethod`, thiếu thì `CARD`, lạ thì 400 `PAYMENT_METHOD_NOT_SUPPORTED`;
   - terminal phải bật method đó, nếu không thì 409 `PAYMENT_METHOD_NOT_ENABLED`;
   - `orderCode` đã dùng cho method khác thì 409 `ORDER_CODE_ALREADY_USED`;
   - rẽ nhánh: `CARD` đi `CardPaymentService.createFromPayments`, còn lại đi `QrPaymentService.create`.

`GET /payments/{id}` và `POST /payments/{id}/cancel` cũng đi qua `auth.authenticate`. Service dùng `cards.exists(tradeNo)` để biết giao dịch thuộc nhánh thẻ hay QR. Refund đi qua cùng cửa xác thực nhưng vào `RefundPayoutService`, không qua `GatewayRuntimeService`.

### 2.3 Nhánh CARD

`CardPaymentService.createFromPayments` gọi `create(...)` với toàn bộ trường thẻ là `null`:

1. Terminal phải bật CARD.
2. `orderCode` đã tồn tại thì trả lại giao dịch cũ (idempotent).
3. `CardBankRouter.resolve` gọi `TerminalRoutes.plan(terminal, CARD)` lấy danh sách acquirer, rồi lọc: acquirer nhận CARD, hợp 3DS (`meetsThreeDs`), merchant có Acquirer Connection ACTIVE. Rỗng thì 409 `ROUTING_NOT_CONFIGURED` hoặc `NO_CARD_BANK_AVAILABLE`.
4. Tạo `GatewayTransaction` (trạng thái `CREATED`) trong `tx.executeWithoutResult`. `record(...)` gọi `money.register` ghi vào `payment_transactions`.
5. Không có thẻ trong request, nên trả luôn `checkoutUrl = /card-checkout/{gwTxnId}`. Số thẻ đi thẳng từ trình duyệt vào gateway, Encore không bao giờ thấy.

### 2.4 Nhánh QR / PAYNOW / GOOGLE_PAY / APPLE_PAY

`QrPaymentService.create`:

1. `orderCode` đã có thì trả lại payment cũ.
2. `threeDs=true` thì 409 `THREEDS_CARD_ONLY`.
3. `resolveRoute`: lấy acquirer đầu tiên trong `TerminalRoutes.plan` thỏa nhận method, hợp 3DS, merchant có connection ACTIVE.
4. `validateItems`: tổng `quantity × price` phải bằng `amount`, nếu không thì 400 `PAYMENT_AMOUNT_MISMATCH`.
5. `validateSettlement`: BIN 6 số và số tài khoản 6 đến 30 số, phải có cả hai hoặc không có cả hai.
6. `money.register`, `money.updateThreeDs("NOT_REQUIRED")`, status `PENDING`. Trùng khóa (đua) thì trả payment đang có.
7. Trả `{tradeNo, checkoutUrl=/checkout/{tradeNo}, qrCode="MOCKQR|v=1|merchant=…|order=…|amount=…|currency=VND"}`.

**Không có lời gọi mock bank HTTP ở bước tạo, cả hai nhánh.**

---

## 3. Flow Onboarding (lúc tạo BTC)

```
Encore: OrganizerServiceImpl.create
   └─ phát event OrganizerCreated
        (sau commit đăng ký, thread riêng; đăng ký không chờ gateway)
   └─ OrganizerGatewayOnboarding
        POST /api/v1/admin/merchant-onboarding   [X-Admin-Key]
             │
Gateway:     ▼
   AdminKeyFilter ─► MerchantOnboardingController ─► MerchantOnboardingService.onboard  (@Transactional, MỘT TX)
        1. merchants.create(name, webhookUrl, externalReference)  → sinh merNo + secret "gwsec_…"
        2. nếu request có settlementAccount → merchants.updateSettlement
        3. với mỗi acquirer mặc định (bank-a, bank-b, QR_PROVIDER_A):
              acquirers.configure(merNo, "MID-<code>-<merNo>", "TID-<code>-<merNo>")
        4. terminals.create(methods CARD+QR, 3DS OPTIONAL, routing profile STANDARD)
        5. trả { merNo, terminalId, merchantSecret, routingProfileCode, paymentMethods, threeDsPolicy }
             │
Encore:      ▼
   lưu organizer_gateway_bindings (secret mã hóa bằng BANK_SIMULATE_CREDENTIAL_KEY)
```

- Cả 4 bước nằm trong **một transaction**. Lỗi giữa chừng thì không có merchant nửa vời.
- Secret **chỉ trả về một lần**, trong response này. Mất response thì `GatewayProvisioningJob` (60 giây) tra lại bằng `externalReference` rồi rotate secret.
- Profile `STANDARD` do seed tạo. Tắt seed (`GATEWAY_SEED_ENABLED`) thì bước 4 trả 404.
- Mặc định đổi bằng `gateway.defaults.*` (`acquirers`, `terminal.routing-profile`, `terminal.payment-methods`, `terminal.three-ds-policy`, `terminal.channel`, `terminal.currency`).

---

## 4. Flow thẻ chi tiết

### 4.1 Các controller thẻ

**`CardCheckoutController` (`/card-checkout/{id}`)**
- `GET` hiện form: mã đơn, số tiền, ngân hàng xử lý, số thẻ, tháng, năm, CVV. Giao dịch không còn `CREATED` thì hiện "Giao dịch này đã được xử lý".
- `POST` kiểm định dạng ở controller (PAN 13 đến 19 số, CVV 3 đến 4 số, tháng 1 đến 12). Sai thì render lại form kèm lỗi. Đúng thì gọi `service.submitCard`, rồi trả 303 tới `target`.
- `submitCard` chặn khi không còn `CREATED` (409 `CARD_ALREADY_SUBMITTED`) hoặc hết hạn (409 `TRANSACTION_EXPIRED`), rồi gọi `authorizeWithBank`.
- `target` là link OTP của bank nếu có, ngược lại là `/gateway-return/{id}`.
- Số thẻ **không được lưu**. Chỉ giữ 6 số đầu và 4 số cuối (`mask`).

**`BankCallbackController` (`POST /gateway-callback/{bank}`)**
- Kiểm `X-Bank-Signature` bằng `CallbackSigner`, sai thì 401 `INVALID_BANK_SIGNATURE`.
- Gọi `handleBankNotification(bank, txnId, approved, authCode, msg)`: khóa row, chốt `SUCCESS`/`FAILED`, bắn webhook.
- Giao dịch đã terminal thì bỏ qua, nên callback trùng không gây hại.

**`CardReturnController` (`/gateway-return/{id}`)**
- Lấy `merchantReturnUrl`, nối `?gwTxnId=…&resultCode=…`, redirect 303.
- Chỉ là chuyển hướng cho trình duyệt. **Trạng thái đơn không đổi ở đây**, nó đổi nhờ webhook, nên FE `/checkout/return` phải polling.

### 4.2 `authorizeWithBank` (trong `CardPaymentService`)

Chạy **ngoài TX** theo luật vàng "gọi provider ngoài transaction":

1. Đọc snapshot giao dịch, dựng `failoverChain` từ `CardBankRouter.resolveAll`, bắt đầu từ bank hiện tại của giao dịch.
2. `recordCard(brand, masked)` trong một TX ngắn.
3. Lặp qua chuỗi bank. Với mỗi bank: `useBank` (đổi `bankCode` nếu khác), rồi `bank.port().authorize(...)`.
   - Lỗi mà `BankFailure.canTryAnotherBank` cho phép thì `noteFailover` ghi event và thử bank kế tiếp.
   - Lỗi không cho thử bank khác thì `failNow` chốt FAILED rồi ném lỗi.
   - Hết bank mà không ai nhận lệnh thì `failNow` rồi ném 502 `ALL_UNAVAILABLE`.
4. Có kết quả thì mở một TX, khóa row, `switch` theo outcome:
   - `APPROVED` → `apply(SUCCESS)`;
   - `DECLINED` → `apply(FAILED)`;
   - `REDIRECT_REQUIRED` → `awaitAuthentication` (trạng thái `PENDING_AUTH`), trả link OTP.
5. Sau commit mới `deliver(notification)` bắn webhook.

Terminal đã được BTC gắn với một ngân hàng (`acquirer_id`) thì chuỗi chỉ có một bank, nên không có failover.

### 4.3 Sơ đồ thẻ có 3DS

```
Khách      Encore BE            Gateway (:8090)                         Mock bank
 │            │                       │                                      │
 │ bấm TT     │                       │                                      │
 ├──────────► │                       │                                      │
 │            │ BankSimPaymentGateway │                                      │
 │            │ POST /gateway/payments│                                      │
 │            │ X-Merchant-No/-Terminal-Id/-Secret                           │
 │            ├─────────────────────► │ RequestIdFilter                      │
 │            │                       │ GatewayRuntimeController             │
 │            │                       │ GatewayRuntimeAuth.authenticate      │
 │            │                       │ GatewayRuntimeService.createPayment  │
 │            │                       │ CardPaymentService.create            │
 │            │                       │   CardBankRouter → TerminalRoutes    │
 │            │                       │   lưu GatewayTransaction = CREATED   │
 │            │ ◄───────────────────── │ { id, checkoutUrl=/card-checkout/id }│
 │ ◄──────────┤ redirect checkoutUrl  │                                      │
 │                                    │                                      │
 │ nhập thẻ, POST /card-checkout/{id} │                                      │
 ├──────────────────────────────────► │ CardCheckoutController               │
 │                                    │ CardPaymentService.submitCard        │
 │                                    │ authorizeWithBank (ngoài TX)         │
 │                                    │ ThreeDsAcquirerClient                │
 │                                    ├────────────────────────────────────► │
 │                                    │   POST /mock-bank/{bank}/v1/authorize│
 │                                    │ ◄──────────────────────────────────── │
 │                                    │   REDIRECT_REQUIRED (link OTP)       │
 │                                    │ status → PENDING_AUTH                │
 │ ◄────────────────────────────────── │ redirect sang trang OTP của bank    │
 │                                                                           │
 │ nhập OTP 123456                                                           │
 ├─────────────────────────────────────────────────────────────────────────► │
 │                                    │ ◄──────────────────────────────────── │
 │                                    │   POST /gateway-callback/{bank} (ký HMAC)
 │                                    │ BankCallbackController               │
 │                                    │ handleBankNotification (khóa row)    │
 │                                    │ apply → SUCCESS, ghi sổ CAPTURE      │
 │                                    │   → SETTLEMENT                       │
 │            ◄─────────────────────── │ MerchantWebhookSender (sau commit)  │
 │            │ POST /webhooks/mock-gateway/payment (HMAC secret merchant)   │
 │            │ PaymentServiceImpl → đơn PAID                                │
 │ ◄──────────┼───────────────────────  redirect /gateway-return/{id} → returnUrl
```

### 4.4 Ba kịch bản theo thẻ test (theo CLAUDE.md, acquirer có 3DS)

| Thẻ | Điều xảy ra |
|---|---|
| đuôi `2000` | `authorize` trả APPROVED ngay. `apply(SUCCESS)`, webhook bắn đi, redirect thẳng `/gateway-return`. Không có callback. |
| đuôi `1000` | `REDIRECT_REQUIRED`, trạng thái `PENDING_AUTH`, redirect sang trang OTP. Sau OTP, mock bank gọi `/gateway-callback`, rồi webhook bắn đi. |
| số khác | `DECLINED`. `apply(FAILED)`, webhook thất bại bắn đi. |

Acquirer không 3DS (`DirectAcquirerClient`): thẻ `…1000` duyệt luôn, số khác từ chối (theo CLAUDE.md, chưa đối chiếu code).

---

## 5. Flow checkout QR (`GatewayCheckoutController`)

```
Encore ─ POST /gateway/payments (QR) ─► GatewayRuntimeService ─► QrPaymentService.create   (mục 2.4)

Khách mở  GET /checkout/{tradeNo}
     └─ QrPaymentService.checkout → expireIfDue (quá hạn thì EXPIRED) → QrCheckoutPage.render (HTML + QR)

Khách bấm nút → POST /checkout/{tradeNo}/{succeed|fail|expire}
     └─ QrPaymentService.complete  (1 TX, khóa row FOR UPDATE)
          ├─ đã PAID        → bỏ qua, redirect returnUrl (chống bấm đúp)
          ├─ không PENDING  → redirect cancelUrl
          ├─ expire / quá hạn → markFinal EXPIRED + webhook thất bại
          ├─ fail           → markFinal CANCELLED + webhook thất bại
          └─ succeed        → money.capture(...)
                              CAPTURE vào clearing:<merchant> → SETTLEMENT sang tài khoản BTC
                              + webhook paid
     └─ sau commit: deliver(webhook) → MerchantWebhookSender.sendQr → Encore
     └─ trả 303 redirect về returnUrl hoặc cancelUrl
```

- Nút "Xác nhận thanh toán" chính là **ngân hàng của khách duyệt**, vì QR không có mock bank.
- Body webhook luôn mang `payerBankBin = 970436` và một số tài khoản người trả cố định (sandbox), để hoàn tiền về người trả vẫn chi được.
- `eventId` webhook = `tradeNo:transactionRef`, Encore dùng để chống xử lý trùng.
- `QrPaymentExpiryJob` quét payment QR `PENDING` quá hạn (tối đa 50 dòng mỗi lần) và đánh `EXPIRED`.

---

## 6. Điểm dễ hiểu nhầm

1. **Tạo payment không gọi mock bank.** Chỉ tạo bản ghi và trả link. Mock bank chỉ bị gọi khi khách nhập thẻ (nhánh thẻ).
2. **Kết quả không quay về qua response.** Nó về Encore qua **webhook** `POST /webhooks/mock-gateway/payment`, ký HMAC bằng secret merchant. Đường dự phòng là `PaymentReconcileJob` (Encore, 5 phút), gọi `GET /payments/{id}`.
3. **Redirect về `returnUrl` không đổi trạng thái đơn.** FE phải polling `/checkout/return`.
4. **Webhook gửi sau commit, ngoài transaction.** Mọi chỗ `deliver(...)` đều nằm sau `tx.execute`.
5. **Hai cửa tạo thẻ cùng dẫn về `CardPaymentService.create`.** `/payments` luôn không gửi thẻ, nên luôn ra `checkoutUrl`. `/card-payments` cho phép gửi kèm thẻ.
6. **Hai cơ chế xác thực khác nhau** (mục 1.5). Rotate secret thẳng trên gateway làm BTC đó nhận 401 `INVALID_MERCHANT_CREDENTIAL`, vì binding phía Encore giữ bản secret cũ. Chỉ rotate qua Admin Portal.

---

## 7. Phần chưa đọc, nên đọc tiếp

- `infrastructure/bank/ThreeDsAcquirerClient`, `DirectAcquirerClient` và mock bank (trang OTP, giới hạn 3 lần sai OTP).
- `infrastructure/notify/MerchantWebhookSender` (ký HMAC, retry).
- `application/GatewayMoneyService` (`register`, `capture`, `markFinal`, bút toán clearing và settlement).
- `infrastructure/security/AdminKeyFilter`.
