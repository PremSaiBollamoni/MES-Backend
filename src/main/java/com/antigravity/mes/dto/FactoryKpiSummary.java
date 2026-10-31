package com.antigravity.mes.dto;

/** One factory's cross-cutting KPIs for the SUPER_ADMIN overview - workforce, today's
 * production, today's attendance, and month-to-date payroll, all in one row so every
 * factory can be compared at a glance. */
public record FactoryKpiSummary(
        Long factoryId,
        String factoryName,
        String factoryCode,
        boolean active,
        long totalEmployees,
        long activeEmployees,
        long inactiveEmployees,
        long stationCount,
        int todayProductionPieces,
        long todayProductionEntries,
        long todayAttendancePresent,
        long todayAttendanceTotal,
        double monthToDatePayroll
) {}
