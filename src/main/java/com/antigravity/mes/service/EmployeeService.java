package com.antigravity.mes.service;

import com.antigravity.mes.model.Employee;
import com.antigravity.mes.model.EmployeeInfo;
import com.antigravity.mes.model.LoginAudit;
import com.antigravity.mes.model.Role;
import com.antigravity.mes.repository.EmployeeInfoRepository;
import com.antigravity.mes.repository.EmployeeRepository;
import com.antigravity.mes.repository.LoginAuditRepository;
import com.antigravity.mes.repository.RoleRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * Depends on cryptoConfig so its @PostConstruct (loading the AES key used by
 * EmployeeInfo's encrypted fields) always runs before this bean's own @PostConstruct
 * seeds demo employees.
 */
@Service
@DependsOn("cryptoConfig")
public class EmployeeService {

    /** Default password assigned to every employee unless HR sets a custom one. */
    public static final String DEFAULT_PASSWORD = "MES2K26";

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeInfoRepository employeeInfoRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LoginAuditRepository loginAuditRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostConstruct
    public void seedEmployees() {
        if (employeeRepository.count() == 0) {
            // Seed a demo employee for each role, usernames MES001.. in order.
            // All demo employees share the system default password. Every seeded
            // employee also gets an employee_info row (department/designation/etc.) -
            // without it, the HR/Profile endpoints (which always join to employee_info)
            // would 404 for these accounts.
            // All seeded employees are assigned to factory_id = 1 (Default Factory)
            seed(1L, "MES001", "Amit Kumar", "FACTORY_MANAGER", "Management", "Factory Manager");
            seed(1L, "MES002", "Rajesh Sharma", "PRODUCTION_SUPERVISOR", "Production", "Production Supervisor");
            seed(1L, "MES003", "Sita Devi", "IE_ANALYST", "Industrial Engineering", "IE Analyst");
            seed(1L, "MES004", "Vijay Singh", "OPERATOR", "Sewing", "Sewing Operator");
            seed(1L, "MES005", "Priya Verma", "QC_INSPECTOR", "Quality Control", "QC Inspector");
            seed(1L, "MES006", "Ravi Teja", "STORE_KEEPER", "Stores", "Store Keeper");
            seed(1L, "MES007", "Meena Iyer", "HR_MANAGER", "Human Resources", "HR Manager");
            System.out.println("Demo employees seeded successfully!");
        }
    }

    private void seed(Long factoryId, String username, String name, String roleName, String department, String designation) {
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));
        Employee employee = employeeRepository.save(
                new Employee(factoryId, username, passwordEncoder.encode(DEFAULT_PASSWORD), name, Set.of(role)));

        EmployeeInfo info = new EmployeeInfo();
        info.setFactoryId(factoryId);
        info.setEmployee(employee);
        info.setEmployeeCode(username);
        info.setDepartment(department);
        info.setDesignation(designation);
        info.setDateOfJoining(LocalDate.now());
        employeeInfoRepository.save(info);
    }

    /**
     * Authenticates by username + password. Usernames are globally unique by policy
     * (never reused across factories, even though the schema also permits per-factory
     * scoping) - so the factory a session runs as is resolved from the matched
     * employee's own record, not chosen by the caller. No login-time factory selection
     * is needed. Failed attempts are rate-limited via a rolling lockout, and every
     * attempt (success or failure) is recorded to {@code login_audit} for traceability.
     * A disabled role (status = INACTIVE) is treated the same as a failed login - it
     * never reveals to the caller *why* access was denied.
     */
    public Optional<Employee> authenticate(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim().toUpperCase();
        Optional<Employee> employeeOpt = employeeRepository.findByUsername(normalizedUsername);

        if (employeeOpt.isEmpty()) {
            loginAuditRepository.save(new LoginAudit(null, normalizedUsername, LoginAudit.Result.FAILURE, null));
            return Optional.empty();
        }

        Employee employee = employeeOpt.get();

        if (!employee.isActive() || employee.isLocked() || employee.getActiveRoles().isEmpty()) {
            loginAuditRepository.save(new LoginAudit(employee.getId(), normalizedUsername, LoginAudit.Result.FAILURE, null));
            return Optional.empty();
        }

        if (!passwordEncoder.matches(password, employee.getPasswordHash())) {
            registerFailedAttempt(employee);
            loginAuditRepository.save(new LoginAudit(employee.getId(), normalizedUsername, LoginAudit.Result.FAILURE, null));
            return Optional.empty();
        }

        employee.setFailedLoginAttempts(0);
        employee.setLockedUntil(null);
        employeeRepository.save(employee);
        loginAuditRepository.save(new LoginAudit(employee.getId(), normalizedUsername, LoginAudit.Result.SUCCESS, null));
        return Optional.of(employee);
    }

    /** Used by the role-selection step of login - the employee must still be active (not deactivated mid-flow). */
    public Optional<Employee> findActiveById(Long id) {
        return employeeRepository.findById(id).filter(Employee::isActive);
    }

    private void registerFailedAttempt(Employee employee) {
        employee.setFailedLoginAttempts(employee.getFailedLoginAttempts() + 1);
        if (employee.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
            employee.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
        }
        employeeRepository.save(employee);
    }
}
