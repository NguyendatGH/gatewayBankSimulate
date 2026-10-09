package com.bankSimulate.googlepay;

import com.bankSimulate.application.QrPaymentService;
import com.bankSimulate.domain.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Nhận token từ nút Google Pay chính thức. Token KHÔNG được log, KHÔNG giải mã và KHÔNG quyết định kết quả:
 * kết quả đến từ kịch bản của {@link GooglePaySandboxBank}. Chốt giao dịch đi qua đúng {@code QrPaymentService.complete}
 * nên capture/ghi sổ/webhook y hệt QR, và gọi lặp lại không capture hai lần (đơn đã chốt thì trả lại kết quả cũ).
 */
@Service
public class GooglePayService {

    private static final Logger log = LoggerFactory.getLogger(GooglePayService.class);
    private static final int MAX_TOKEN_CHARS = 20_000;

    public record Result(String status, String redirectUrl) {}

    private final QrPaymentService payments;
    private final GooglePaySandboxBank bank;

    public GooglePayService(QrPaymentService payments, GooglePaySandboxBank bank) {
        this.payments = payments;
        this.bank = bank;
    }

    public Result pay(String tradeNo, String token, String scenarioRaw) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_CHARS)
            throw new ApiException(400, "GOOGLE_PAY_TOKEN_INVALID", "Google Pay token is missing or too large");
        QrPaymentService.PaymentState state = payments.state(tradeNo);
        if (!"GOOGLE_PAY".equals(state.method()))
            throw new ApiException(409, "PAYMENT_METHOD_MISMATCH", "Payment was not created for GOOGLE_PAY");
        if (!"PENDING".equals(state.status())) return resultOf(state);

        GooglePaySandboxBank.Scenario scenario = bank.resolve(scenarioRaw);
        log.info("Google Pay token received (content not logged), sandbox scenario={}", scenario);
        switch (scenario) {
            case BANK_TIMEOUT -> {
                return new Result("PENDING", null);
            }
            case DECLINED -> payments.complete(tradeNo, QrPaymentService.Action.FAIL);
            case APPROVED -> payments.complete(tradeNo, QrPaymentService.Action.SUCCEED);
        }
        return resultOf(payments.state(tradeNo));
    }

    private static Result resultOf(QrPaymentService.PaymentState state) {
        return switch (state.status()) {
            case "PAID" -> new Result("SUCCEEDED", state.returnUrl());
            case "PENDING" -> new Result("PENDING", null);
            default -> new Result("FAILED", state.cancelUrl());
        };
    }
}
