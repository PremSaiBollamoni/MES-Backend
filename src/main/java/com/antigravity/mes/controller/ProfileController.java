package com.antigravity.mes.controller;

import com.antigravity.mes.dto.ChangePasswordRequest;
import com.antigravity.mes.dto.EmployeeDetailResponse;
import com.antigravity.mes.dto.ProfileUpdateRequest;
import com.antigravity.mes.service.EmployeeManagementService;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Self-service endpoints - the acting employee is resolved from the JWT, never a path param. */
@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = "*")
public class ProfileController {

    @Autowired
    private EmployeeManagementService employeeManagementService;

    @RequiresActivity("PROFILE")
    @GetMapping
    public ResponseEntity<EmployeeDetailResponse> get(@RequestAttribute("employeeId") Long employeeId) {
        return ResponseEntity.ok(employeeManagementService.getEmployee(employeeId));
    }

    @RequiresActivity("PROFILE")
    @PutMapping
    public ResponseEntity<EmployeeDetailResponse> update(
            @RequestAttribute("employeeId") Long employeeId,
            @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(employeeManagementService.updateOwnProfile(employeeId, request));
    }

    @RequiresActivity("PROFILE")
    @PutMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestAttribute("employeeId") Long employeeId,
            @RequestBody ChangePasswordRequest request) {
        employeeManagementService.changeOwnPassword(employeeId, request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(Map.of("status", "PASSWORD_CHANGED"));
    }
}
