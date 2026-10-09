/**
 * Google Pay (sandbox) gom MỘT chỗ để khỏi lẫn với QR/ví khác. Luồng:
 * <pre>
 * trang /checkout/{id}  --(GooglePayCheckoutPage: nút Google Pay chính thức, pay.js, TEST)-->
 *   POST /checkout/{id}/google-pay  (GooglePayController)
 *     -> GooglePayService: kiểm token, chọn kết quả theo kịch bản (GooglePaySandboxBank)
 *        -> QrPaymentService.complete(...)   // capture, ghi sổ, webhook y hệt QR; không capture hai lần
 * </pre>
 * Token Google Pay TEST chỉ là dữ liệu mẫu: KHÔNG log, KHÔNG giải mã, KHÔNG quyết định kết quả.
 * Phần dùng chung với QR (đơn, capture, webhook, khung trang) vẫn nằm ở {@code QrPaymentService} và {@code QrCheckoutPage}.
 */
package com.bankSimulate.googlepay;
