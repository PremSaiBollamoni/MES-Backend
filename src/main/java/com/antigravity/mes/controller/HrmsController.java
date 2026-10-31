package com.antigravity.mes.controller;

import com.antigravity.mes.hrms.HrmsClient;
import com.antigravity.mes.hrms.HrmsSyncService;
import com.antigravity.mes.web.ErrorCodes;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Read-only visibility into the HRMS portal's own employee records, for reconciliation. */
@RestController
@RequestMapping("/api/hrms")
@CrossOrigin(origins = "*")
public class HrmsController {

    @Autowired
    private HrmsClient hrmsClient;

    @Autowired
    private HrmsSyncService hrmsSyncService;

    @RequiresActivity("EMPLOYEE_LIST")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@RequestAttribute(required = false) Long factoryId) {
        return ResponseEntity.ok(Map.of("enabled", hrmsClient.isEnabled(factoryId)));
    }

    @RequiresActivity("EMPLOYEE_LIST")
    @GetMapping("/employees")
    public ResponseEntity<?> employees(@RequestAttribute(required = false) Long factoryId) {
        if (!hrmsClient.isEnabled(factoryId)) {
            return unavailable();
        }
        return ResponseEntity.ok(hrmsClient.listEmployees(factoryId, true));
    }

    /** HRMS employees created directly in the HRMS portal that have no local counterpart yet. */
    @RequiresActivity("EMPLOYEE_LIST")
    @GetMapping("/employees/unlinked")
    public ResponseEntity<?> unlinkedEmployees(@RequestAttribute(required = false) Long factoryId) {
        if (!hrmsClient.isEnabled(factoryId)) {
            return unavailable();
        }
        return ResponseEntity.ok(hrmsSyncService.listUnlinkedEmployees(factoryId));
    }

    private ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("code", ErrorCodes.HRMS_NOT_CONFIGURED, "error", ErrorCodes.fallback(ErrorCodes.HRMS_NOT_CONFIGURED)));
    }
}
