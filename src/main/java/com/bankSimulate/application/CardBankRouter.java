package com.bankSimulate.application;

import com.bankSimulate.domain.acquirer.Acquirer;
import com.bankSimulate.domain.cardAuth.CardAuthorizationPort;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.enums.PaymentMethod;
import com.bankSimulate.domain.enums.Status;
import com.bankSimulate.domain.merchant.Merchant;
import com.bankSimulate.domain.terminal.Terminal;
import com.bankSimulate.infrastructure.persistence.AcquirerMerchantConfigRepository;
import com.bankSimulate.infrastructure.persistence.AcquirerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CardBankRouter {

    public record CardRoute(String acquirerCode, CardAuthorizationPort port) {
    }

    private final TerminalRoutes routes;
    private final AcquirerRepository acquirers;
    private final AcquirerMerchantConfigRepository acquirerConfigs;
    private final CardAuthorizationPort threeDsPort;
    private final CardAuthorizationPort directPort;

    public CardBankRouter(TerminalRoutes routes, AcquirerRepository acquirers,
                          AcquirerMerchantConfigRepository acquirerConfigs, List<CardAuthorizationPort> ports) {
        this.routes = routes;
        this.acquirers = acquirers;
        this.acquirerConfigs = acquirerConfigs;
        this.threeDsPort = ports.stream().filter(CardAuthorizationPort::threeDs).findFirst().orElseThrow();
        this.directPort = ports.stream().filter(p -> !p.threeDs()).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public CardRoute routeFor(String acquirerCode) {
        Acquirer acquirer = acquirers.findByCode(acquirerCode).orElseThrow(() -> new ApiException(409,
                "NO_CARD_BANK_AVAILABLE", "Acquirer " + acquirerCode + " no longer exists"));
        return route(acquirer);
    }

    @Transactional(readOnly = true)
    public List<CardRoute> resolveAll(Merchant merchant, Terminal terminal) {
        List<Acquirer> candidates = routes.plan(terminal, PaymentMethod.CARD).acquirers();
        if (candidates.isEmpty())
            throw new ApiException(409, "ROUTING_NOT_CONFIGURED", "No route configured for CARD");

        List<CardRoute> usable = new ArrayList<>();
        for (Acquirer acquirer : candidates) {
            if (!acquirer.accepts(PaymentMethod.CARD)
                    || !acquirer.meetsThreeDs(PaymentMethod.CARD, terminal.getThreeDsPolicy())) continue;
            if (!acquirerConfigs.existsByAcquirerIdAndMerchantIdAndStatus(acquirer.getId(), merchant.getId(), Status.ACTIVE))
                continue;
            usable.add(route(acquirer));
        }
        if (usable.isEmpty()) throw new ApiException(409, "NO_CARD_BANK_AVAILABLE", "No card bank available");
        return usable;
    }

    @Transactional(readOnly = true)
    public CardRoute resolve(Merchant merchant, Terminal terminal) {
        return resolveAll(merchant, terminal).getFirst();
    }

    private CardRoute route(Acquirer acquirer) {
        return new CardRoute(acquirer.getCode(), acquirer.isThreeDsSupported() ? threeDsPort : directPort);
    }
}
