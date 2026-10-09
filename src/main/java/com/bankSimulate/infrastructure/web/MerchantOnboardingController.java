package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.MerchantOnboardingService;
import com.bankSimulate.infrastructure.web.dto.AdminDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/merchant-onboarding")
public class MerchantOnboardingController {

    private final MerchantOnboardingService service;

    public MerchantOnboardingController(MerchantOnboardingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminDtos.OnboardingResponse onboard(@Valid @RequestBody AdminDtos.OnboardingRequest request) {
        return service.onboard(request);
    }
}
