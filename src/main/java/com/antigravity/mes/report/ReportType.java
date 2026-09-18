package com.antigravity.mes.report;

/** The distinct AI-generated report kinds PALMS can produce. Each has its own data aggregation
 * and prompt, but shares the same generate/store/render-to-PDF pipeline. */
public enum ReportType {
    PRODUCTION,
    STOCK,
    ATTENDANCE,
    WORKER_PERFORMANCE,
    EARNINGS
}
