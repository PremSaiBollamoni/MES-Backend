package com.antigravity.mes.hrms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Mirrors HRMS's LiveStatusOut schema (GET /api/attendance/live). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HrmsLiveStatus(
        List<Map<String, Object>> present,
        List<Map<String, Object>> absent,
        List<Map<String, Object>> late,
        @JsonProperty("total_employees") Integer totalEmployees,
        @JsonProperty("present_count") Integer presentCount,
        @JsonProperty("absent_count") Integer absentCount,
        @JsonProperty("late_count") Integer lateCount,
        @JsonProperty("as_of") String asOf
) {}
