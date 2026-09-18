package com.antigravity.mes.service;

import com.antigravity.mes.dto.BulkDeleteResult;
import com.antigravity.mes.dto.RoleCreateRequest;
import com.antigravity.mes.dto.RoleDetailResponse;
import com.antigravity.mes.dto.RoleUpdateRequest;
import com.antigravity.mes.exception.ConflictException;
import com.antigravity.mes.exception.NotFoundException;
import com.antigravity.mes.model.Role;
import com.antigravity.mes.repository.RoleRepository;
import com.antigravity.mes.repository.EmployeeRepository;
import com.antigravity.mes.web.ActivityCatalog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RoleManagementService {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    public List<RoleDetailResponse> listRoles() {
        return roleRepository.findAll().stream().map(this::toDetail).toList();
    }

    public RoleDetailResponse getRole(Long id) {
        return toDetail(requireRole(id));
    }

    @Transactional
    public RoleDetailResponse createRole(RoleCreateRequest request) {
        if (isBlank(request.roleName())) {
            throw new IllegalArgumentException("Role name is required");
        }
        String roleName = request.roleName().trim().toUpperCase();
        if (roleRepository.findByRoleName(roleName).isPresent()) {
            throw new ConflictException("Role name already exists: " + roleName);
        }
        List<String> activities = validateActivities(request.activities());

        Role role = new Role();
        role.setRoleName(roleName);
        role.setActivities(activities);
        role.setStatus(Role.Status.ACTIVE);
        return toDetail(roleRepository.save(role));
    }

    @Transactional
    public RoleDetailResponse updateRole(Long id, RoleUpdateRequest request) {
        Role role = requireRole(id);

        if (!isBlank(request.roleName())) {
            String roleName = request.roleName().trim().toUpperCase();
            roleRepository.findByRoleName(roleName).ifPresent(existing -> {
                if (!existing.getRoleId().equals(id)) {
                    throw new ConflictException("Role name already exists: " + roleName);
                }
            });
            role.setRoleName(roleName);
        }
        if (request.activities() != null) {
            role.setActivities(validateActivities(request.activities()));
        }
        if (!isBlank(request.status())) {
            try {
                role.setStatus(Role.Status.valueOf(request.status().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status: " + request.status());
            }
        }
        return toDetail(roleRepository.save(role));
    }

    /** Permanently deletes the role. Blocked (with a clear count) if any employee still holds it. */
    @Transactional
    public void deleteRole(Long id) {
        Role role = requireRole(id);
        long assignedCount = employeeRepository.findAll().stream()
                .filter(e -> e.getRoles().stream().anyMatch(r -> r.getRoleId().equals(id)))
                .count();
        if (assignedCount > 0) {
            throw new ConflictException(
                    "Cannot delete this role - it is assigned to " + assignedCount + " employee(s). Reassign them first.");
        }
        try {
            roleRepository.delete(role);
            roleRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Cannot delete this role - it is still referenced elsewhere.");
        }
    }

    /** Deletes each role independently, so one that's still assigned to employees doesn't block the rest of the batch. */
    public BulkDeleteResult deleteRoles(List<Long> ids) {
        int deleted = 0;
        List<BulkDeleteResult.BulkDeleteFailure> failures = new ArrayList<>();
        for (Long id : ids) {
            try {
                deleteRole(id);
                deleted++;
            } catch (Exception e) {
                failures.add(new BulkDeleteResult.BulkDeleteFailure(id, e.getMessage()));
            }
        }
        return new BulkDeleteResult(deleted, failures);
    }

    private List<String> validateActivities(List<String> activities) {
        if (activities == null || activities.isEmpty()) {
            throw new IllegalArgumentException("At least one activity is required");
        }
        for (String activity : activities) {
            if (!ActivityCatalog.ALL.contains(activity)) {
                throw new IllegalArgumentException("Unknown activity: " + activity);
            }
        }
        return activities;
    }

    private Role requireRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Role not found: " + id));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private RoleDetailResponse toDetail(Role role) {
        return new RoleDetailResponse(role.getRoleId(), role.getRoleName(), role.getActivities(), role.getStatus().name());
    }
}
