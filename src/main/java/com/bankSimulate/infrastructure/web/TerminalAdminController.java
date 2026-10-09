package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.TerminalService;
import com.bankSimulate.infrastructure.web.dto.AdminDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class TerminalAdminController {
    private final TerminalService service;

    public TerminalAdminController(TerminalService service) {
        this.service = service;
    }

    @PostMapping("/merchants/{merNo}/terminals")
    public AdminDtos.TerminalResponse create(@PathVariable String merNo, @Valid @RequestBody AdminDtos.CreateTerminalRequest request) {
        return service.create(merNo, request);
    }

    @GetMapping("/merchants/{merNo}/terminals")
    public List<AdminDtos.TerminalResponse> list(@PathVariable String merNo) {
        return service.list(merNo);
    }

    @GetMapping("/terminals/{terminalId}")
    public AdminDtos.TerminalResponse get(@PathVariable String terminalId) {
        return service.get(terminalId);
    }

    @PatchMapping("/terminals/{terminalId}")
    public AdminDtos.TerminalResponse update(@PathVariable String terminalId, @RequestBody AdminDtos.UpdateTerminalRequest request) {
        return service.update(terminalId, request);
    }

    @PutMapping("/terminals/{terminalId}/payment-methods")
    public AdminDtos.TerminalResponse updateMethods(@PathVariable String terminalId, @Valid @RequestBody AdminDtos.PaymentMethodsRequest request) {
        return service.updateMethods(terminalId, request);
    }

    @PutMapping("/terminals/{terminalId}/three-ds-policy")
    public AdminDtos.TerminalResponse update3ds(@PathVariable String terminalId, @RequestBody AdminDtos.ThreeDsRequest request) {
        return service.update3ds(terminalId, request);
    }

    @PutMapping("/terminals/{terminalId}/routing-profile")
    public AdminDtos.TerminalResponse assignRouting(@PathVariable String terminalId, @RequestBody AdminDtos.RoutingProfileRequest request) {
        return service.assignRouting(terminalId, request);
    }

    @PutMapping("/terminals/{terminalId}/configuration")
    public AdminDtos.TerminalResponse configure(@PathVariable String terminalId, @Valid @RequestBody AdminDtos.TerminalConfigurationRequest request) {
        return service.configure(terminalId, request);
    }

}
