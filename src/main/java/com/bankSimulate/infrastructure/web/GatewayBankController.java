package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.BankService;
import com.bankSimulate.domain.bank.BankProfile;
import com.bankSimulate.infrastructure.web.dto.GatewayBankDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/gateway/banks")
public class GatewayBankController {

    private final BankService banks;

    public GatewayBankController(BankService banks) {
        this.banks = banks;
    }

    @GetMapping
    public GatewayBankDtos.SupportedBanksResponse supported() {
        List<GatewayBankDtos.SupportedBank> list = banks.profiles().stream()
                .filter(p -> p.bankBin() != null && !p.bankBin().isBlank())
                .map(p -> new GatewayBankDtos.SupportedBank(p.bankBin(), p.name()))
                .sorted(java.util.Comparator.comparing(GatewayBankDtos.SupportedBank::bin))
                .toList();
        return new GatewayBankDtos.SupportedBanksResponse(list.stream().map(GatewayBankDtos.SupportedBank::bin).toList(), list);
    }
}
