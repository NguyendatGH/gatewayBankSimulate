package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.AcquirerService;
import com.bankSimulate.infrastructure.web.dto.AdminDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AcquirerAdminController {
    private final AcquirerService service;

    public AcquirerAdminController(AcquirerService service) {
        this.service = service;
    }

    @PostMapping("/acquirers")
    public AdminDtos.AcquirerResponse create(@Valid @RequestBody AdminDtos.CreateAcquirerRequest request) {
        return service.create(request);
    }

    @GetMapping("/acquirers")
    public List<AdminDtos.AcquirerResponse> list() {
        return service.list();
    }

    @PatchMapping("/acquirers/{code}")
    public AdminDtos.AcquirerResponse update(@PathVariable String code, @RequestBody AdminDtos.UpdateAcquirerRequest request) {
        return service.update(code, request);
    }

    @GetMapping("/merchants/{merNo}/acquirer-configs") public List<AdminDtos.AcquirerConfigResponse> configs(@PathVariable String merNo) { return service.listConfigs(merNo); }
    @PostMapping("/merchants/{merNo}/acquirer-configs")
    public AdminDtos.AcquirerConfigResponse configure(@PathVariable String merNo, @Valid @RequestBody AdminDtos.AcquirerConfigRequest request) {
        return service.configure(merNo, request);
    }
}
