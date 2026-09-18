package com.antigravity.mes.hrms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors HRMS's EmployeeOut schema (GET /api/employees). Extra HRMS-only fields are ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HrmsEmployeeOut(
        Integer id,
        @JsonProperty("employee_code") String employeeCode,
        @JsonProperty("device_user_id") String deviceUserId,
        String name,
        String email,
        String phone,
        String designation,
        @JsonProperty("is_active") Boolean isActive,
        @JsonProperty("department_name") String departmentName,
        @JsonProperty("shift_name") String shiftName
) {}
