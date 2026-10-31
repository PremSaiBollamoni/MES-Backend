package com.antigravity.mes.controller;

import com.antigravity.mes.hrms.HrmsClient;
import com.antigravity.mes.model.Factory;
import com.antigravity.mes.repository.FactoryRepository;
import com.antigravity.mes.security.TokenPrincipal;
import com.antigravity.mes.security.JwtService;
import com.antigravity.mes.dto.FactoryKpiSummary;
import com.antigravity.mes.service.EmployeeManagementService;
import com.antigravity.mes.service.FactoryKpiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/factories")
@CrossOrigin(origins = "*")
public class FactoryController {

    @Autowired
    private FactoryRepository factoryRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private HrmsClient hrmsClient;

    @Autowired
    private EmployeeManagementService employeeManagementService;

    @Autowired
    private FactoryKpiService factoryKpiService;

    /**
     * List all factories - SUPER_ADMIN only
     */
    @GetMapping
    public ResponseEntity<?> listFactories(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Optional<TokenPrincipal> principal = validateSuperAdmin(authHeader);
        if (principal.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "SUPER_ADMIN access required"));
        }

        List<Factory> factories = factoryRepository.findAll();
        return ResponseEntity.ok(factories);
    }

    /**
     * Get factory details by ID - SUPER_ADMIN only
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getFactory(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Optional<TokenPrincipal> principal = validateSuperAdmin(authHeader);
        if (principal.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "SUPER_ADMIN access required"));
        }

        Optional<Factory> factory = factoryRepository.findById(id);
        return factory.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Factory not found")));
    }

    /**
     * Create a new factory - SUPER_ADMIN only
     */
    @PostMapping
    public ResponseEntity<?> createFactory(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Optional<TokenPrincipal> principal = validateSuperAdmin(authHeader);
        if (principal.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "SUPER_ADMIN access required"));
        }

        String factoryName = request.get("factoryName");
        String factoryCode = request.get("factoryCode");
        String location = request.get("location");

        if (factoryName == null || factoryCode == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "factoryName and factoryCode are required"));
        }

        Factory factory = new Factory(factoryName, factoryCode, location);
        Factory saved = factoryRepository.save(factory);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Update factory details - SUPER_ADMIN only
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateFactory(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Optional<TokenPrincipal> principal = validateSuperAdmin(authHeader);
        if (principal.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "SUPER_ADMIN access required"));
        }

        Optional<Factory> factoryOpt = factoryRepository.findById(id);
        if (factoryOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Factory not found"));
        }

        Factory factory = factoryOpt.get();
        if (request.containsKey("factoryName")) {
            factory.setFactoryName(request.get("factoryName"));
        }
        // Safe to change any time: only employee_id/factory_id link accounts to a
        // factory, never factory_code. The code only affects the prefix used for
        // usernames generated AFTER this change - existing logins are unaffected.
        if (request.containsKey("factoryCode")) {
            String code = request.get("factoryCode");
            if (code != null && !code.isBlank()) {
                factory.setFactoryCode(code.trim().toUpperCase());
            }
        }
        if (request.containsKey("location")) {
            factory.setLocation(request.get("location"));
        }
        if (request.containsKey("hrmsEnabled")) {
            factory.setHrmsEnabled(Boolean.parseBoolean(request.get("hrmsEnabled")));
        }
        if (request.containsKey("isActive")) {
            factory.setActive(Boolean.parseBoolean(request.get("isActive")));
        }

        Factory updated = factoryRepository.save(factory);
        return ResponseEntity.ok(updated);
    }

    /**
     * Update HRMS configuration for a factory - SUPER_ADMIN only
     */
    @PostMapping("/{id}/hrms-config")
    public ResponseEntity<?> updateHrmsConfig(
            @PathVariable Long id,
            @RequestBody String hrmsConfigJson,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Optional<TokenPrincipal> principal = validateSuperAdmin(authHeader);
        if (principal.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "SUPER_ADMIN access required"));
        }

        Optional<Factory> factoryOpt = factoryRepository.findById(id);
        if (factoryOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Factory not found"));
        }

        Factory factory = factoryOpt.get();
        factory.setHrmsConfigJson(hrmsConfigJson);
        Factory updated = factoryRepository.save(factory);
        return ResponseEntity.ok(updated);
    }

    /**
     * Tests a candidate HRMS connection (login + one real employee fetch) before it's
     * saved - lets the admin catch a wrong URL/password immediately instead of finding
     * out later when the next sync silently fails. Does not persist anything.
     */
    @PostMapping("/hrms-test-connection")
    public ResponseEntity<?> testHrmsConnection(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Optional<TokenPrincipal> principal = validateSuperAdmin(authHeader);
        if (principal.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "SUPER_ADMIN access required"));
        }

        String baseUrl = request.get("baseUrl");
        String username = request.get("username");
        String password = request.get("password");

        HrmsClient.HrmsConnectionTestResult result = hrmsClient.testConnection(baseUrl, username, password);
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.success());
        response.put("message", result.message());
        response.put("employeeCount", result.employeeCount());
        return ResponseEntity.ok(response);
    }

    /**
     * Renumbers every employee in this factory to <factoryCode><seq> (e.g. GTGBZA001,
     * GTGBZA002, ...). Changes real login usernames - the response's old->new mapping
     * must be given to HR so affected employees learn their new username before their
     * old one stops working.
     */
    @PostMapping("/{id}/fix-usernames")
    public ResponseEntity<?> fixUsernames(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Optional<TokenPrincipal> principal = validateSuperAdmin(authHeader);
        if (principal.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "SUPER_ADMIN access required"));
        }

        try {
            List<EmployeeManagementService.UsernameChange> changes = employeeManagementService.fixUsernamesForFactory(id);
            return ResponseEntity.ok(changes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cross-factory KPI overview - SUPER_ADMIN only. One row per factory: workforce,
     * today's production, today's attendance, and month-to-date payroll.
     */
    @GetMapping("/kpi-overview")
    public ResponseEntity<?> kpiOverview(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Optional<TokenPrincipal> principal = validateSuperAdmin(authHeader);
        if (principal.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "SUPER_ADMIN access required"));
        }
        List<FactoryKpiSummary> summaries = factoryKpiService.getAllFactoryKpis();
        return ResponseEntity.ok(summaries);
    }

    /**
     * Validate SUPER_ADMIN access - helper method
     */
    private Optional<TokenPrincipal> validateSuperAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        Optional<TokenPrincipal> principal = jwtService.validate(authHeader.substring(7));
        if (principal.isPresent() && principal.get().isSuperAdmin()) {
            return principal;
        }
        return Optional.empty();
    }
}
