package com.antigravity.mes.controller;

import com.antigravity.mes.dto.AssignRolesRequest;
import com.antigravity.mes.dto.BulkDeleteRequest;
import com.antigravity.mes.dto.BulkDeleteResult;
import com.antigravity.mes.dto.BulkImportFromHrmsRequest;
import com.antigravity.mes.dto.BulkImportResult;
import com.antigravity.mes.dto.EmployeeCreateRequest;
import com.antigravity.mes.dto.EmployeeDetailResponse;
import com.antigravity.mes.dto.EmployeeSummaryResponse;
import com.antigravity.mes.dto.EmployeeUpdateRequest;
import com.antigravity.mes.dto.ImportFromHrmsRequest;
import com.antigravity.mes.service.EmployeeManagementService;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
@CrossOrigin(origins = "*")
public class EmployeeController {

    @Autowired
    private EmployeeManagementService employeeManagementService;

    /**
     * SUPER_ADMIN must explicitly pick which factory to view (see FactoryController for
     * the list) - viewFactoryId scopes their view to it. Without it, SUPER_ADMIN sees
     * nothing here at all (never an accidental merged cross-factory dump); a regular
     * employee always sees their own factory regardless of this parameter - it cannot
     * be used to escape their own factory's boundary.
     */
    @RequiresActivity("EMPLOYEE_LIST")
    @GetMapping
    public ResponseEntity<List<EmployeeSummaryResponse>> list(
            @RequestParam(required = false) Long viewFactoryId,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        boolean superAdmin = Boolean.TRUE.equals(isSuperAdmin);
        if (superAdmin) {
            // No factory chosen yet - show nothing rather than every factory merged together.
            if (viewFactoryId == null) {
                return ResponseEntity.ok(List.of());
            }
            return ResponseEntity.ok(employeeManagementService.listEmployees(viewFactoryId, false));
        }
        return ResponseEntity.ok(employeeManagementService.listEmployees(factoryId, false));
    }

    @RequiresActivity("EMPLOYEE_DETAIL")
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeDetailResponse> get(
            @PathVariable Long id,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        return ResponseEntity.ok(employeeManagementService.getEmployee(id, factoryId, Boolean.TRUE.equals(isSuperAdmin)));
    }

    @RequiresActivity("EMPLOYEE_FORM")
    @PostMapping
    public ResponseEntity<EmployeeDetailResponse> create(
            @RequestBody EmployeeCreateRequest request,
            @RequestAttribute(required = false) Long factoryId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeManagementService.createEmployee(request, factoryId));
    }

    @RequiresActivity("EMPLOYEE_FORM")
    @PostMapping("/import-from-hrms")
    public ResponseEntity<EmployeeDetailResponse> importFromHrms(
            @RequestBody ImportFromHrmsRequest request,
            @RequestAttribute(required = false) Long factoryId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeManagementService.importFromHrms(request, factoryId));
    }

    @RequiresActivity("EMPLOYEE_FORM")
    @PostMapping("/import-from-hrms/bulk")
    public ResponseEntity<BulkImportResult> importAllFromHrms(
            @RequestBody BulkImportFromHrmsRequest request,
            @RequestAttribute(required = false) Long factoryId) {
        return ResponseEntity.ok(employeeManagementService.importAllFromHrms(request.defaultRoleCode(), request.defaultDepartment(), factoryId));
    }

    @RequiresActivity("EMPLOYEE_FORM")
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeDetailResponse> update(
            @PathVariable Long id,
            @RequestBody EmployeeUpdateRequest request,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        return ResponseEntity.ok(employeeManagementService.updateEmployee(id, request, factoryId, Boolean.TRUE.equals(isSuperAdmin)));
    }

    /** Replaces the employee's full set of roles - the "Assign Roles" action on the detail screen. */
    @RequiresActivity("EMPLOYEE_FORM")
    @PutMapping("/{id}/roles")
    public ResponseEntity<EmployeeDetailResponse> assignRoles(
            @PathVariable Long id,
            @RequestBody AssignRolesRequest request,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        return ResponseEntity.ok(employeeManagementService.assignRoles(id, request.roleCodes(), factoryId, Boolean.TRUE.equals(isSuperAdmin)));
    }

    @RequiresActivity("EMPLOYEE_DETAIL")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Map<String, String>> deactivate(
            @PathVariable Long id,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        employeeManagementService.setActive(id, false, factoryId, Boolean.TRUE.equals(isSuperAdmin));
        return ResponseEntity.ok(Map.of("status", "DEACTIVATED"));
    }

    @RequiresActivity("EMPLOYEE_DETAIL")
    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<Map<String, String>> reactivate(
            @PathVariable Long id,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        employeeManagementService.setActive(id, true, factoryId, Boolean.TRUE.equals(isSuperAdmin));
        return ResponseEntity.ok(Map.of("status", "ACTIVE"));
    }

    @RequiresActivity("EMPLOYEE_DETAIL")
    @PostMapping(value = "/{id}/face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> enrollFace(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) throws IOException {
        employeeManagementService.enrollFace(id, file.getBytes(), file.getOriginalFilename(), factoryId, Boolean.TRUE.equals(isSuperAdmin));
        return ResponseEntity.ok(Map.of("status", "FACE_ENROLLED"));
    }

    @RequiresActivity("EMPLOYEE_DETAIL")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        employeeManagementService.deleteEmployee(id, factoryId, Boolean.TRUE.equals(isSuperAdmin));
        return ResponseEntity.noContent().build();
    }

    @RequiresActivity("EMPLOYEE_DETAIL")
    @PostMapping("/bulk-delete")
    public ResponseEntity<BulkDeleteResult> bulkDelete(
            @RequestBody BulkDeleteRequest request,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        return ResponseEntity.ok(employeeManagementService.deleteEmployees(request.ids(), factoryId, Boolean.TRUE.equals(isSuperAdmin)));
    }
}
