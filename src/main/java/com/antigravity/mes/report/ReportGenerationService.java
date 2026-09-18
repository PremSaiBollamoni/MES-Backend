package com.antigravity.mes.report;

import com.antigravity.mes.exception.NotFoundException;
import com.antigravity.mes.model.GeneratedReport;
import com.antigravity.mes.repository.GeneratedReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates AI report generation for any ReportType: aggregate real data -> ask Claude to
 * narrate it -> store the HTML once. A report already generated for a (date, type) is reused
 * unless the caller explicitly asks to regenerate, so re-viewing a report never re-calls the model.
 */
@Service
public class ReportGenerationService {

    @Autowired
    private ReportDataAggregator dataAggregator;

    @Autowired
    private ClaudeReportService claudeReportService;

    @Autowired
    private GeneratedReportRepository generatedReportRepository;

    public List<ReportSummaryResponse> listAll(ReportType reportType) {
        return generatedReportRepository.findAllByReportTypeOrderByReportDateDesc(reportType).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public ReportSummaryResponse generate(
            ReportType reportType, LocalDate date, PeriodType requestedPeriod, boolean force, Long generatedByEmployeeId,
            String provider, String model) throws Exception {
        // EARNINGS is always monthly; only ATTENDANCE lets the caller choose; everything else
        // (PRODUCTION/STOCK/WORKER_PERFORMANCE) has no monthly aggregation built, so stays DAILY
        // regardless of what's passed.
        PeriodType periodType = switch (reportType) {
            case EARNINGS -> PeriodType.MONTHLY;
            case ATTENDANCE -> requestedPeriod == null ? PeriodType.DAILY : requestedPeriod;
            default -> PeriodType.DAILY;
        };
        LocalDate periodStart = periodType == PeriodType.MONTHLY ? date.withDayOfMonth(1) : date;
        LocalDate periodEnd = periodType == PeriodType.MONTHLY ? periodStart.withDayOfMonth(periodStart.lengthOfMonth()) : periodStart;

        if (!force) {
            var existing = generatedReportRepository.findByReportDateAndReportTypeAndPeriodType(periodStart, reportType, periodType);
            if (existing.isPresent()) return toSummary(existing.get());
        }
        if (!claudeReportService.isEnabled(provider)) {
            throw new IllegalStateException("AI reports are disabled - set the corresponding API key on the backend to enable them");
        }

        Map<String, Object> metrics = switch (reportType) {
            case PRODUCTION -> dataAggregator.aggregateProduction(periodStart);
            case STOCK -> dataAggregator.aggregateStock(periodStart);
            case ATTENDANCE -> periodType == PeriodType.MONTHLY
                    ? dataAggregator.aggregateMonthlyAttendance(periodStart, periodEnd)
                    : dataAggregator.aggregateAttendance(periodStart);
            case WORKER_PERFORMANCE -> dataAggregator.aggregateWorkerPerformance(periodStart);
            case EARNINGS -> dataAggregator.aggregateEarnings(periodStart, periodEnd);
        };
        String periodLabel = periodType == PeriodType.MONTHLY
                ? periodStart.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                : periodStart.toString();
        String html = claudeReportService.generateHtmlReport(reportType, periodType, periodLabel, metrics, provider, model);

        GeneratedReport report = generatedReportRepository.findByReportDateAndReportTypeAndPeriodType(periodStart, reportType, periodType)
                .orElseGet(GeneratedReport::new);
        report.setReportDate(periodStart);
        report.setReportType(reportType);
        report.setPeriodType(periodType);
        report.setGeneratedByEmployeeId(generatedByEmployeeId);
        report.setHtmlContent(html);
        report.setModelUsed(claudeReportService.getModelUsed(provider, model));
        return toSummary(generatedReportRepository.save(report));
    }

    public GeneratedReport requireReport(Long id) {
        return generatedReportRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Report not found: " + id));
    }

    private ReportSummaryResponse toSummary(GeneratedReport report) {
        return new ReportSummaryResponse(report.getId(), report.getReportType(), report.getReportDate(), report.getPeriodType(), report.getCreatedAt(), report.getModelUsed());
    }
}
