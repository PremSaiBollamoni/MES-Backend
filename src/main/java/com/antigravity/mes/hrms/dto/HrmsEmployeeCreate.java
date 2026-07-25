package com.antigravity.mes.hrms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors HRMS's EmployeeCreate schema (POST /api/employees) exactly - see /openapi.json on the HRMS host. */
public record HrmsEmployeeCreate(
        @JsonProperty("employee_code") String employeeCode,
        @JsonProperty("device_user_id") String deviceUserId,
        String name,
        String email,
        String phone,
        @JsonProperty("department_id") Integer departmentId,
        @JsonProperty("shift_id") Integer shiftId,
        String designation,
        @JsonProperty("attendance_type") String attendanceType,
        @JsonProperty("reporting_manager_id") Integer reportingManagerId
) {}
