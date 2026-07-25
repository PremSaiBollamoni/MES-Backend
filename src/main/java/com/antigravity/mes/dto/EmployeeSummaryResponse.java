package com.antigravity.mes.dto;

import java.util.List;

/** roleCode is the primary (first alphabetically) role, for call sites that only show one. roleCodes is the full set. */
public record EmployeeSummaryResponse(
        Long id,
        String employeeCode,
        String name,
        String username,
        String roleCode,
        List<String> roleCodes,
        String department,
        String designation,
        String shift,
        boolean active
) {}
