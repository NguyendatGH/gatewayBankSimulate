package com.bankSimulate.application;

import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.enums.PaymentMethod;
import com.bankSimulate.infrastructure.logging.GatewayLogContext;
import com.bankSimulate.infrastructure.persistence.TerminalPaymentMethodRepository;
import com.bankSimulate.infrastructure.web.dto.GatewayRuntimeDtos;
import org.springframework.stereotype.Service;

@Service
public class GatewayRuntimeService {

    private final TerminalPaymentMethodRepository methods;
    private final CardPaymentService cards;
    private final QrPaymentService qr;
    private final GatewayMoneyService money;
    private final GooglePaySandboxBank googlePay;

    public GatewayRuntimeService(TerminalPaymentMethodRepository methods, CardPaymentService cards, QrPaymentService qr,
                                 GatewayMoneyService money, GooglePaySandboxBank googlePay) {
        this.methods = methods;
        this.cards = cards;
        this.qr = qr;
        this.money = money;
        this.googlePay = googlePay;
    }

    public GatewayRuntimeDtos.PaymentResponse createPayment(GatewayRuntimeAuth.Access access,
                                                            GatewayRuntimeDtos.CreatePaymentRequest request) {
        GatewayLogContext.setOrderNo(Long.toString(request.orderCode()));
        PaymentMethod selected = parseMethod(request.paymentMethod());
        if (selected == PaymentMethod.GOOGLE_PAY && !googlePay.enabled())
            throw new ApiException(409, "PAYMENT_METHOD_NOT_ENABLED", "GOOGLE_PAY is disabled on this gateway");
        if (!methods.existsByTerminalIdAndPaymentMethod(access.terminal().getId(), selected))
            throw new ApiException(409, "PAYMENT_METHOD_NOT_ENABLED", selected + " is not enabled on terminal");
        money.findByOrder(access.merchant().getId(), access.terminal().getId(), Long.toString(request.orderCode()))
                .filter(existing -> !existing.paymentMethod().equals(selected.name()))
                .ifPresent(existing -> {
                    throw new ApiException(409, "ORDER_CODE_ALREADY_USED", "orderCode " + request.orderCode()
                            + " was already used for " + existing.paymentMethod() + " payment " + existing.providerPaymentId());
                });
        return selected == PaymentMethod.CARD
                ? cards.createFromPayments(access, request)
                : qr.create(access, request, selected);
    }

    public GatewayRuntimeDtos.PaymentStatusResponse paymentStatus(GatewayRuntimeAuth.Access access, String tradeNo) {
        return cards.exists(tradeNo) ? cards.paymentStatus(access, tradeNo).orElseThrow() : qr.status(access, tradeNo);
    }

    public void cancelPayment(GatewayRuntimeAuth.Access access, String tradeNo) {
        if (cards.exists(tradeNo)) cards.cancel(access, tradeNo);
        else qr.cancel(access, tradeNo);
    }

    private static PaymentMethod parseMethod(String raw) {
        try {
            return raw == null || raw.isBlank() ? PaymentMethod.CARD : PaymentMethod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(400, "PAYMENT_METHOD_NOT_SUPPORTED", "Unsupported payment method");
        }
    }
}
