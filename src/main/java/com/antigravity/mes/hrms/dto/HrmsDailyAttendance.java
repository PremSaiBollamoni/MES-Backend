package com.antigravity.mes.hrms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors HRMS's DailyAttendanceOut schema (GET /api/attendance/daily). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HrmsDailyAttendance(
        Integer id,
        @JsonProperty("employee_id") Integer employeeId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("employee_code") String employeeCode,
        @JsonProperty("department_name") String departmentName,
        @JsonProperty("attendance_date") String attendanceDate,
        @JsonProperty("first_in") String firstIn,
        @JsonProperty("last_out") String lastOut,
        @JsonProperty("total_hours") Double totalHours,
        String status,
        @JsonProperty("is_late") Boolean isLate,
        @JsonProperty("late_minutes") Integer lateMinutes,
        @JsonProperty("early_exit") Boolean earlyExit,
        @JsonProperty("early_exit_minutes") Integer earlyExitMinutes
) {}
