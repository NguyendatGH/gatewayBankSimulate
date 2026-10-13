package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.GatewayRuntimeAuth;
import com.bankSimulate.application.TerminalService;
import com.bankSimulate.domain.terminal.Terminal;
import com.bankSimulate.domain.terminal.TerminalPaymentMethod;
import com.bankSimulate.infrastructure.persistence.TerminalPaymentMethodRepository;
import com.bankSimulate.infrastructure.web.dto.GatewayBankDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gateway/terminal")
public class GatewayTerminalController {

    private final GatewayRuntimeAuth auth;
    private final TerminalPaymentMethodRepository methods;
    private final TerminalService terminals;

    public GatewayTerminalController(GatewayRuntimeAuth auth, TerminalPaymentMethodRepository methods,
                                     TerminalService terminals) {
        this.auth = auth;
        this.methods = methods;
        this.terminals = terminals;
    }

    @GetMapping
    public GatewayBankDtos.TerminalInfoResponse info(@RequestHeader("X-Merchant-No") String merNo,
                                                     @RequestHeader("X-Terminal-Id") String terminalId,
                                                     @RequestHeader("X-Merchant-Secret") String secret) {
        GatewayRuntimeAuth.Access access = auth.authenticate(merNo, terminalId, secret);
        Terminal terminal = access.terminal();
        return new GatewayBankDtos.TerminalInfoResponse(
                access.merchant().getMerNo(),
                terminal.getTerminalId(),
                terminal.getChannel().name(),
                terminal.getCurrency(),

                terminals.routableMethods(terminal, access.merchant()).stream().map(Enum::name).sorted().toList(),

                terminal.getThreeDsPolicy() == null ? null : terminal.getThreeDsPolicy().name(),
                methods.findAllByTerminalId(terminal.getId()).stream()
                        .map(TerminalPaymentMethod::getPaymentMethod)
                        .map(Enum::name)
                        .sorted()
                        .toList());
    }
}
