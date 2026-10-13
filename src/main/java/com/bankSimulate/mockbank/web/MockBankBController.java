package com.bankSimulate.mockbank.web;
import com.bankSimulate.infrastructure.logging.GatewayLogContext;
import com.bankSimulate.mockbank.DirectMockBank;
import com.bankSimulate.mockbank.MockBankAuthorizeInput;
import com.bankSimulate.mockbank.MockBankDecision;
import com.bankSimulate.mockbank.web.dto.MockBankBDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock-bank/{bank}")
public class MockBankBController {

    private final DirectMockBank issuer;

    public MockBankBController(DirectMockBank issuer) {
        this.issuer = issuer;
    }

    @PostMapping("/payment/authorize")
    public MockBankBDtos.AuthorizeResponse authorize(@PathVariable String bank,
                                                     @Valid @RequestBody MockBankBDtos.AuthorizeRequest request) {

        GatewayLogContext.setBank(bank);
        GatewayLogContext.setMerchantNo(request.merchant());
        GatewayLogContext.setTradeNo(request.reference());
        MockBankAuthorizeInput input = new MockBankAuthorizeInput(request.reference(), request.amount(),
                request.currency(), request.pan(), request.expMonth(), request.expYear(), request.cvv(),
                MockBankAuthorizeInput.AuthPreference.NONE, request.callback(), null);

        MockBankDecision decision = issuer.authorize(input);
        boolean approved = decision.kind() == MockBankDecision.Kind.APPROVE;
        return new MockBankBDtos.AuthorizeResponse(
                approved ? MockBankBDtos.STATUS_APPROVED : MockBankBDtos.STATUS_DECLINED,
                decision.bankRef(), decision.message());
    }
}
