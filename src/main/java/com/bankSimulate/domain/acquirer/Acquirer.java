package com.bankSimulate.domain.acquirer;

import com.bankSimulate.domain.enums.PaymentMethod;
import com.bankSimulate.domain.enums.Status;
import com.bankSimulate.domain.enums.ThreeDsPolicy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@RequiredArgsConstructor
@Table(name = "acquirers")
public class Acquirer {
    @Id
    private UUID id;
    @Column(name = "code", nullable = false, unique = true)
    private String code;
    @Column(name = "name", nullable = false)
    private String name;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "acquirer_payment_methods", joinColumns = @JoinColumn(name = "acquirer_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private Set<PaymentMethod> paymentMethods = EnumSet.noneOf(PaymentMethod.class);
    @Column(name = "three_ds_supported", nullable = false)
    private boolean threeDsSupported;
    @Column(name = "bank_bin", length = 6)
    private String bankBin;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Acquirer(String code, String name, Set<PaymentMethod> paymentMethods, boolean threeDsSupported) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.paymentMethods = EnumSet.copyOf(paymentMethods);
        this.threeDsSupported = threeDsSupported;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public boolean accepts(PaymentMethod method) {
        return status == Status.ACTIVE && paymentMethods.contains(method);
    }

    public boolean meetsThreeDs(PaymentMethod method, ThreeDsPolicy policy) {
        return method != PaymentMethod.CARD || policy != ThreeDsPolicy.REQUIRED || threeDsSupported;
    }

    public void update(String name, Set<PaymentMethod> paymentMethods, Boolean threeDsSupported, Status status) {
        if (name != null) this.name = name;
        if (paymentMethods != null) {
            this.paymentMethods.clear();
            this.paymentMethods.addAll(paymentMethods);
        }
        if (threeDsSupported != null) this.threeDsSupported = threeDsSupported;
        if (status != null) this.status = status;
        this.updatedAt = Instant.now();
    }

    public void changeBankBin(String bankBin) {
        this.bankBin = bankBin == null || bankBin.isBlank() ? null : bankBin.trim();
        this.updatedAt = Instant.now();
    }

    public boolean isSelectableBank() {
        return status == Status.ACTIVE && bankBin != null;
    }
}
