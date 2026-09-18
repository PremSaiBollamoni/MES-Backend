package com.antigravity.mes.controller;

import com.antigravity.mes.model.Employee;
import com.antigravity.mes.model.Role;
import com.antigravity.mes.security.JwtService;
import com.antigravity.mes.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private JwtService jwtService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Connected successfully to MES Backend");
        response.put("database", "MySQL (MES)");
        return ResponseEntity.ok(response);
    }

    /**
     * Verifies username + password. An employee with exactly one active role logs in
     * immediately (unchanged behaviour). An employee with more than one active role gets
     * a role-selection prompt instead of a full session token - see {@link #selectRole}.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        if (username == null || password == null) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Username and password are required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        return employeeService.authenticate(username, password)
                .<ResponseEntity<?>>map(employee -> {
                    Set<Role> activeRoles = employee.getActiveRoles();
                    if (activeRoles.size() == 1) {
                        return ResponseEntity.ok(successResponse(employee, activeRoles.iterator().next()));
                    }
                    return ResponseEntity.ok(roleSelectionResponse(employee, activeRoles));
                })
                .orElseGet(() -> {
                    Map<String, String> errorResponse = new HashMap<>();
                    errorResponse.put("error", "Invalid username or password");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
                });
    }

    /**
     * Lets an already-signed-in employee switch which of their held roles is active,
     * without re-entering a password - the "Switch Role" drawer action. Only reachable
     * if they actually hold the requested role and it's still active.
     */
    @PostMapping("/switch-role")
    public ResponseEntity<?> switchRole(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> request) {
        Optional<Employee> employeeOpt = resolveEmployeeFromBearer(authHeader);
        if (employeeOpt.isEmpty()) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid or expired session - please log in again");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }

        String roleName = request.get("roleName");
        Employee employee = employeeOpt.get();
        if (roleName == null || !employee.hasActiveRoleNamed(roleName)) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "You do not hold that role");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        }

        Role chosenRole = employee.getActiveRoles().stream()
                .filter(r -> r.getRoleName().equals(roleName))
                .findFirst()
                .orElseThrow();
        return ResponseEntity.ok(successResponse(employee, chosenRole));
    }

    private Optional<Employee> resolveEmployeeFromBearer(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        return jwtService.validate(authHeader.substring(7))
                .flatMap(principal -> employeeService.findActiveById(principal.employeeId()));
    }

    /** Second step for multi-role employees: exchange the role-selection token + a chosen role for a full session token. */
    @PostMapping("/select-role")
    public ResponseEntity<?> selectRole(@RequestBody Map<String, String> request) {
        String preAuthToken = request.get("preAuthToken");
        String roleName = request.get("roleName");

        if (preAuthToken == null || roleName == null) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "preAuthToken and roleName are required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        Optional<Long> employeeId = jwtService.validateRoleSelectionToken(preAuthToken);
        if (employeeId.isEmpty()) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Role selection has expired - please log in again");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }

        Optional<Employee> employeeOpt = employeeService.findActiveById(employeeId.get());
        if (employeeOpt.isEmpty()) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Account is no longer available");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }

        Employee employee = employeeOpt.get();
        if (!employee.hasActiveRoleNamed(roleName)) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "You do not hold that role");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        }

        Role chosenRole = employee.getActiveRoles().stream()
                .filter(r -> r.getRoleName().equals(roleName))
                .findFirst()
                .orElseThrow();
        return ResponseEntity.ok(successResponse(employee, chosenRole));
    }

    private Map<String, Object> successResponse(Employee employee, Role role) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", employee.getId());
        response.put("name", employee.getName());
        response.put("username", employee.getUsername());
        response.put("role", role.getRoleName());
        response.put("activities", role.getActivities());
        response.put("token", jwtService.issueToken(employee.getId(), role.getRoleName()));
        // Every role this employee currently holds (not just the active one) - lets the
        // client show/hide "Switch Role" without a separate lookup.
        response.put("availableRoles", employee.getActiveRoles().stream().map(Role::getRoleName).sorted().toList());
        return response;
    }

    private Map<String, Object> roleSelectionResponse(Employee employee, Set<Role> activeRoles) {
        List<Map<String, Object>> roles = new ArrayList<>();
        for (Role role : activeRoles) {
            Map<String, Object> roleInfo = new HashMap<>();
            roleInfo.put("roleId", role.getRoleId());
            roleInfo.put("roleName", role.getRoleName());
            roles.add(roleInfo);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("requiresRoleSelection", true);
        response.put("id", employee.getId());
        response.put("name", employee.getName());
        response.put("username", employee.getUsername());
        response.put("roles", roles);
        response.put("preAuthToken", jwtService.issueRoleSelectionToken(employee.getId()));
        return response;
    }
}
