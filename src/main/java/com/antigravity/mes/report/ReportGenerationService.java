package com.antigravity.mes.report;

import com.antigravity.mes.ai.AiService;
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
import java.util.Optional;

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

    @Autowired
    private PdfRenderingService pdfRenderingService;

    /** Generates (or reuses the stored) AI-written report for one day and returns it as a
     * PDF - used by the scheduled/manual daily emails. Stored reports are reused so the same
     * day's email never costs a second model call. */
    public byte[] dailyReportPdf(ReportType reportType, LocalDate date, Long factoryId) throws Exception {
        ReportSummaryResponse summary = generate(reportType, date, PeriodType.DAILY, false, null, factoryId, false);
        GeneratedReport report = requireReport(summary.id(), factoryId, false);
        // A report cut off by the model's token cap is stored as-is - regenerate rather than
        // attach a PDF that stops mid-page.
        if (!report.getHtmlContent().contains("</html>")) {
            summary = generate(reportType, date, PeriodType.DAILY, true, null, factoryId, false);
            report = requireReport(summary.id(), factoryId, false);
        }
        return pdfRenderingService.renderToPdf(report.getHtmlContent());
    }

    public List<ReportSummaryResponse> listAll(ReportType reportType, Long factoryId) {
        return generatedReportRepository.findAllByFactoryIdAndReportTypeOrderByReportDateDesc(factoryId, reportType).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public ReportSummaryResponse generate(
            ReportType reportType, LocalDate date, PeriodType requestedPeriod, boolean force, Long generatedByEmployeeId,
            Long factoryId, boolean isSuperAdmin) throws Exception {
        // EARNINGS is always monthly; only ATTENDANCE lets the caller choose; everything else
        // (PRODUCTION/STOCK/WORKER_PERFORMANCE) has no monthly aggregation built, so stays DAILY
        // regardless of what's passed.
        PeriodType periodType = resolvePeriodType(reportType, requestedPeriod);
        LocalDate periodStart = periodStartOf(date, periodType);
        LocalDate periodEnd = periodType == PeriodType.MONTHLY ? periodStart.withDayOfMonth(periodStart.lengthOfMonth()) : periodStart;

        if (!force) {
            var existing = generatedReportRepository.findByFactoryIdAndReportDateAndReportTypeAndPeriodType(factoryId, periodStart, reportType, periodType);
            if (existing.isPresent()) return toSummary(existing.get());
        }
        if (!claudeReportService.isEnabled()) {
            throw new IllegalStateException("AI reports are not set up - choose a report model in AI settings");
        }

        Map<String, Object> metrics = switch (reportType) {
            case PRODUCTION -> dataAggregator.aggregateProduction(periodStart, factoryId, isSuperAdmin);
            case STOCK -> dataAggregator.aggregateStock(periodStart, factoryId, isSuperAdmin);
            case ATTENDANCE -> periodType == PeriodType.MONTHLY
                    ? dataAggregator.aggregateMonthlyAttendance(periodStart, periodEnd, factoryId)
                    : dataAggregator.aggregateAttendance(periodStart, factoryId, isSuperAdmin);
            case WORKER_PERFORMANCE -> dataAggregator.aggregateWorkerPerformance(periodStart, factoryId, isSuperAdmin);
            case EARNINGS -> dataAggregator.aggregateEarnings(periodStart, periodEnd, factoryId);
        };
        String periodLabel = periodType == PeriodType.MONTHLY
                ? periodStart.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                : periodStart.toString();
        AiService.AiReply reply = claudeReportService.generateHtmlReport(reportType, periodType, periodLabel, metrics);

        GeneratedReport report = generatedReportRepository.findByFactoryIdAndReportDateAndReportTypeAndPeriodType(factoryId, periodStart, reportType, periodType)
                .orElseGet(GeneratedReport::new);
        report.setFactoryId(factoryId);
        report.setReportDate(periodStart);
        report.setReportType(reportType);
        report.setPeriodType(periodType);
        report.setGeneratedByEmployeeId(generatedByEmployeeId);
        report.setHtmlContent(reply.text());
        report.setModelUsed(reply.model());
        return toSummary(generatedReportRepository.save(report));
    }

    /** The report already generated for this type, date and period, if there is one - lets the caller skip a model call. */
    public Optional<ReportSummaryResponse> findExisting(ReportType reportType, LocalDate date, PeriodType requestedPeriod, Long factoryId) {
        PeriodType periodType = resolvePeriodType(reportType, requestedPeriod);
        LocalDate periodStart = periodStartOf(date, periodType);
        return generatedReportRepository.findByFactoryIdAndReportDateAndReportTypeAndPeriodType(factoryId, periodStart, reportType, periodType)
                .map(this::toSummary);
    }

    // EARNINGS is always monthly; only ATTENDANCE lets the caller choose; everything else
    // (PRODUCTION/STOCK/WORKER_PERFORMANCE) has no monthly aggregation built, so stays DAILY
    // regardless of what's passed.
    private PeriodType resolvePeriodType(ReportType reportType, PeriodType requestedPeriod) {
        return switch (reportType) {
            case EARNINGS -> PeriodType.MONTHLY;
            case ATTENDANCE -> requestedPeriod == null ? PeriodType.DAILY : requestedPeriod;
            default -> PeriodType.DAILY;
        };
    }

    private LocalDate periodStartOf(LocalDate date, PeriodType periodType) {
        return periodType == PeriodType.MONTHLY ? date.withDayOfMonth(1) : date;
    }

    /** Enforces factory ownership - a non-super-admin caller gets "not found" (not
     * "forbidden") for a report belonging to a different factory. */
    public GeneratedReport requireReport(Long id, Long factoryId, boolean isSuperAdmin) {
        GeneratedReport report = generatedReportRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Report not found: " + id));
        if (!isSuperAdmin && factoryId != null && !factoryId.equals(report.getFactoryId())) {
            throw new NotFoundException("Report not found: " + id);
        }
        return report;
    }

    private ReportSummaryResponse toSummary(GeneratedReport report) {
        return new ReportSummaryResponse(report.getId(), report.getReportType(), report.getReportDate(), report.getPeriodType(), report.getCreatedAt(), report.getModelUsed());
    }
}
