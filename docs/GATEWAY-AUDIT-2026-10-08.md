# Kiểm tra gateway: cấu hình admin, log, callback/webhook, lưu giao dịch

Ngày kiểm: 2026-10-08. Kiểm bằng hai cách:

1. Đọc code ở cả ba repo (`bankSimulate/`, `be/`, `fe/`).
2. Chạy thật 11 tình huống thanh toán (thành công lẫn thất bại) trên gateway tạm (cổng 28090, DB tạm
   `payment_lab_intern`), rồi đọc từng cột trong DB. Gateway và DB tạm đã xóa sau khi kiểm. Không sửa dòng code nào.

Tài liệu này là **ảnh chụp tại ngày kiểm**. Code đổi sau ngày này thì một số kết luận có thể không còn đúng.

## Tóm tắt

| Câu hỏi | Trả lời ngắn |
|---|---|
| Admin cấu hình acquirer được không? | **Được**: tạo, sửa, bật/tắt. Chưa xóa được |
| Admin cấu hình bank được không? | **Không**. Ngân hàng chi tiền viết cứng trong code; mock bank không có gì để cấu hình |
| BE có log dạng `[...][merNo-terNo-tradeNo-orderNo]`? | **Có**, cùng chuẩn với gateway. Phần đầu là nguồn của luồng (`MOCK`, `PAYOS`…), ngoài luồng thanh toán mới là `BACKEND` |
| Có callback, webhook không? | **Có**: bank → gateway (callback 3DS), gateway → Encore (webhook). Cả hai ký HMAC |
| Gateway DB có lưu giao dịch? | **Có**, mọi phương thức |
| Lúc thanh toán thất bại có lưu không? | **Có**, nếu giao dịch đã được tạo. Bị từ chối trước khi tạo thì chỉ có log |
| Có lưu resCode, resMsg, mã/thông điệp gốc của bank? | **Một phần**. Mã của gateway: có. Thông điệp của gateway: cột có nhưng luôn trống. **Mã gốc của bank: không lưu, chỉ có trong log** |

Phát hiện thêm 3 lỗi (mục 6).

---

## 1. Admin cấu hình được gì

Admin thao tác trên **Gateway Admin Portal** (FE, `/gateway-admin/...`). FE gọi BE `/api/v1/admin/gateway/**`
(`GatewayAdminProxyController`), BE gắn `X-Admin-Key` rồi gọi gateway `/api/v1/admin/**`. Khóa admin không bao giờ
xuống trình duyệt.

| Thứ | Làm được | Chưa làm được | Màn hình FE |
|---|---|---|---|
| **Acquirer** | Tạo (mã, tên, phương thức nhận, có 3DS không), sửa, bật/tắt | Xóa | `AcquirersPage` |
| **Acquirer Connection** (merchant ↔ acquirer, kèm MID/TID) | Xem, thêm | Sửa MID/TID, tắt, xóa | `MerchantDetailPage` |
| **Routing profile** | Xem, tạo profile, thêm rule | Xóa rule, đổi thứ tự ưu tiên, tắt rule hoặc profile | `RoutingProfilesPage` |
| **Merchant** | Xem, sửa (tên, webhook, trạng thái), đổi secret | — | `MerchantDetailPage` |
| **Terminal** | Tạo, bật/tắt, cấu hình phương thức + 3DS + routing, chuyển terminal Encore đang dùng | — | `MerchantDetailPage`, `TerminalConfigPage` |
| **Ngân hàng chi tiền** (đích hoàn tiền) | **Không** | Viết cứng 3 ngân hàng trong `bankSimulate/.../config/BankProfileConfiguration.java`: MB `970422`, VietinBank `970415`, Vietcombank `970436` | — |
| **Mock bank** | **Không** | Thẻ test, OTP `123456`, số lần nhập sai nằm trong code; địa chỉ từng bank nằm trong biến môi trường `gateway.mock-bank.<mã>.base-url` | — |

Ghi chú:

- Bảng `routing_rules` có cột `enabled`, và runtime có đọc cột này, nhưng không có API nào đổi nó. Mọi rule tạo ra đều
  bật mãi.
- Tài khoản nhận tiền (settlement) của merchant: gateway có API `PUT /api/v1/admin/merchants/{merNo}/settlement-account`,
  nhưng BE cố ý không mở cho admin. Ban tổ chức tự khai trên Encore, BE đẩy lên gateway.

---

## 2. Log của BE

### 2.1 Định dạng

Đã có, cùng chuẩn với gateway. Code: `be/.../domain/common/LogContext.java` + `infrastructure/logging/LogPrefixConverter.java`.

```
[SOURCE][merNo-terNo-tradeNo-orderNo][refundId=…][trace=…]
```

- `SOURCE` là nguồn của luồng đang chạy: `MOCK` (BankSim), `PAYOS`, `WALLET`… Ngoài mọi luồng thanh toán thì là
  `BACKEND`. Không phải lúc nào cũng là `BACKEND`.
- Mã nào chưa biết thì in `-`.
- `refundId` và `trace` chỉ hiện khi có.

Gateway dùng chuẩn tương tự, nhưng phần đầu là vai đang "nói": `[GATEWAY]`, `[BANK_A]`, `[MERCHANT]`.

### 2.2 Mẫu lấy từ log thật (E2E ngày 2026-10-08)

```
… BankSimPaymentGateway  : [MOCK][MerNo000010-TerNo000014---1791464540136796][trace=e10b0366-…] -> banksim POST /api/v1/gateway/payments ok status=200 in 105ms
… CheckoutServiceImpl    : [MOCK][MerNo000010-TerNo000014-TradeNo000001-1791464540136796][trace=e10b0366-…] Payment link created: …
… PaymentServiceImpl     : [MOCK][MerNo000010-TerNo000014-TradeNo000001-1791464540136796][trace=d3193cc0-…] Order ************6796 PAID, 1 vé
… PaymentServiceImpl     : [MOCK][-------][trace=d3193cc0-…] running handling webhook --
```

Cùng một `TradeNo…` xuất hiện ở log của cả Encore lẫn gateway, nên `grep TradeNo000001` ra trọn câu chuyện ở hai phía.

Đếm trên toàn bộ log của lần chạy đó:

| Prefix | Số dòng |
|---|---|
| `[BACKEND][-------]` (ngoài luồng thanh toán) | 160 |
| `[MOCK][MerNo…-TerNo…-TradeNo…-<order>]` (đủ 4 mã) | 18 |
| `[BACKEND][MerNo…-TerNo…----]` | 13 |
| `[MOCK][------<order>]` | 9 |
| `[MOCK][MerNo…-TerNo…---<order>]` (chưa có tradeNo) | 9 |
| `[MOCK][-------]` | 9 |

### 2.3 Chỗ chưa trọn

- **Đầu luồng webhook, prefix còn trống** (`[MOCK][-------]`): lúc này chưa đọc payload nên chưa biết giao dịch nào.
  Đọc xong mới có mã.
- **orderCode trong nội dung log bị che** (`Order ************6796 PAID`): `MaskingMessageConverter` tưởng chuỗi số dài là
  số tài khoản. Phần prefix vẫn hiện đủ, nên vẫn grep được.
- `BankSimAdminClient` có một dòng tự viết tay tiền tố: `log.error("[BACKEND] cannot connect to [PAYMENT-GATEWAY]")`,
  nên dòng đó hiện hai lần `[BACKEND]`.

---

## 3. Callback và webhook

| Chiều | Endpoint | Bảo vệ | Ghi chú |
|---|---|---|---|
| Bank → gateway (callback) | `POST /gateway-callback/{bank}` (`BankCallbackController`) | Header `X-Bank-Signature` = HMAC-SHA256(body, `gateway.bank-callback-secret`) | Chỉ bank kiểu 3DS dùng, sau bước OTP hoặc khi khách hủy. Bank không 3DS trả kết quả ngay trong lúc được gọi (`MockBankBDtos` có field `callback` nhưng không dùng) |
| Gateway → Encore (webhook thẻ) | URL webhook của merchant (`MerchantWebhookSender`) | Header `X-Mock-Signature` = HMAC-SHA256(body, secret của merchant) | Gửi **sau** khi đã commit |
| Gateway → Encore (webhook QR/ví) | như trên (`GatewayRuntimeService.postPaymentWebhook`) | như trên | Có thêm `payerBankBin`, `payerAccountNumber` để hoàn tiền QR về đúng người trả |
| Encore nhận | `POST /webhooks/mock-gateway/payment`, `POST /webhooks/payos/payment` | Kiểm HMAC bằng secret của merchant sở hữu giao dịch | Webhook hoàn tiền: **không có** |

### 3.1 Gateway có lưu không

- **Callback từ bank**: không lưu body gốc. Chỉ lưu **kết quả** (trạng thái mới + một dòng lịch sử).
- **Webhook gửi Encore**: **không lưu gì**. Gửi một lần, không thử lại; hỏng thì chỉ có một dòng log
  `Webhook delivery failed`. Bảng `webhook_deliveries` (có từ `V3`, có sẵn cột `attempt_count`, `next_retry_at`) không
  code nào dùng.
- Mất webhook thì Encore có lưới an toàn: `PaymentReconcileJob` mỗi 5 phút hỏi lại `GET /api/v1/gateway/payments/{id}`.

### 3.2 Encore có lưu không

Có, khá đủ:

| Bảng (Encore) | Lưu gì |
|---|---|
| `webhook_events` | Mỗi webhook nhận: `raw_payload`, `signature_valid`, `processing_result` (`PROCESSED` / `DUPLICATE` / `IGNORED` / `REJECTED_SIGNATURE` / `FAILED`) |
| `payments.raw_webhook_payload` | Payload webhook cuối cùng áp vào payment, **kể cả webhook thất bại** (`Payment.markFailed`) |
| `gateway_call_logs` | Mỗi lần gọi gateway: `createPaymentLink`, `cancelPaymentLink`, `getPaymentStatus`, `submitRefund`, `getRefundStatus`, kèm request (đã che), response, HTTP status, thời gian, traceId. Webhook nhận vào cũng được ghi với chiều `INBOUND` |

---

## 4. Gateway DB lưu giao dịch thế nào

### 4.1 Các bảng

| Bảng | Có gì |
|---|---|
| `payment_transactions` | Bảng chính, **mọi phương thức**: trạng thái, số tiền, `transaction_ref` (khi thành công), kết quả 3DS, tài khoản settlement. **Không có cột mã lỗi / lý do** |
| `payment_attempts` | Mỗi giao dịch một dòng: `state`, `result_code`, `result_message`, `downstream_reference` |
| `transaction_events` | Lịch sử sự kiện của `payment_transactions` (`event_type`) |
| `gateway_transactions` | **Chỉ thẻ**: `result_code`, `result_message`, `bank_code`, `bank_ref`, `auth_code`, `card_masked`, `failure_reason` |
| `gateway_transaction_events` | Lịch sử đổi trạng thái thẻ: từ → tới, **ai gây ra** (`MERCHANT` / `GATEWAY` / `BANK` / `JOB`), nội dung |
| `sandbox_ledger_entries` | Sổ cái (tiền đi đâu) |

Hoàn tiền **không** lưu DB: nằm trong RAM của `GatewayRuntimeService`, restart là mất.

> **Cập nhật cùng ngày:** đã sửa. Refund lưu ở `refund_transactions` (`RefundPayoutService`, migration `V13`), gắn với
> giao dịch gốc; restart gateway không còn mất refund.

### 4.2 Các tình huống đã chạy

| Mã đơn | Tình huống |
|---|---|
| 3001 | Thẻ `…1000`, nhập OTP đúng |
| 3002 | Thẻ `…3000`, bank từ chối ngay |
| 3003 | Thẻ `…1000`, nhập OTP sai 3 lần |
| 3004 | Thẻ `…1000`, khách bấm Hủy ở trang bank |
| 3005 | Thẻ `…1000`, khách bỏ dở ở trang OTP, job hết hạn chốt (hạn đặt 20 giây để test) |
| 3006 | Merchant hủy trước khi khách nhập thẻ |
| 3007 | Terminal chỉ route tới một acquirer trỏ vào cổng chết (mọi bank đều sập) |
| 3008 | Thanh toán PAYNOW mà terminal chưa bật → 409 `PAYMENT_METHOD_NOT_ENABLED` |
| 4001 | QR thành công |
| 4002 | QR, bấm "Mô phỏng thất bại" |
| 4003 | QR, bấm "Mô phỏng hết hạn" |
| 4004 | QR 150.000.000đ, tài khoản khách mô phỏng chỉ có 100.000.000đ |

### 4.3 Kết quả: `gateway_transactions` (thẻ)

```
 order | status  | rc | result_message | bank_code | bank_ref         | auth_code  | card_masked      | failure_reason
-------+---------+----+----------------+-----------+------------------+------------+------------------+------------------------------------------
 3001  | SUCCESS | 02 |                | bank-a    | TDS-3A15070C-8FE | AUTH10D52D | 400000******1000 |
 3002  | FAILED  | 01 |                | bank-a    | TDS-BFA48A4A-592 |            | 400000******3000 | Card not accepted by issuer
 3003  | FAILED  | 01 |                | bank-a    | TDS-4C79DC94-4FC |            | 400000******1000 | Authentication failed after 3 attempts
 3004  | FAILED  | 01 |                | bank-a    | TDS-79792E22-CA3 |            | 400000******1000 | Cardholder cancelled at bank page
 3005  | EXPIRED | 04 |                | bank-a    | TDS-9316C720-FBB |            | 400000******1000 | Hết thời gian chờ chủ thẻ xác thực
 3006  | FAILED  | 05 |                | bank-a    |                  |            |                  | Cancelled by merchant
 3007  | FAILED  | 01 |                | bank-dead |                  |            | 400000******1000 | Đã thử 1 bank, không bank nào nhận được lệnh.
       |         |    |                |           |                  |            |                  | Lỗi cuối: bank-dead không kết nối được: I/O error on POST
       |         |    |                |           |                  |            |                  | request for "http://localhost:1/mock-bank/bank-dead/v1/authorize": null
```

Cột `result_message` **trống ở cả 7 dòng**.

### 4.4 Kết quả: `gateway_transaction_events`

```
 order | thay đổi             | ai       | nội dung
-------+----------------------+----------+-----------------------------------------------------------
 3001  | -→CREATED            | MERCHANT | Created for bank bank-a amount=100000 cardSupplied=false
 3001  | CREATED→PENDING_AUTH | BANK     | Bank requires cardholder authentication
 3001  | PENDING_AUTH→SUCCESS | BANK     | Approved
 3002  | -→CREATED            | MERCHANT | Created for bank bank-a …
 3002  | CREATED→FAILED       | BANK     | Card not accepted by issuer
 3003  | CREATED→PENDING_AUTH | BANK     | Bank requires cardholder authentication
 3003  | PENDING_AUTH→FAILED  | BANK     | Authentication failed after 3 attempts
 3004  | PENDING_AUTH→FAILED  | BANK     | Cardholder cancelled at bank page
 3005  | PENDING_AUTH→EXPIRED | JOB      | Expired after waiting for cardholder
 3006  | CREATED→FAILED       | MERCHANT | Cancelled by merchant
 3007  | CREATED→CREATED      | GATEWAY  | Bank bank-dead lỗi BANK_UNREACHABLE, hết bank để thử
 3007  | CREATED→FAILED       | GATEWAY  | Đã thử 1 bank, không bank nào nhận được lệnh. …
```

(Rút gọn: bỏ bớt các dòng `-→CREATED` lặp lại.) Mỗi lần thử một bank hỏng cũng có một dòng (đơn 3007).

### 4.5 Kết quả: `payment_transactions` + `payment_attempts` (mọi phương thức)

```
 order | m    | status    | amount_paid | transaction_ref               | 3ds           | attempt.state | attempt.result_code | attempt.result_message
-------+------+-----------+-------------+-------------------------------+---------------+---------------+---------------------+-----------------------
 3001  | CARD | PAID      |      100000 | TDS-3A15070C-8FE              | AUTHENTICATED | APPROVED      |                     |
 3002  | CARD | DECLINED  |           0 |                               |               | DECLINED      | DECLINED            |
 3003  | CARD | DECLINED  |           0 |                               | CHALLENGE     | DECLINED      | DECLINED            |
 3004  | CARD | DECLINED  |           0 |                               | CHALLENGE     | DECLINED      | DECLINED            |
 3005  | CARD | EXPIRED   |           0 |                               | CHALLENGE     | EXPIRED       | EXPIRED             |
 3006  | CARD | CANCELLED |           0 |                               |               | CANCELLED     | CANCELLED           |
 3007  | CARD | DECLINED  |           0 |                               |               | DECLINED      | DECLINED            |
 4001  | QR   | PAID      |      100000 | BS1791466439465_QR_PROVIDER_A | NOT_REQUIRED  | APPROVED      |                     |
 4002  | QR   | CANCELLED |           0 |                               | NOT_REQUIRED  | CANCELLED     | CANCELLED           |
 4003  | QR   | EXPIRED   |           0 |                               | NOT_REQUIRED  | EXPIRED       | EXPIRED             |
 4004  | QR   | DECLINED  |           0 |                               | NOT_REQUIRED  | DECLINED      | DECLINED            |
```

- `attempt.result_code` chỉ là bản sao của trạng thái, không phải mã lỗi thật.
- `attempt.result_message` trống ở cả 11 dòng.
- Đơn 3008 (PAYNOW chưa bật): **0 dòng** trong mọi bảng.

### 4.6 Kết quả: `transaction_events`

```
 3001 | PAYMENT_CREATED > 3DS_CHALLENGE > 3DS_AUTHENTICATED > BANK_APPROVED > SETTLEMENT_DESTINATION_NOT_CONFIGURED
 3002 | PAYMENT_CREATED > PAYMENT_FINALIZED
 3005 | PAYMENT_CREATED > 3DS_CHALLENGE > PAYMENT_FINALIZED
 4001 | PAYMENT_CREATED > 3DS_NOT_REQUIRED > AUTO_CAPTURE > SETTLEMENT_DESTINATION_NOT_CONFIGURED > ISSUER_APPROVED
 4002 | PAYMENT_CREATED > 3DS_NOT_REQUIRED > PAYMENT_FINALIZED
 4004 | PAYMENT_CREATED > 3DS_NOT_REQUIRED > ISSUER_INSUFFICIENT_FUNDS
```

Đơn 4004 chỉ còn thấy lý do "không đủ tiền" ở đây (xem lỗi 6.1). Thứ tự của đơn 4001 bị sai (xem lỗi 6.2).

### 4.7 Mã gốc của bank: chỉ có trong log

```
[BANK_A][MerNo000001-TerNo000001-TradeNo000001-3001] … Bank answered outcome=REDIRECT_REQUIRED bankRawCode=05 bankRef=TDS-3A15070C-8FE
[BANK_A][MerNo000001-TerNo000001-TradeNo000001-3001] … Bank notification received bankRef=TDS-3A15070C-8FE approved=true message=Approved
[BANK_A][MerNo000001-TerNo000001-TradeNo000002-3002] … Bank answered outcome=DECLINED bankRawCode=99 bankRef=TDS-BFA48A4A-592
[BANK_A][MerNo000001-TerNo000001-TradeNo000003-3003] … Bank notification received bankRef=TDS-4C79DC94-4FC approved=false message=Authentication failed after 3 attempts
```

`bankRawCode` (`00` / `05` / `99` với bank 3DS, `APPROVED` / `DECLINED` với bank trả thẳng) được in ở
`CardPaymentService.authorizeWithBank` rồi bỏ đi. Callback từ bank cũng có `respCode` riêng, nhưng
`BankCallbackController` chỉ đổi nó thành `true`/`false` (`approved`) rồi bỏ mã gốc.

---

## 5. Trả lời thẳng: có lưu resCode, resMsg, bank res, bank res msg không

| Thứ | Lưu không | Ở đâu | Ghi chú |
|---|---|---|---|
| **resCode** (mã kết quả của gateway, `01`–`05`) | **Có, chỉ với thẻ** | `gateway_transactions.result_code` | QR/ví không có mã, chỉ có `status` |
| **resMsg** (thông điệp kết quả của gateway) | **Không** | `gateway_transactions.result_message` | Cột có từ `V11`, entity có field, nhưng **không code nào ghi vào**. `payment_attempts.result_message` cũng vậy |
| **bank res** (mã gốc của bank) | **Không** | — | Chỉ trong log `bankRawCode=` |
| **bank res msg** (thông điệp của bank) | **Một phần** | `failure_reason` khi thất bại; `gateway_transaction_events.detail` mọi lúc | Khi thành công (`Approved`) chỉ nằm ở bảng lịch sử, không có cột riêng |
| Mã tham chiếu bank | **Có** | `gateway_transactions.bank_ref` (cả khi thất bại), `payment_transactions.transaction_ref` (khi PAID) | |
| Mã duyệt (auth code) | **Có, khi thành công** | `gateway_transactions.auth_code` | |
| Request / response gốc gửi bank | **Không** | — | |
| Body callback bank gửi về | **Không** | — | |
| Webhook đã gửi cho merchant | **Không** | — | `webhook_deliveries` bỏ trống |

### Lúc thanh toán thất bại có lưu không

**Có, với mọi thất bại xảy ra sau khi giao dịch đã được tạo.** Đã thấy đủ: bank từ chối, OTP sai 3 lần, khách hủy ở
trang bank, hết hạn, merchant hủy, mọi bank đều sập (cả 7 tình huống thẻ), cùng QR thất bại / hết hạn / không đủ tiền.

| Loại thất bại | Lưu gì |
|---|---|
| Thẻ | Trạng thái + `result_code` + `failure_reason` (lý do dạng chữ) + `bank_ref` (nếu bank đã trả) + đủ lịch sử |
| QR / ví | Chỉ trạng thái (`DECLINED` / `CANCELLED` / `EXPIRED`) và tên sự kiện. Không có lý do dạng chữ |
| Bị từ chối **trước khi tạo** giao dịch (phương thức chưa bật, routing thiếu, sai secret, merchant/terminal tắt, sai tổng tiền…) | **Không lưu gì trong DB.** Chỉ có dòng log `Rejected status=… code=…` (`ApiExceptionHandler`) |

---

## 6. Lỗi phát hiện

### 6.1 Lý do thất bại bị ghi đè

Đơn QR 4004 (không đủ tiền): `GatewayMoneyService.capture` ghi đúng `payment_attempts.result_code =
'ISSUER_INSUFFICIENT_FUNDS'`. Ngay sau đó `GatewayRuntimeService.completePayment` gọi `money.markFinal(p.id, "DECLINED")`.
Câu SQL ở `GatewayMoneyService.java:218` cập nhật `payment_attempts` **không có điều kiện**:

```sql
UPDATE payment_attempts SET state = ?, response_received_at = now(), result_code = ? WHERE payment_id = (...)
```

nên lý do bị ghi đè thành `DECLINED`. Lý do thật chỉ còn trong `transaction_events`.

### 6.2 Thứ tự sự kiện trong `transaction_events` không tin được

Cột `created_at DEFAULT now()`. Trong PostgreSQL, `now()` là giờ **bắt đầu transaction**, nên mọi sự kiện ghi trong
cùng một transaction có giờ giống hệt nhau. Sắp xếp theo giờ thì ra thứ tự tùy ý. Ví dụ đơn 4001 hiện `AUTO_CAPTURE`
trước `ISSUER_APPROVED`, trong khi thực tế duyệt xảy ra trước.

`gateway_transaction_events` không bị vì lấy giờ từ Java (`Instant.now()`).

### 6.3 QR gửi trùng `orderCode` trả 500

Đã ghi ở `GATEWAY-CODE-WALKTHROUGH.md` mục 8.1. Luồng QR không tìm giao dịch cũ trước khi `INSERT` vào
`payment_transactions`, nên đụng `UNIQUE (merchant_id, terminal_id, order_code)` → `DuplicateKeyException` → 500
`INTERNAL_ERROR`. Luồng thẻ thì trả lại giao dịch cũ.

### 6.4 Chuyện nhỏ

- `failure_reason` của đơn 3007 chứa nguyên văn lỗi kỹ thuật, kể cả URL nội bộ
  (`I/O error on POST request for "http://localhost:1/..."`). Field này được gửi trong webhook (`message`), tức là
  merchant thấy được.
- `routing_rules.enabled` không có API để đổi (mục 1).

---

> **Cập nhật cùng ngày:** 6.1 (lý do bị ghi đè), 6.2 (thứ tự sự kiện, cả `sandbox_ledger_entries`) và 6.3 (QR gửi trùng
> `orderCode` ra 500) đã sửa khi chuyển luồng QR/ví sang lưu DB (`QrPaymentService`). Mục 7.b vì thế đã xong.

## 7. Đề xuất sửa (chưa làm)

Xếp theo mức hữu ích cho việc tra soát giao dịch:

| # | Việc | Phạm vi |
|---|---|---|
| a | Lưu mã + thông điệp gốc của bank vào cột riêng (vd `bank_resp_code`, `bank_resp_message` trên `gateway_transactions`), ghi `result_message`, và lưu `respCode` của callback | Migration gateway + `CardPaymentService`, `GatewayTransaction`, `BankCallbackController` |
| b | Sửa 6.1 (không ghi đè lý do), 6.2 (`clock_timestamp()` hoặc giờ từ Java), 6.3 (QR idempotent theo `orderCode`) | `GatewayMoneyService`, `GatewayRuntimeService`, một migration nhỏ |
| c | Ghi lại mỗi lần gửi webhook vào `webhook_deliveries` (đã có sẵn cột), có thể thêm cơ chế thử lại | `MerchantWebhookSender`, `postPaymentWebhook`, một job mới |
| d | Admin tắt/sửa Acquirer Connection, xóa/tắt routing rule | API gateway + proxy BE + FE |
| e | Lưu các yêu cầu bị từ chối trước khi tạo giao dịch (nếu cần tra soát cả những lần merchant gọi sai) | Tùy nhu cầu; hiện chỉ có log |

---

## Phụ lục: cách tái hiện

Cần PostgreSQL local, `jq`, và `bankSimulate/.env` (cung cấp `GATEWAY_ADMIN_KEY`, `GATEWAY_MASTER_KEY`, `DB_USERNAME`,
`DB_PASSWORD`). Chạy gateway trên DB tạm, hạn thẻ ngắn để thấy được job hết hạn, và một acquirer trỏ vào cổng chết:

```bash
cd bankSimulate && set -a; . ./.env; set +a
PGPASSWORD="$DB_PASSWORD" createdb -h localhost -U "$DB_USERNAME" payment_lab_intern
DB_URL=jdbc:postgresql://localhost:5432/payment_lab_intern GATEWAY_SEED_ENABLED=true SERVER_PORT=28090 \
GATEWAY_PUBLIC_BASE_URL=http://localhost:28090 GATEWAY_MOCK_BANK_URL=http://localhost:28090 \
GATEWAY_CARD_AUTH_TTL=PT20S GATEWAY_CARD_EXPIRY_INTERVAL=PT5S \
GATEWAY_MOCK_BANK_BANK_DEAD_BASE_URL=http://localhost:1 \
./mvnw spring-boot:run
```

Sau đó chạy script dưới đây ở terminal khác.

<details><summary>Script chạy 11 tình huống và đọc DB</summary>

```bash
#!/usr/bin/env bash
set -u
cd bankSimulate && set -a && . ./.env && set +a
GW=http://localhost:28090
export PGPASSWORD="$DB_PASSWORD"
q() { psql -h localhost -U "$DB_USERNAME" -d payment_lab_intern -P pager=off "$@"; }
A=(-H "X-Admin-Key: $GATEWAY_ADMIN_KEY" -H 'Content-Type: application/json')

O=$(curl -s -X POST $GW/api/v1/admin/merchant-onboarding "${A[@]}" -d '{"name":"Shop kiem tra","externalReference":"STORE_CHECK"}')
MER=$(echo "$O" | jq -r .merchantNo); TER=$(echo "$O" | jq -r .terminalId); SECRET=$(echo "$O" | jq -r .merchantSecret)
H=(-H "X-Merchant-No: $MER" -H "X-Terminal-Id: $TER" -H "X-Merchant-Secret: $SECRET")

# terminal thứ hai chỉ route tới một acquirer "sập"
curl -s -X POST $GW/api/v1/admin/acquirers "${A[@]}" -d '{"code":"bank-dead","name":"Bank sap","paymentMethods":["CARD"],"threeDsSupported":true}' >/dev/null
curl -s -X POST $GW/api/v1/admin/routing-profiles "${A[@]}" -d '{"code":"DEAD_ONLY","name":"Chi bank-dead"}' >/dev/null
curl -s -X POST $GW/api/v1/admin/routing-profiles/DEAD_ONLY/rules "${A[@]}" -d '{"paymentMethod":"CARD","acquirerCode":"bank-dead","priority":1}' >/dev/null
curl -s -X POST $GW/api/v1/admin/merchants/$MER/acquirer-configs "${A[@]}" -d '{"acquirerCode":"bank-dead","mid":"MID-DEAD","tid":"TID-DEAD"}' >/dev/null
TER2=$(curl -s -X POST $GW/api/v1/admin/merchants/$MER/terminals "${A[@]}" -d '{"name":"Dead","channel":"WEB","currency":"VND","paymentMethods":["CARD"],"threeDsPolicy":"OPTIONAL","routingProfileCode":"DEAD_ONLY"}' | jq -r .terminalId)

pay() { # $1 order $2 method $3 amount [$4 terminal]
  local t=${4:-$TER}
  curl -s -X POST $GW/api/v1/gateway/payments -H "X-Merchant-No: $MER" -H "X-Terminal-Id: $t" -H "X-Merchant-Secret: $SECRET" \
    -H 'Content-Type: application/json' \
    -d "{\"orderCode\":$1,\"amount\":$3,\"description\":\"x\",\"paymentMethod\":\"$2\",\"items\":[{\"name\":\"Ve\",\"quantity\":1,\"price\":$3}],\"returnUrl\":\"http://localhost:3000/ok\",\"cancelUrl\":\"http://localhost:3000/cancel\"}"
}
card_submit() { curl -s -o /dev/null -w "%{redirect_url}" -X POST "$GW/card-checkout/$1" -d pan=$2 -d expiryMonth=12 -d expiryYear=2030 -d cvv=123; }
tid() { echo "$1" | jq -r .providerPaymentId; }

T=$(tid "$(pay 3001 CARD 100000)"); L=$(card_submit $T 4000000000001000); curl -s -o /dev/null -X POST "$L" -d otp=123456
T=$(tid "$(pay 3002 CARD 100000)"); card_submit $T 4000000000003000 >/dev/null
T=$(tid "$(pay 3003 CARD 100000)"); L=$(card_submit $T 4000000000001000); for i in 1 2 3; do curl -s -o /dev/null -X POST "$L" -d otp=000000; done
T=$(tid "$(pay 3004 CARD 100000)"); L=$(card_submit $T 4000000000001000); curl -s -o /dev/null -X POST "$L/cancel"
T5=$(tid "$(pay 3005 CARD 100000)"); card_submit $T5 4000000000001000 >/dev/null
T=$(tid "$(pay 3006 CARD 100000)"); curl -s -o /dev/null -X POST $GW/api/v1/gateway/payments/$T/cancel "${H[@]}"
T=$(tid "$(pay 3007 CARD 100000 $TER2)"); card_submit $T 4000000000001000 >/dev/null
pay 3008 PAYNOW 100000 | jq -c '{status,code}'
T=$(tid "$(pay 4001 QR 100000)"); curl -s -o /dev/null -X POST "$GW/checkout/$T/succeed?threeDs=false"
T=$(tid "$(pay 4002 QR 100000)"); curl -s -o /dev/null -X POST "$GW/checkout/$T/fail?threeDs=false"
T=$(tid "$(pay 4003 QR 100000)"); curl -s -o /dev/null -X POST "$GW/checkout/$T/expire"
T=$(tid "$(pay 4004 QR 150000000)"); curl -s -o /dev/null -X POST "$GW/checkout/$T/succeed?threeDs=false"

# job hết hạn chạy lần đầu sau 30 giây
for i in $(seq 1 30); do [ "$(q -Atc "select status from gateway_transactions where gw_txn_id='$T5'")" = EXPIRED ] && break; sleep 2; done

q -c "select order_code, status, result_code rc, result_message, bank_code, bank_ref, auth_code, card_masked, failure_reason from gateway_transactions order by order_code"
q -c "select t.order_code, coalesce(e.from_status,'-')||'→'||e.to_status as change, e.source, left(e.detail,90) detail from gateway_transaction_events e join gateway_transactions t on t.id=e.transaction_id order by t.order_code, e.created_at"
q -c "select order_code, payment_method m, status, amount_paid, transaction_ref, three_ds_result from payment_transactions order by order_code"
q -c "select p.order_code, a.state, a.result_code, a.result_message, a.downstream_reference from payment_attempts a join payment_transactions p on p.id=a.payment_id order by p.order_code"
q -c "select p.order_code, string_agg(e.event_type, ' > ' order by e.created_at) from transaction_events e join payment_transactions p on p.id=e.payment_id group by p.order_code order by p.order_code"
```

</details>

Xong thì tắt gateway và `PGPASSWORD="$DB_PASSWORD" dropdb -h localhost -U "$DB_USERNAME" payment_lab_intern`.
