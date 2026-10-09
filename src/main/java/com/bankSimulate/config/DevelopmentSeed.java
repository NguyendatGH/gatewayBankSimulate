package com.bankSimulate.config;

import com.bankSimulate.domain.enums.PaymentMethod;
import com.bankSimulate.domain.acquirer.Acquirer;
import com.bankSimulate.domain.acquirer.AcquirerMerchantConfig;
import com.bankSimulate.domain.merchant.Merchant;
import com.bankSimulate.domain.routing.RoutingProfile;
import com.bankSimulate.domain.routing.RoutingRule;
import com.bankSimulate.infrastructure.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Configuration
public class DevelopmentSeed {

    public static final String BANK_A = "bank-a";
    public static final String BANK_B = "bank-b";
    public static final String QR_PROVIDER = "QR_PROVIDER_A";
    public static final String PROFILE_VIA_BANK_A = "CARD_VIA_BANK_A";
    public static final String PROFILE_VIA_BANK_B = "CARD_VIA_BANK_B";

    public static final String PROFILE_STANDARD = "STANDARD";

    private static final Logger log = LoggerFactory.getLogger(DevelopmentSeed.class);

    @Bean
    @ConditionalOnProperty(name = "gateway.seed.enabled", havingValue = "true")
    CommandLineRunner seedGateway(AcquirerRepository acquirers, MerchantRepository merchants,
                                  AcquirerMerchantConfigRepository acquirerConfigs,
                                  RoutingProfileRepository profiles, RoutingRuleRepository rules) {
        return args -> new Seeder(acquirers, merchants, acquirerConfigs, profiles, rules).run();
    }

    static final class Seeder {
        private final AcquirerRepository acquirers;
        private final MerchantRepository merchants;
        private final AcquirerMerchantConfigRepository acquirerConfigs;
        private final RoutingProfileRepository profiles;
        private final RoutingRuleRepository rules;

        Seeder(AcquirerRepository acquirers, MerchantRepository merchants,
               AcquirerMerchantConfigRepository acquirerConfigs, RoutingProfileRepository profiles,
               RoutingRuleRepository rules) {
            this.acquirers = acquirers;
            this.merchants = merchants;
            this.acquirerConfigs = acquirerConfigs;
            this.profiles = profiles;
            this.rules = rules;
        }

        @Transactional
        void run() {
            Acquirer bankA = acquirer(BANK_A, "Mock Bank A (có 3DS)", EnumSet.of(PaymentMethod.CARD), true);
            Acquirer bankB = acquirer(BANK_B, "Mock Bank B (trả kết quả thẳng)", EnumSet.of(PaymentMethod.CARD), false);
            Acquirer qr = acquirer(QR_PROVIDER, "Mock QR Provider A", EnumSet.of(PaymentMethod.QR), false);
            acquirer("ACQUIRER_A", "Mock Acquirer A",
                    EnumSet.of(PaymentMethod.CARD, PaymentMethod.PAYNOW, PaymentMethod.GOOGLE_PAY, PaymentMethod.APPLE_PAY), true);
            acquirer("ACQUIRER_B", "Mock Acquirer B",
                    EnumSet.of(PaymentMethod.CARD, PaymentMethod.PAYNOW, PaymentMethod.GOOGLE_PAY, PaymentMethod.APPLE_PAY), true);

            bank("MBB", "MB Bank", "970422", EnumSet.of(PaymentMethod.QR), false);
            bank("VCB", "Vietcombank", "970436", EnumSet.of(PaymentMethod.CARD, PaymentMethod.QR), true);
            bank("VTB", "VietinBank", "970415",
                    EnumSet.of(PaymentMethod.CARD, PaymentMethod.QR, PaymentMethod.PAYNOW), false);
            bank("TCB", "Techcombank", "970407",
                    EnumSet.of(PaymentMethod.CARD, PaymentMethod.QR, PaymentMethod.GOOGLE_PAY, PaymentMethod.APPLE_PAY), true);

            RoutingProfile viaA = profile(PROFILE_VIA_BANK_A, "Card via bank-a (3DS)");
            rule(viaA, PaymentMethod.CARD, bankA, 1);
            RoutingProfile viaB = profile(PROFILE_VIA_BANK_B, "Card via bank-b (non-3DS)");
            rule(viaB, PaymentMethod.CARD, bankB, 1);
            RoutingProfile standard = profile(PROFILE_STANDARD, "Thẻ + QR (mặc định)");
            rule(standard, PaymentMethod.CARD, bankA, 1);
            rule(standard, PaymentMethod.CARD, bankB, 2);
            rule(standard, PaymentMethod.QR, qr, 1);

            for (Merchant merchant : merchants.findAll()) {
                for (Acquirer acquirer : List.of(bankA, bankB, qr)) merchantAcquirer(merchant, acquirer);
            }
        }

        private void bank(String code, String name, String bin, Set<PaymentMethod> methods, boolean threeDs) {
            Acquirer bank = acquirer(code, name, methods, threeDs);
            if (bank.getBankBin() != null || acquirers.findAll().stream().anyMatch(a -> bin.equals(a.getBankBin()))) return;
            bank.changeBankBin(bin);
            acquirers.save(bank);
            log.info("[seed] bank {} selectable by organizers with BIN {}", code, bin);
        }

        private Acquirer acquirer(String code, String name, Set<PaymentMethod> methods, boolean threeDs) {
            return acquirers.findByCode(code).orElseGet(() -> {
                log.info("[seed] acquirer {} created methods={} threeDs={}", code, methods, threeDs);
                return acquirers.save(new Acquirer(code, name, methods, threeDs));
            });
        }

        private RoutingProfile profile(String code, String name) {
            return profiles.findByCode(code).orElseGet(() -> {
                log.info("[seed] routing profile {} created", code);
                return profiles.save(new RoutingProfile(code, name));
            });
        }

        private void rule(RoutingProfile profile, PaymentMethod method, Acquirer acquirer, int priority) {
            if (rules.existsByRoutingProfileIdAndPaymentMethodAndPriority(profile.getId(), method, priority)) return;
            rules.save(new RoutingRule(profile.getId(), method, acquirer.getId(), priority));
            log.info("[seed] routing rule {} {} -> {} priority {}", profile.getCode(), method, acquirer.getCode(), priority);
        }

        private void merchantAcquirer(Merchant merchant, Acquirer acquirer) {
            if (acquirerConfigs.existsByAcquirerIdAndMerchantId(acquirer.getId(), merchant.getId())) return;
            String suffix = merchant.getMerNo() + "-" + acquirer.getCode();
            acquirerConfigs.save(new AcquirerMerchantConfig(acquirer.getId(), merchant.getId(),
                    "MID-" + suffix, "TID-" + suffix));
            log.info("[seed] merchant {} configured for acquirer {}", merchant.getMerNo(), acquirer.getCode());
        }
    }
}
