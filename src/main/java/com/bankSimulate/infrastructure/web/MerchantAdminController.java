package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.MerchantOnboardingService;
import com.bankSimulate.application.MerchantService;
import com.bankSimulate.infrastructure.web.dto.AdminDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/merchants")
public class MerchantAdminController {
    private final MerchantService service;
    private final MerchantOnboardingService onboarding;

    public MerchantAdminController(MerchantService service, MerchantOnboardingService onboarding) {
        this.service = service;
        this.onboarding = onboarding;
    }

    @GetMapping
    public java.util.List<AdminDtos.MerchantListItem> list() { return service.list(); }
    @PostMapping public AdminDtos.MerchantCreatedResponse create(@Valid @RequestBody AdminDtos.CreateMerchantRequest request) { return service.create(request); }
    @GetMapping("/{merNo}") public AdminDtos.MerchantResponse get(@PathVariable String merNo) { return service.get(merNo); }
    @PatchMapping("/{merNo}") public AdminDtos.MerchantResponse update(@PathVariable String merNo, @RequestBody AdminDtos.UpdateMerchantRequest request) { return service.update(merNo, request); }
    @PutMapping("/{merNo}/settlement-account")
    public AdminDtos.MerchantResponse settlement(@PathVariable String merNo, @Valid @RequestBody AdminDtos.SettlementAccountRequest request) {
        return service.updateSettlement(merNo, request);
    }
    @PostMapping("/{merNo}/credentials/rotate") @ResponseStatus(HttpStatus.CREATED)
    public AdminDtos.CredentialResponse rotate(@PathVariable String merNo) { return service.rotate(merNo); }
}
