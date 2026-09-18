package com.antigravity.mes.dto;

import java.time.LocalDate;

/** Self-editable fields only - role, employeeCode, department/designation stay HR-controlled. */
public record ProfileUpdateRequest(
        String name,
        LocalDate dateOfBirth,
        String gender,
        String bloodGroup,
        String address,
        String emergencyContactName,
        String emergencyContactNumber,
        String emergencyContactRelationship
) {}
