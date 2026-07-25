package com.antigravity.mes.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** roleCode is the primary (first alphabetically) role, for call sites that only show one. roleCodes is the full set. */
public record EmployeeDetailResponse(
        Long id,
        String employeeCode,
        String name,
        String username,
        String roleCode,
        List<String> roleCodes,
        boolean active,
        String department,
        String designation,
        LocalDate dateOfJoining,
        String shift,
        LocalDate dateOfBirth,
        String gender,
        String bloodGroup,
        String address,
        String emergencyContactName,
        String emergencyContactNumber,
        String emergencyContactRelationship,
        LocalDateTime createdAt,
        Integer hrmsEmployeeId,
        String hrmsEmployeeCode,
        String hrmsDeviceUserId,
        String hrmsSyncStatus
) {}
