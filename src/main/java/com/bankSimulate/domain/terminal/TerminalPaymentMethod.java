package com.bankSimulate.domain.terminal;

import com.bankSimulate.domain.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Getter
@Table(name = "terminal_payment_methods")
@IdClass(TerminalPaymentMethod.Key.class)
public class TerminalPaymentMethod {
    @Id
    @Column(name = "terminal_id")
    private UUID terminalId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private com.bankSimulate.domain.enums.PaymentMethod paymentMethod;

    @Column(nullable = false)
    private boolean enabled;

    protected TerminalPaymentMethod() {
    }

    public TerminalPaymentMethod(UUID terminalId, PaymentMethod paymentMethod) {
        this.terminalId = terminalId;
        this.paymentMethod = paymentMethod;
        this.enabled = true;
    }


    public static class Key implements Serializable {
        private UUID terminalId;
        private PaymentMethod paymentMethod;

        public Key() {
        }

        public Key(UUID terminalId, PaymentMethod paymentMethod) {
            this.terminalId = terminalId;
            this.paymentMethod = paymentMethod;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && terminalId.equals(k.terminalId) && paymentMethod == k.paymentMethod;
        }

        @Override
        public int hashCode() {
            return 31 * terminalId.hashCode() + paymentMethod.hashCode();
        }
    }
}
