package com.bankSimulate.config;

import com.bankSimulate.domain.bank.BankProcessor;
import com.bankSimulate.domain.bank.BankProfile;
import com.bankSimulate.infrastructure.bank.ConfiguredBankProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BankProfileConfiguration {
    @Bean
    public BankProcessor mbBank() {
        return new ConfiguredBankProcessor(new BankProfile("ACQUIRER_A", "MB Bank", "970422"));
    }

    @Bean
    public BankProcessor vietinBank() {
        return new ConfiguredBankProcessor(new BankProfile("ACQUIRER_B", "VietinBank", "970415"));
    }

    @Bean
    public BankProcessor vietcomBank() {
        return new ConfiguredBankProcessor(new BankProfile("QR_PROVIDER_A", "Vietcombank", "970436"));
    }
}
