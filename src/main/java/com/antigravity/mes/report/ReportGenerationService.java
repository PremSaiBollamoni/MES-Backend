package com.antigravity.mes.report;

import com.antigravity.mes.exception.NotFoundException;
import com.antigravity.mes.model.GeneratedReport;
import com.antigravity.mes.repository.GeneratedReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    public ReportSummaryResponse generate(ReportType reportType, LocalDate date, boolean force, Long generatedByEmployeeId) throws Exception {
        if (!force) {
            var existing = generatedReportRepository.findByReportDateAndReportType(date, reportType);
            if (existing.isPresent()) return toSummary(existing.get());
        }
        if (!claudeReportService.isEnabled()) {
            throw new IllegalStateException("AI reports are disabled - set ANTHROPIC_API_KEY on the backend to enable them");
        }

        Map<String, Object> metrics = switch (reportType) {
            case PRODUCTION -> dataAggregator.aggregateProduction(date);
            case STOCK -> dataAggregator.aggregateStock(date);
            case ATTENDANCE -> dataAggregator.aggregateAttendance(date);
            case WORKER_PERFORMANCE -> dataAggregator.aggregateWorkerPerformance(date);
        };
        String html = claudeReportService.generateHtmlReport(reportType, date, metrics);

        GeneratedReport report = generatedReportRepository.findByReportDateAndReportType(date, reportType)
                .orElseGet(GeneratedReport::new);
        report.setReportDate(date);
        report.setReportType(reportType);
        report.setGeneratedByEmployeeId(generatedByEmployeeId);
        report.setHtmlContent(html);
        report.setModelUsed(claudeReportService.getModelUsed());
        return toSummary(generatedReportRepository.save(report));
    }

    public GeneratedReport requireReport(Long id) {
        return generatedReportRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Report not found: " + id));
    }

    private ReportSummaryResponse toSummary(GeneratedReport report) {
        return new ReportSummaryResponse(report.getId(), report.getReportType(), report.getReportDate(), report.getCreatedAt(), report.getModelUsed());
    }
}
