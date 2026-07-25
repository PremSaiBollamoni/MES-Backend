package com.antigravity.mes.report;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReportSummaryResponse(Long id, ReportType reportType, LocalDate reportDate, LocalDateTime generatedAt, String modelUsed) {}
