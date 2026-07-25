package com.antigravity.mes.dto;

import java.time.LocalDate;

/**
 * All fields optional - only non-null values are applied. Password is only changed
 * when non-blank. Username is system-generated and not editable here.
 */
public record EmployeeUpdateRequest(
        String password,
        String name,
        String roleCode,
        Boolean active,
        String employeeCode,
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
        String emergencyContactRelationship
) {}
