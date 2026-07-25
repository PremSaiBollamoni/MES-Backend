package com.antigravity.mes.dto;

import java.time.LocalDate;

/**
 * Username is system-generated (MES001, MES002, ...), not supplied here.
 * password is optional - if blank, {@link com.antigravity.mes.service.EmployeeService#DEFAULT_PASSWORD} is used.
 */
public record EmployeeCreateRequest(
        String password,
        String name,
        String roleCode,
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
