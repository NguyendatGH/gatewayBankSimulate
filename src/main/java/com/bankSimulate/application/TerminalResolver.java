package com.bankSimulate.application;

import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.enums.PaymentMethod;
import com.bankSimulate.domain.enums.TerminalPurpose;
import com.bankSimulate.domain.merchant.Merchant;
import com.bankSimulate.domain.terminal.Terminal;
import com.bankSimulate.infrastructure.persistence.TerminalRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class TerminalResolver {

    private final TerminalRepository terminals;
    private final TerminalService terminalService;

    public TerminalResolver(TerminalRepository terminals, TerminalService terminalService) {
        this.terminals = terminals;
        this.terminalService = terminalService;
    }

    public Terminal forPayment(Merchant merchant, PaymentMethod method) {
        for (Terminal t : acceptingTerminals(merchant)) {
            if (terminalService.routableMethods(t, merchant).contains(method)) return t;
        }
        throw new ApiException(409, "PAYMENT_METHOD_NOT_ENABLED", method + " is not enabled for this merchant");
    }

    public Set<String> customerMethods(Merchant merchant) {
        EnumSet<PaymentMethod> union = EnumSet.noneOf(PaymentMethod.class);
        for (Terminal t : acceptingTerminals(merchant)) union.addAll(terminalService.routableMethods(t, merchant));
        Set<String> names = new LinkedHashSet<>();
        for (PaymentMethod m : PaymentMethod.values()) if (union.contains(m)) names.add(m.name());
        return names;
    }

    public List<Terminal> acceptingTerminals(Merchant merchant) {
        List<Terminal> live = terminals.findAllByMerchantId(merchant.getId()).stream().filter(Terminal::isLive).toList();
        List<Terminal> channels = live.stream().filter(Terminal::isChannel)
                .sorted(Comparator.comparing(Terminal::getChannelOpenedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (!channels.isEmpty()) return channels;
        return live.stream().filter(t -> t.getPurpose() == TerminalPurpose.DEFAULT).toList();
    }
}
