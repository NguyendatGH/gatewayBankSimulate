package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.RoutingService;
import com.bankSimulate.infrastructure.web.dto.AdminDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/routing-profiles")
public class RoutingAdminController {
    private final RoutingService service;

    public RoutingAdminController(RoutingService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminDtos.RoutingProfileListItem> list() {
        return service.list();
    }

    @PostMapping
    public AdminDtos.RoutingProfileResponse create(@Valid @RequestBody AdminDtos.CreateRoutingProfileRequest request) {
        return service.create(request);
    }

    @PostMapping("/{code}/rules")
    public AdminDtos.RoutingProfileResponse addRule(@PathVariable String code, @Valid @RequestBody AdminDtos.RoutingRuleRequest request) {
        return service.addRule(code, request);
    }

    @GetMapping("/{code}")
    public AdminDtos.RoutingProfileResponse get(@PathVariable String code) {
        return service.get(code);
    }
}
