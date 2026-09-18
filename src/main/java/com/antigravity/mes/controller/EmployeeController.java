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

    @RequiresActivity("EMPLOYEE_LIST")
    @GetMapping
    public ResponseEntity<List<EmployeeSummaryResponse>> list() {
        return ResponseEntity.ok(employeeManagementService.listEmployees());
    }

    @RequiresActivity("EMPLOYEE_DETAIL")
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeDetailResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(employeeManagementService.getEmployee(id));
    }

    @RequiresActivity("EMPLOYEE_FORM")
    @PostMapping
    public ResponseEntity<EmployeeDetailResponse> create(@RequestBody EmployeeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeManagementService.createEmployee(request));
    }

    @RequiresActivity("EMPLOYEE_FORM")
    @PostMapping("/import-from-hrms")
    public ResponseEntity<EmployeeDetailResponse> importFromHrms(@RequestBody ImportFromHrmsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeManagementService.importFromHrms(request));
    }

    @RequiresActivity("EMPLOYEE_FORM")
    @PostMapping("/import-from-hrms/bulk")
    public ResponseEntity<BulkImportResult> importAllFromHrms(@RequestBody BulkImportFromHrmsRequest request) {
        return ResponseEntity.ok(employeeManagementService.importAllFromHrms(request.defaultRoleCode(), request.defaultDepartment()));
    }

    @RequiresActivity("EMPLOYEE_FORM")
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeDetailResponse> update(@PathVariable Long id, @RequestBody EmployeeUpdateRequest request) {
        return ResponseEntity.ok(employeeManagementService.updateEmployee(id, request));
    }

    /** Replaces the employee's full set of roles - the "Assign Roles" action on the detail screen. */
    @RequiresActivity("EMPLOYEE_FORM")
    @PutMapping("/{id}/roles")
    public ResponseEntity<EmployeeDetailResponse> assignRoles(@PathVariable Long id, @RequestBody AssignRolesRequest request) {
        return ResponseEntity.ok(employeeManagementService.assignRoles(id, request.roleCodes()));
    }

    @RequiresActivity("EMPLOYEE_DETAIL")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Map<String, String>> deactivate(@PathVariable Long id) {
        employeeManagementService.setActive(id, false);
        return ResponseEntity.ok(Map.of("status", "DEACTIVATED"));
    }

    @RequiresActivity("EMPLOYEE_DETAIL")
    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<Map<String, String>> reactivate(@PathVariable Long id) {
        employeeManagementService.setActive(id, true);
        return ResponseEntity.ok(Map.of("status", "ACTIVE"));
    }

    @RequiresActivity("EMPLOYEE_DETAIL")
    @PostMapping(value = "/{id}/face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> enrollFace(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        employeeManagementService.enrollFace(id, file.getBytes(), file.getOriginalFilename());
        return ResponseEntity.ok(Map.of("status", "FACE_ENROLLED"));
    }

    @RequiresActivity("EMPLOYEE_DETAIL")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        employeeManagementService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    @RequiresActivity("EMPLOYEE_DETAIL")
    @PostMapping("/bulk-delete")
    public ResponseEntity<BulkDeleteResult> bulkDelete(@RequestBody BulkDeleteRequest request) {
        return ResponseEntity.ok(employeeManagementService.deleteEmployees(request.ids()));
    }
}
