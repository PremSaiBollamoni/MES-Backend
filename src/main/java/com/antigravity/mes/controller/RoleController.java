package com.antigravity.mes.controller;

import com.antigravity.mes.dto.BulkDeleteRequest;
import com.antigravity.mes.dto.BulkDeleteResult;
import com.antigravity.mes.dto.RoleCreateRequest;
import com.antigravity.mes.dto.RoleDetailResponse;
import com.antigravity.mes.dto.RoleResponse;
import com.antigravity.mes.dto.RoleUpdateRequest;
import com.antigravity.mes.model.Role;
import com.antigravity.mes.repository.RoleRepository;
import com.antigravity.mes.service.RoleManagementService;
import com.antigravity.mes.web.ActivityCatalog;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@CrossOrigin(origins = "*")
public class RoleController {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RoleManagementService roleManagementService;

    /** Only active roles are offered - consumed by the employee create/edit form. */
    @RequiresActivity("EMPLOYEE_FORM")
    @GetMapping
    public ResponseEntity<List<RoleResponse>> list() {
        List<RoleResponse> roles = roleRepository.findAll().stream()
                .filter(Role::isActive)
                .map(role -> new RoleResponse(role.getRoleName()))
                .toList();
        return ResponseEntity.ok(roles);
    }

    /** Full catalog of activity (screen) codes, for the Role Management form's checkboxes. */
    @RequiresActivity("ROLE_LIST")
    @GetMapping("/manage/activities")
    public ResponseEntity<List<String>> listActivities() {
        return ResponseEntity.ok(ActivityCatalog.ALL);
    }

    @RequiresActivity("ROLE_LIST")
    @GetMapping("/manage")
    public ResponseEntity<List<RoleDetailResponse>> manageList() {
        return ResponseEntity.ok(roleManagementService.listRoles());
    }

    @RequiresActivity("ROLE_LIST")
    @GetMapping("/manage/{id}")
    public ResponseEntity<RoleDetailResponse> manageGet(@PathVariable Long id) {
        return ResponseEntity.ok(roleManagementService.getRole(id));
    }

    @RequiresActivity("ROLE_FORM")
    @PostMapping("/manage")
    public ResponseEntity<RoleDetailResponse> manageCreate(@RequestBody RoleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleManagementService.createRole(request));
    }

    @RequiresActivity("ROLE_FORM")
    @PutMapping("/manage/{id}")
    public ResponseEntity<RoleDetailResponse> manageUpdate(@PathVariable Long id, @RequestBody RoleUpdateRequest request) {
        return ResponseEntity.ok(roleManagementService.updateRole(id, request));
    }

    @RequiresActivity("ROLE_FORM")
    @DeleteMapping("/manage/{id}")
    public ResponseEntity<Void> manageDelete(@PathVariable Long id) {
        roleManagementService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @RequiresActivity("ROLE_FORM")
    @PostMapping("/manage/bulk-delete")
    public ResponseEntity<BulkDeleteResult> bulkDelete(@RequestBody BulkDeleteRequest request) {
        return ResponseEntity.ok(roleManagementService.deleteRoles(request.ids()));
    }
}
