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
import com.antigravity.mes.model.Factory;
import com.antigravity.mes.repository.EmployeeInfoRepository;
import com.antigravity.mes.repository.EmployeeRepository;
import com.antigravity.mes.repository.FactoryRepository;
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
    private FactoryRepository factoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private HrmsSyncService hrmsSyncService;

    /**
     * SUPER_ADMIN sees every factory's employees; anyone else sees only their own
     * factory's - this was previously unfiltered (a real cross-factory data leak).
     * SUPER_ADMIN accounts themselves are always excluded: they carry a placeholder
     * factory_id (the employees table requires one) but are a system-wide login, not
     * a factory's HR-managed employee, and must never appear in - or be touched by -
     * any factory-scoped employee list, edit, or username-renumbering action.
     */
    public List<EmployeeSummaryResponse> listEmployees(Long factoryId, boolean isSuperAdmin) {
        return employeeRepository.findAll().stream()
                .filter(employee -> !employee.hasActiveRoleNamed("SUPER_ADMIN"))
                .filter(employee -> isSuperAdmin || factoryId == null || factoryId.equals(employee.getFactoryId()))
                .map(employee -> toSummary(employee, employeeInfoRepository.findByEmployeeId(employee.getId()).orElse(null)))
                .toList();
    }

    public EmployeeDetailResponse getEmployee(Long id, Long factoryId, boolean isSuperAdmin) {
        Employee employee = requireEmployeeInFactory(id, factoryId, isSuperAdmin);
        EmployeeInfo info = requireInfo(id);
        return toDetail(employee, info);
    }

    @Transactional
    public EmployeeDetailResponse createEmployee(EmployeeCreateRequest request, Long factoryId) {
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
                factoryId,
                generateNextUsername(factoryId),
                passwordEncoder.encode(password),
                request.name(),
                Set.of(role));
        employee = employeeRepository.save(employee);

        EmployeeInfo info = new EmployeeInfo();
        info.setFactoryId(factoryId);
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

        applyHrmsSyncResult(info, hrmsSyncService.syncNewEmployee(employee, info), factoryId);

        return toDetail(employee, info);
    }

    private void applyHrmsSyncResult(EmployeeInfo info, HrmsEmployeeOut synced, Long factoryId) {
        if (!hrmsSyncService.isEnabled(factoryId)) {
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
    public EmployeeDetailResponse importFromHrms(ImportFromHrmsRequest request, Long factoryId) {
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
                factoryId,
                generateNextUsername(factoryId),
                passwordEncoder.encode(EmployeeService.DEFAULT_PASSWORD),
                request.name(),
                Set.of(role));
        employee = employeeRepository.save(employee);

        EmployeeInfo info = new EmployeeInfo();
        info.setFactoryId(factoryId);
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
    public BulkImportResult importAllFromHrms(String defaultRoleCode, String defaultDepartment, Long factoryId) {
        validateRole(defaultRoleCode);
        boolean overrideDepartment = !isBlank(defaultDepartment);

        List<EmployeeDetailResponse> imported = new ArrayList<>();
        List<BulkImportResult.BulkImportFailure> failed = new ArrayList<>();

        for (HrmsEmployeeOut hrmsEmployee : hrmsSyncService.listUnlinkedEmployees(factoryId)) {
            try {
                EmployeeDetailResponse created = importFromHrms(new ImportFromHrmsRequest(
                        hrmsEmployee.id(),
                        hrmsEmployee.employeeCode(),
                        hrmsEmployee.deviceUserId(),
                        hrmsEmployee.name(),
                        hrmsEmployee.designation(),
                        overrideDepartment ? defaultDepartment : hrmsEmployee.departmentName(),
                        defaultRoleCode,
                        hrmsEmployee.employeeCode()), factoryId);
                imported.add(created);
            } catch (Exception e) {
                failed.add(new BulkImportResult.BulkImportFailure(
                        hrmsEmployee.employeeCode(), hrmsEmployee.name(), e.getMessage()));
            }
        }
        return new BulkImportResult(imported, failed);
    }

    /** Uploads a photo for HRMS face-recognition enrollment - the employee must already be synced. */
    public void enrollFace(Long employeeId, byte[] fileBytes, String filename, Long factoryId, boolean isSuperAdmin) {
        requireEmployeeInFactory(employeeId, factoryId, isSuperAdmin);
        EmployeeInfo info = requireInfo(employeeId);
        if (info.getHrmsEmployeeId() == null) {
            throw new IllegalArgumentException("This employee isn't synced to HRMS yet - face enrollment isn't available");
        }
        hrmsSyncService.enrollFace(factoryId, info.getHrmsEmployeeId(), fileBytes, filename);
    }

    @Transactional
    public EmployeeDetailResponse updateEmployee(Long id, EmployeeUpdateRequest request, Long factoryId, boolean isSuperAdmin) {
        Employee employee = requireEmployeeInFactory(id, factoryId, isSuperAdmin);
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
    public EmployeeDetailResponse assignRoles(Long id, List<String> roleCodes, Long factoryId, boolean isSuperAdmin) {
        Employee employee = requireEmployeeInFactory(id, factoryId, isSuperAdmin);
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
    public void setActive(Long id, boolean active, Long factoryId, boolean isSuperAdmin) {
        Employee employee = requireEmployeeInFactory(id, factoryId, isSuperAdmin);
        employee.setActive(active);
        employeeRepository.save(employee);
    }

    /**
     * Permanently deletes the employee (employee_info cascades). Blocked with a clear
     * message if the employee is still referenced elsewhere (e.g. production records,
     * once those modules exist) rather than surfacing a raw SQL constraint error.
     */
    @Transactional
    public void deleteEmployee(Long id, Long factoryId, boolean isSuperAdmin) {
        Employee employee = requireEmployeeInFactory(id, factoryId, isSuperAdmin);
        try {
            employeeRepository.delete(employee);
            employeeRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "Cannot delete this employee - they have associated records elsewhere in the system. Deactivate instead.");
        }
    }

    /** Deletes each employee independently, so one with blocking associations doesn't stop the rest of the batch. */
    public BulkDeleteResult deleteEmployees(List<Long> ids, Long factoryId, boolean isSuperAdmin) {
        int deleted = 0;
        List<BulkDeleteResult.BulkDeleteFailure> failures = new ArrayList<>();
        for (Long id : ids) {
            try {
                deleteEmployee(id, factoryId, isSuperAdmin);
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

    /**
     * Renumbers every employee in a factory to {@code <factoryCode><seq>} (e.g.
     * GTGBZA001, GTGBZA002, ...), ordered by employee id so the sequence is stable
     * and repeatable. Uses a two-phase rename (temporary placeholder usernames first,
     * then final ones) because usernames are globally unique - assigning final values
     * directly, one row at a time, can collide with another employee's *current*
     * username mid-batch even though the end state has no duplicates.
     *
     * This changes real login credentials. The caller is responsible for getting the
     * returned old->new mapping to HR so affected employees can be told their new
     * username before they're locked out of their old one.
     */
    @Transactional
    public List<UsernameChange> fixUsernamesForFactory(Long factoryId) {
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new NotFoundException("Factory not found: " + factoryId));
        String prefix = factory.getFactoryCode();
        if (isBlank(prefix)) {
            throw new IllegalArgumentException("This factory has no factory code set - set one first");
        }

        // SUPER_ADMIN accounts carry this factory's id as a placeholder but are not
        // real factory employees - never renumber them.
        List<Employee> employees = employeeRepository.findByFactoryId(factoryId).stream()
                .filter(e -> !e.hasActiveRoleNamed("SUPER_ADMIN"))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        employees.sort(Comparator.comparing(Employee::getId));

        List<String> oldUsernames = employees.stream().map(Employee::getUsername).toList();

        // Phase 1: move everyone in this factory to a collision-proof placeholder.
        for (Employee e : employees) {
            e.setUsername("__TMP__" + e.getId());
        }
        employeeRepository.saveAll(employees);
        employeeRepository.flush();

        // Phase 2: assign final sequential, factory-code-prefixed usernames. Only
        // employees whose username actually changes are reported back - a re-run on an
        // already-fixed factory should report zero changes, not everyone unconditionally.
        List<UsernameChange> changes = new ArrayList<>();
        int seq = 1;
        for (int i = 0; i < employees.size(); i++) {
            Employee e = employees.get(i);
            String newUsername = String.format("%s%03d", prefix, seq++);
            e.setUsername(newUsername);
            if (!oldUsernames.get(i).equals(newUsername)) {
                changes.add(new UsernameChange(e.getId(), e.getName(), oldUsernames.get(i), newUsername));
            }
        }
        employeeRepository.saveAll(employees);

        return changes;
    }

    public record UsernameChange(Long employeeId, String employeeName, String oldUsername, String newUsername) {}

    private Employee requireEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Employee not found: " + id));
    }

    /**
     * Same lookup, but also enforces factory ownership - a non-super-admin caller gets
     * "not found" (not "forbidden") for an employee in a different factory, so this
     * endpoint can never be used to even confirm another factory's employee ID exists.
     */
    private Employee requireEmployeeInFactory(Long id, Long factoryId, boolean isSuperAdmin) {
        Employee employee = requireEmployee(id);
        if (!isSuperAdmin && factoryId != null && !factoryId.equals(employee.getFactoryId())) {
            throw new NotFoundException("Employee not found: " + id);
        }
        return employee;
    }

    private EmployeeInfo requireInfo(Long employeeId) {
        return employeeInfoRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee info not found for employee: " + employeeId));
    }

    /**
     * Next sequential username scoped to the employee's factory, prefixed with that
     * factory's own code - e.g. factory code GTGBZA produces GTGBZA001, GTGBZA002, ...
     * Falls back to "MES" if the factory has no code yet (shouldn't happen - factoryCode
     * is required at factory creation) so employee creation never hard-fails on this.
     * Usernames are globally unique by policy, so the search for the next number scans
     * every employee's username (not just this factory's) that starts with the prefix.
     */
    private String generateNextUsername(Long factoryId) {
        String prefix = factoryRepository.findById(factoryId)
                .map(Factory::getFactoryCode)
                .filter(code -> code != null && !code.isBlank())
                .orElse("MES");

        int maxSeq = employeeRepository.findAll().stream()
                .map(Employee::getUsername)
                .filter(u -> u != null && u.startsWith(prefix))
                .mapToInt(u -> {
                    try {
                        return Integer.parseInt(u.substring(prefix.length()));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
        return String.format("%s%03d", prefix, maxSeq + 1);
    }

    /** Roles are global - shared and identically named across every factory. */
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
