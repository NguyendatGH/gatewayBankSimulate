package com.bankSimulate.application;

import com.bankSimulate.domain.bank.BankProcessor;
import com.bankSimulate.domain.bank.BankProfile;
import com.bankSimulate.domain.bank.BankRefundResult;
import com.bankSimulate.domain.common.ApiException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BankService {
    private final Map<String, BankProcessor> processors;

    public BankService(List<BankProcessor> processors) {
        this.processors = processors.stream().collect(Collectors.toUnmodifiableMap(
                processor -> processor.profile().code(), Function.identity()));
    }

    public List<BankProfile> profiles() {
        return processors.values().stream().map(BankProcessor::profile).sorted(java.util.Comparator.comparing(BankProfile::code)).toList();
    }

    public BankRefundResult refund(String referenceId, long amount, String payerAccountNumber) {
        return processor("ACQUIRER_A").refund(referenceId, amount, payerAccountNumber);
    }

    public BankRefundResult refund(String referenceId, long amount, String toBin, String payerAccountNumber) {
        return processorForBin(toBin).refund(referenceId, amount, payerAccountNumber);
    }

    private BankProcessor processor(String code) {
        BankProcessor processor = processors.get(code);
        if (processor == null) throw new ApiException(409, "BANK_PROFILE_NOT_FOUND", "No runtime bank profile configured for " + code);
        return processor;
    }

    private BankProcessor processorForBin(String bin) {
        if (bin == null || bin.isBlank()) return processor("ACQUIRER_A");
        return processors.values().stream()
                .filter(candidate -> bin.equals(candidate.profile().bankBin()))
                .findFirst()
                .orElseThrow(() -> new ApiException(409, "BANK_PROFILE_NOT_FOUND", "No runtime bank profile configured for BIN " + bin));
    }
}
