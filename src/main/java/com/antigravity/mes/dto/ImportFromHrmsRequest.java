package com.antigravity.mes.dto;

/** Pulls an employee HR created directly in HRMS into this app. HRMS has no concept of app roles, so one is required here. */
public record ImportFromHrmsRequest(
        Integer hrmsEmployeeId,
        String hrmsEmployeeCode,
        String hrmsDeviceUserId,
        String name,
        String designation,
        String department,
        String roleCode,
        String employeeCode
) {}
