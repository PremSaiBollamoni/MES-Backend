package com.antigravity.mes.service;

import com.antigravity.mes.dto.BulkDeleteResult;
import com.antigravity.mes.dto.BulkImportResult;
import com.antigravity.mes.dto.EmployeeCreateRequest;
import com.antigravity.mes.dto.EmployeeDetailResponse;
import com.antigravity.mes.dto.EmployeeSummaryResponse;
import com.antigravity.mes.dto.EmployeeUpdateRequest;
import com.antigravity.mes.dto.ImportFromHrmsRequest;
import com.antigravity.mes.dto.ProfileUpdateRequest;
import com.antigravity.mes.exception.ConflictException;
import com.antigravity.mes.exception.NotFoundException;
import com.antigravity.mes.hrms.HrmsSyncService;
import com.antigravity.mes.hrms.dto.HrmsEmployeeOut;
import com.antigravity.mes.model.Employee;
import com.antigravity.mes.model.EmployeeInfo;
import com.antigravity.mes.model.Role;
import com.antigravity.mes.repository.EmployeeInfoRepository;
import com.antigravity.mes.repository.EmployeeRepository;
import com.antigravity.mes.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * HR/CRUD management for employees, separate from {@link EmployeeService} which only
 * handles authentication - keeps auth logic and HR record-keeping independently
 * testable and change-safe (single responsibility).
 */
@Service
@DependsOn("cryptoConfig")
public class EmployeeManagementService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeInfoRepository employeeInfoRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private HrmsSyncService hrmsSyncService;

    public List<EmployeeSummaryResponse> listEmployees() {
        return employeeRepository.findAll().stream()
                .map(employee -> toSummary(employee, employeeInfoRepository.findByEmployeeId(employee.getId()).orElse(null)))
                .toList();
    }

    public EmployeeDetailResponse getEmployee(Long id) {
        Employee employee = requireEmployee(id);
        EmployeeInfo info = requireInfo(id);
        return toDetail(employee, info);
    }

    @Transactional
    public EmployeeDetailResponse createEmployee(EmployeeCreateRequest request) {
        if (isBlank(request.name())) {
            throw new IllegalArgumentException("Name is required");
        }
        if (isBlank(request.employeeCode())) {
            throw new IllegalArgumentException("Employee code is required");
        }
        Role role = validateRole(request.roleCode());

        if (employeeInfoRepository.existsByEmployeeCode(request.employeeCode())) {
            throw new ConflictException("Employee code is already in use");
        }

        String password = isBlank(request.password()) ? EmployeeService.DEFAULT_PASSWORD : request.password();
        Employee employee = new Employee(
                generateNextUsername(),
                passwordEncoder.encode(password),
                request.name(),
                Set.of(role));
        employee = employeeRepository.save(employee);

        EmployeeInfo info = new EmployeeInfo();
        info.setEmployee(employee);
        info.setEmployeeCode(request.employeeCode());
        info.setDepartment(request.department());
        info.setDesignation(request.designation());
        info.setDateOfJoining(request.dateOfJoining());
        if (!isBlank(request.shift())) {
            info.setShift(request.shift());
        }
        info.setDateOfBirth(request.dateOfBirth());
        if (!isBlank(request.gender())) {
            info.setGender(parseGender(request.gender()));
        }
        info.setBloodGroup(request.bloodGroup());
        info.setAddress(request.address());
        info.setEmergencyContactName(request.emergencyContactName());
        info.setEmergencyContactNumber(request.emergencyContactNumber());
        info.setEmergencyContactRelationship(request.emergencyContactRelationship());
        info.setBankName(request.bankName());
        info.setBankAccountNumber(request.bankAccountNumber());
        info.setIfscCode(request.ifscCode());
        info = employeeInfoRepository.save(info);

        applyHrmsSyncResult(info, hrmsSyncService.syncNewEmployee(employee, info));

        return toDetail(employee, info);
    }

    private void applyHrmsSyncResult(EmployeeInfo info, HrmsEmployeeOut synced) {
        if (!hrmsSyncService.isEnabled()) {
            return;
        }
        if (synced == null) {
            info.setHrmsSyncStatus(EmployeeInfo.HrmsSyncStatus.FAILED);
        } else {
            info.setHrmsEmployeeId(synced.id());
            info.setHrmsEmployeeCode(synced.employeeCode());
            info.setHrmsDeviceUserId(synced.deviceUserId());
            info.setHrmsSyncStatus(EmployeeInfo.HrmsSyncStatus.SYNCED);
            info.setHrmsSyncedAt(java.time.LocalDateTime.now());
        }
        employeeInfoRepository.save(info);
    }

    /** Pulls an employee that was created directly in HRMS (not through this app) into our system. */
    @Transactional
    public EmployeeDetailResponse importFromHrms(ImportFromHrmsRequest request) {
        if (isBlank(request.name())) {
            throw new IllegalArgumentException("Name is required");
        }
        if (isBlank(request.employeeCode())) {
            throw new IllegalArgumentException("Employee code is required");
        }
        Role role = validateRole(request.roleCode());

        if (employeeInfoRepository.existsByEmployeeCode(request.employeeCode())) {
            throw new ConflictException("Employee code is already in use");
        }

        Employee employee = new Employee(
                generateNextUsername(),
                passwordEncoder.encode(EmployeeService.DEFAULT_PASSWORD),
                request.name(),
                Set.of(role));
        employee = employeeRepository.save(employee);

        EmployeeInfo info = new EmployeeInfo();
        info.setEmployee(employee);
        info.setEmployeeCode(request.employeeCode());
        info.setDesignation(request.designation());
        info.setDepartment(request.department());
        info.setHrmsEmployeeId(request.hrmsEmployeeId());
        info.setHrmsEmployeeCode(request.hrmsEmployeeCode());
        info.setHrmsDeviceUserId(request.hrmsDeviceUserId());
        info.setHrmsSyncStatus(EmployeeInfo.HrmsSyncStatus.SYNCED);
        info.setHrmsSyncedAt(java.time.LocalDateTime.now());
        info = employeeInfoRepository.save(info);

        return toDetail(employee, info);
    }

    /**
     * One-click "sync all" - pulls in every HRMS employee with no local record yet,
     * assigning them all the same default role (HR can move individuals to a different
     * role afterward via the normal edit flow). One employee failing (e.g. a duplicate
     * employee code) never stops the rest of the batch.
     */
    @Transactional
    public BulkImportResult importAllFromHrms(String defaultRoleCode, String defaultDepartment) {
        validateRole(defaultRoleCode);
        boolean overrideDepartment = !isBlank(defaultDepartment);

        List<EmployeeDetailResponse> imported = new ArrayList<>();
        List<BulkImportResult.BulkImportFailure> failed = new ArrayList<>();

        for (HrmsEmployeeOut hrmsEmployee : hrmsSyncService.listUnlinkedEmployees()) {
            try {
                EmployeeDetailResponse created = importFromHrms(new ImportFromHrmsRequest(
                        hrmsEmployee.id(),
                        hrmsEmployee.employeeCode(),
                        hrmsEmployee.deviceUserId(),
                        hrmsEmployee.name(),
                        hrmsEmployee.designation(),
                        overrideDepartment ? defaultDepartment : hrmsEmployee.departmentName(),
                        defaultRoleCode,
                        hrmsEmployee.employeeCode()));
                imported.add(created);
            } catch (Exception e) {
                failed.add(new BulkImportResult.BulkImportFailure(
                        hrmsEmployee.employeeCode(), hrmsEmployee.name(), e.getMessage()));
            }
        }
        return new BulkImportResult(imported, failed);
    }

    /** Uploads a photo for HRMS face-recognition enrollment - the employee must already be synced. */
    public void enrollFace(Long employeeId, byte[] fileBytes, String filename) {
        EmployeeInfo info = requireInfo(employeeId);
        if (info.getHrmsEmployeeId() == null) {
            throw new IllegalArgumentException("This employee isn't synced to HRMS yet - face enrollment isn't available");
        }
        hrmsSyncService.enrollFace(info.getHrmsEmployeeId(), fileBytes, filename);
    }

    @Transactional
    public EmployeeDetailResponse updateEmployee(Long id, EmployeeUpdateRequest request) {
        Employee employee = requireEmployee(id);
        EmployeeInfo info = requireInfo(id);

        if (!isBlank(request.password())) {
            employee.setPasswordHash(passwordEncoder.encode(request.password()));
            // A password reset is often how HR rescues an employee who is locked out after
            // too many failed attempts - without clearing this too, the new password would
            // still be rejected by authenticate()'s isLocked() check until the lockout expires.
            employee.setFailedLoginAttempts(0);
            employee.setLockedUntil(null);
        }
        if (!isBlank(request.name())) {
            employee.setName(request.name());
        }
        // Role membership is managed exclusively via assignRoles()/the "Assign Roles" screen,
        // not this general edit form - avoids silently collapsing a multi-role employee back
        // to one role whenever HR edits an unrelated field like department.
        if (request.active() != null) {
            employee.setActive(request.active());
        }
        employeeRepository.save(employee);

        if (!isBlank(request.employeeCode()) && !request.employeeCode().equals(info.getEmployeeCode())) {
            if (employeeInfoRepository.existsByEmployeeCode(request.employeeCode())) {
                throw new ConflictException("Employee code is already in use");
            }
            info.setEmployeeCode(request.employeeCode());
        }
        if (request.department() != null) info.setDepartment(request.department());
        if (request.designation() != null) info.setDesignation(request.designation());
        if (request.dateOfJoining() != null) info.setDateOfJoining(request.dateOfJoining());
        if (!isBlank(request.shift())) info.setShift(request.shift());
        if (request.dateOfBirth() != null) info.setDateOfBirth(request.dateOfBirth());
        if (!isBlank(request.gender())) info.setGender(parseGender(request.gender()));
        if (request.bloodGroup() != null) info.setBloodGroup(request.bloodGroup());
        if (request.address() != null) info.setAddress(request.address());
        if (request.emergencyContactName() != null) info.setEmergencyContactName(request.emergencyContactName());
        if (request.emergencyContactNumber() != null) info.setEmergencyContactNumber(request.emergencyContactNumber());
        if (request.emergencyContactRelationship() != null) {
            info.setEmergencyContactRelationship(request.emergencyContactRelationship());
        }
        if (request.bankName() != null) info.setBankName(request.bankName());
        if (request.bankAccountNumber() != null) info.setBankAccountNumber(request.bankAccountNumber());
        if (request.ifscCode() != null) info.setIfscCode(request.ifscCode());
        employeeInfoRepository.save(info);

        return toDetail(employee, info);
    }

    /** Replaces the employee's full role set - the "Assign Roles" action on the employee detail screen. */
    @Transactional
    public EmployeeDetailResponse assignRoles(Long id, List<String> roleCodes) {
        Employee employee = requireEmployee(id);
        EmployeeInfo info = requireInfo(id);
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        Set<Role> roles = new LinkedHashSet<>();
        for (String roleCode : roleCodes) {
            roles.add(validateRole(roleCode));
        }
        employee.setRoles(roles);
        employeeRepository.save(employee);
        return toDetail(employee, info);
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        Employee employee = requireEmployee(id);
        employee.setActive(active);
        employeeRepository.save(employee);
    }

    /**
     * Permanently deletes the employee (employee_info cascades). Blocked with a clear
     * message if the employee is still referenced elsewhere (e.g. production records,
     * once those modules exist) rather than surfacing a raw SQL constraint error.
     */
    @Transactional
    public void deleteEmployee(Long id) {
        Employee employee = requireEmployee(id);
        try {
            employeeRepository.delete(employee);
            employeeRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "Cannot delete this employee - they have associated records elsewhere in the system. Deactivate instead.");
        }
    }

    /** Deletes each employee independently, so one with blocking associations doesn't stop the rest of the batch. */
    public BulkDeleteResult deleteEmployees(List<Long> ids) {
        int deleted = 0;
        List<BulkDeleteResult.BulkDeleteFailure> failures = new ArrayList<>();
        for (Long id : ids) {
            try {
                deleteEmployee(id);
                deleted++;
            } catch (Exception e) {
                failures.add(new BulkDeleteResult.BulkDeleteFailure(id, e.getMessage()));
            }
        }
        return new BulkDeleteResult(deleted, failures);
    }

    /** Self-service profile edit - only personal fields, never role/employeeCode/active. */
    @Transactional
    public EmployeeDetailResponse updateOwnProfile(Long employeeId, ProfileUpdateRequest request) {
        Employee employee = requireEmployee(employeeId);
        EmployeeInfo info = requireInfo(employeeId);

        if (!isBlank(request.name())) {
            employee.setName(request.name());
        }
        employeeRepository.save(employee);

        if (request.dateOfBirth() != null) info.setDateOfBirth(request.dateOfBirth());
        if (!isBlank(request.gender())) info.setGender(parseGender(request.gender()));
        if (request.bloodGroup() != null) info.setBloodGroup(request.bloodGroup());
        if (request.address() != null) info.setAddress(request.address());
        if (request.emergencyContactName() != null) info.setEmergencyContactName(request.emergencyContactName());
        if (request.emergencyContactNumber() != null) info.setEmergencyContactNumber(request.emergencyContactNumber());
        if (request.emergencyContactRelationship() != null) {
            info.setEmergencyContactRelationship(request.emergencyContactRelationship());
        }
        employeeInfoRepository.save(info);

        return toDetail(employee, info);
    }

    @Transactional
    public void changeOwnPassword(Long employeeId, String currentPassword, String newPassword) {
        Employee employee = requireEmployee(employeeId);
        if (isBlank(currentPassword) || !passwordEncoder.matches(currentPassword, employee.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (isBlank(newPassword) || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters");
        }
        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        employeeRepository.save(employee);
    }

    private Employee requireEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Employee not found: " + id));
    }

    private EmployeeInfo requireInfo(Long employeeId) {
        return employeeInfoRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee info not found for employee: " + employeeId));
    }

    /** Next sequential MES### username, e.g. MES001, MES002, ... MES1000. */
    private String generateNextUsername() {
        int maxSeq = employeeRepository.findAll().stream()
                .map(Employee::getUsername)
                .filter(u -> u != null && u.startsWith("MES"))
                .mapToInt(u -> {
                    try {
                        return Integer.parseInt(u.substring(3));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
        return String.format("MES%03d", maxSeq + 1);
    }

    private Role validateRole(String roleName) {
        if (isBlank(roleName)) {
            throw new IllegalArgumentException("Role is required");
        }
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleName));
        if (!role.isActive()) {
            throw new IllegalArgumentException("Role is disabled: " + roleName);
        }
        return role;
    }

    private EmployeeInfo.Gender parseGender(String gender) {
        try {
            return EmployeeInfo.Gender.valueOf(gender.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid gender: " + gender);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Deterministic display order for a multi-role employee's role list (alphabetical by role name). */
    private List<String> sortedRoleCodes(Employee employee) {
        return employee.getRoles().stream()
                .map(Role::getRoleName)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private EmployeeSummaryResponse toSummary(Employee employee, EmployeeInfo info) {
        List<String> roleCodes = sortedRoleCodes(employee);
        return new EmployeeSummaryResponse(
                employee.getId(),
                info != null ? info.getEmployeeCode() : null,
                employee.getName(),
                employee.getUsername(),
                roleCodes.isEmpty() ? null : roleCodes.get(0),
                roleCodes,
                info != null ? info.getDepartment() : null,
                info != null ? info.getDesignation() : null,
                info != null ? info.getShift() : null,
                employee.isActive());
    }

    private EmployeeDetailResponse toDetail(Employee employee, EmployeeInfo info) {
        List<String> roleCodes = sortedRoleCodes(employee);
        return new EmployeeDetailResponse(
                employee.getId(),
                info.getEmployeeCode(),
                employee.getName(),
                employee.getUsername(),
                roleCodes.isEmpty() ? null : roleCodes.get(0),
                roleCodes,
                employee.isActive(),
                info.getDepartment(),
                info.getDesignation(),
                info.getDateOfJoining(),
                info.getShift(),
                info.getDateOfBirth(),
                info.getGender() != null ? info.getGender().name() : null,
                info.getBloodGroup(),
                info.getAddress(),
                info.getEmergencyContactName(),
                info.getEmergencyContactNumber(),
                info.getEmergencyContactRelationship(),
                info.getBankName(),
                info.getBankAccountNumber(),
                info.getIfscCode(),
                info.getCreatedAt(),
                info.getHrmsEmployeeId(),
                info.getHrmsEmployeeCode(),
                info.getHrmsDeviceUserId(),
                info.getHrmsSyncStatus().name());
    }
}
