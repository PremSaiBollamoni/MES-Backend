package com.antigravity.mes.report;

import com.antigravity.mes.model.GeneratedReport;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** AI-generated production reports (Claude/Opus 4.8) - management-facing, read-only for anyone
 * with the REPORTS activity. Generation is expensive (a real model call), so it's explicitly
 * triggered and cached per date rather than computed on every read. */
@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReportController.class);

    @Autowired
    private ReportGenerationService reportGenerationService;

    @Autowired
    private PdfRenderingService pdfRenderingService;

    @RequiresActivity("REPORTS")
    @GetMapping
    public ResponseEntity<List<ReportSummaryResponse>> list(@RequestParam(required = false, defaultValue = "PRODUCTION") ReportType type) {
        return ResponseEntity.ok(reportGenerationService.listAll(type));
    }

    @RequiresActivity("REPORTS")
    @PostMapping("/generate")
    public ResponseEntity<?> generate(
            @RequestAttribute("employeeId") Long employeeId,
            @RequestParam String date,
            @RequestParam(required = false, defaultValue = "PRODUCTION") ReportType type,
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        try {
            return ResponseEntity.ok(reportGenerationService.generate(type, LocalDate.parse(date), force, employeeId));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Report generation failed for date {}", date, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "Report generation failed: " + e.getMessage()));
        }
    }

    @RequiresActivity("REPORTS")
    @GetMapping(value = "/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        GeneratedReport report = reportGenerationService.requireReport(id);
        try {
            byte[] pdf = pdfRenderingService.renderToPdf(report.getHtmlContent());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"production-report-" + report.getReportDate() + ".pdf\"")
                    .body(pdf);
        } catch (Exception e) {
            // PDF rendering can fail if the model produced non-well-formed XHTML - fall back to
            // serving the raw HTML rather than losing the report entirely.
            log.error("PDF rendering failed for report {} ({}), serving HTML fallback", id, report.getReportDate(), e);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(report.getHtmlContent().getBytes(StandardCharsets.UTF_8));
        }
    }
}
