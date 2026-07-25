package com.antigravity.mes.controller;

import com.antigravity.mes.hrms.HrmsClient;
import com.antigravity.mes.hrms.HrmsSyncService;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("enabled", hrmsClient.isEnabled()));
    }

    @RequiresActivity("EMPLOYEE_LIST")
    @GetMapping("/employees")
    public ResponseEntity<?> employees() {
        if (!hrmsClient.isEnabled()) {
            return unavailable();
        }
        return ResponseEntity.ok(hrmsClient.listEmployees(true));
    }

    /** HRMS employees created directly in the HRMS portal that have no local counterpart yet. */
    @RequiresActivity("EMPLOYEE_LIST")
    @GetMapping("/employees/unlinked")
    public ResponseEntity<?> unlinkedEmployees() {
        if (!hrmsClient.isEnabled()) {
            return unavailable();
        }
        return ResponseEntity.ok(hrmsSyncService.listUnlinkedEmployees());
    }

    private ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "HRMS integration is not configured yet"));
    }
}
