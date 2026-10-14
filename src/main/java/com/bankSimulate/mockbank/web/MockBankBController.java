package com.bankSimulate.mockbank.web;
import com.bankSimulate.infrastructure.logging.GatewayLogContext;
import com.bankSimulate.infrastructure.security.BankRequestSigner;
import com.bankSimulate.mockbank.DirectMockBank;
import com.bankSimulate.mockbank.MockBankAuthorizeInput;
import com.bankSimulate.mockbank.MockBankDecision;
import com.bankSimulate.mockbank.web.dto.MockBankBDtos;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock-bank/{bank}")
public class MockBankBController {

    private final DirectMockBank issuer;
    private final BankRequestSigner requestSigner;
    private final MockBankRequestReader reader;

    public MockBankBController(DirectMockBank issuer, BankRequestSigner requestSigner, MockBankRequestReader reader) {
        this.issuer = issuer;
        this.requestSigner = requestSigner;
        this.reader = reader;
    }

    @PostMapping("/payment/authorize")
    public MockBankBDtos.AuthorizeResponse authorize(@PathVariable String bank, @RequestBody String rawBody,
            @RequestHeader(value = BankRequestSigner.TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = BankRequestSigner.SIGNATURE_HEADER, required = false) String signature) {

        GatewayLogContext.setBank(bank);
        reader.tagLogFromUntrusted(rawBody, "merchant", "reference");
        requestSigner.requireValid(bank, timestamp, rawBody, signature);
        MockBankBDtos.AuthorizeRequest request = reader.read(rawBody, MockBankBDtos.AuthorizeRequest.class);
        GatewayLogContext.setMerchantNo(request.merchant());
        GatewayLogContext.setTradeNo(request.reference());
        MockBankAuthorizeInput input = new MockBankAuthorizeInput(request.merchant(), request.reference(), request.amount(),
                request.currency(), request.pan(), request.expMonth(), request.expYear(), request.cvv(),
                MockBankAuthorizeInput.AuthPreference.NONE, request.callback(), null);

        MockBankDecision decision = issuer.authorize(input);
        boolean approved = decision.kind() == MockBankDecision.Kind.APPROVE;
        return new MockBankBDtos.AuthorizeResponse(
                approved ? MockBankBDtos.STATUS_APPROVED : MockBankBDtos.STATUS_DECLINED,
                decision.bankRef(), decision.message());
    }
}
