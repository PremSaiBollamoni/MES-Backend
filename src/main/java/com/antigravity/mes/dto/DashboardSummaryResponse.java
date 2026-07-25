package com.antigravity.mes.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardSummaryResponse(
        long totalEmployees,
        long activeEmployees,
        long inactiveEmployees,
        Map<String, Long> employeesByRole,
        Map<String, Long> employeesByDepartment,
        List<RecentJoiner> recentJoiners
) {
    public record RecentJoiner(String name, String employeeCode, String department, LocalDate dateOfJoining) {}
}
