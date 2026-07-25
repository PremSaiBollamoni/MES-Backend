package com.antigravity.mes.report;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/**
 * Turns the real, aggregated production data for one day into a full narrative report by
 * calling Claude (Opus 4.8). Claude only ever sees the pre-computed metrics JSON - it is
 * instructed to narrate and analyze that data, never invent numbers, and to skip any section
 * PALMS doesn't actually track rather than fabricate one.
 */
@Service
public class ClaudeReportService {

    private static final String MODEL_NAME = "claude-haiku-4-5-20251001";
    private static final Model MODEL = Model.CLAUDE_HAIKU_4_5_20251001;

    @Value("${app.anthropic.enabled}")
    private boolean enabled;

    @Value("${app.anthropic.api-key}")
    private String apiKey;

    @Autowired
    private ObjectMapper objectMapper;

    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public String getModelUsed() {
        return MODEL_NAME;
    }

    public String generateHtmlReport(ReportType reportType, LocalDate reportDate, Map<String, Object> metrics) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("AI reports are disabled - set ANTHROPIC_API_KEY to enable them");
        }

        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        String metricsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metrics);

        // Haiku 4.5 doesn't support adaptive thinking or the effort parameter (effort errors
        // outright on this tier) - plain request, no thinking config.
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(64000L)
                .system(systemPrompt(reportType, reportDate))
                .addUserMessage("Here is today's real, pre-computed data. Generate the full report from it:\n\n" + metricsJson)
                .build();

        StringBuilder html = new StringBuilder();
        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
            stream.stream()
                    .flatMap(event -> event.contentBlockDelta().stream())
                    .flatMap(deltaEvent -> deltaEvent.delta().text().stream())
                    .forEach(textDelta -> html.append(textDelta.text()));
        }

        return cleanHtml(html.toString());
    }

    /** Claude sometimes wraps output in a markdown code fence despite instructions not to - strip it defensively. */
    private String cleanHtml(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) trimmed = trimmed.substring(firstNewline + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private String systemPrompt(ReportType reportType, LocalDate reportDate) {
        // Plain placeholder replacement, NOT String.formatted() - the prompt bodies contain
        // literal '%' characters (CSS widths, percentages) that the Formatter would choke on.
        String template = switch (reportType) {
            case PRODUCTION -> PRODUCTION_PROMPT_TEMPLATE;
            case STOCK -> STOCK_PROMPT_TEMPLATE;
            case ATTENDANCE -> ATTENDANCE_PROMPT_TEMPLATE;
            case WORKER_PERFORMANCE -> WORKER_PERFORMANCE_PROMPT_TEMPLATE;
        };
        return (OUTPUT_FORMAT_RULES + template).replace("{{REPORT_DATE}}", reportDate.toString());
    }

    /** Shared across all report types: the XHTML/CSS-2.1 rendering constraints imposed by the
     * Flying Saucer PDF pipeline, and the anti-fabrication rules. */
    private static final String OUTPUT_FORMAT_RULES = """
                You will be given ONE JSON object containing every real, currently-tracked metric for a
                single date. This is your ONLY source of truth. Every number you write in the report MUST
                come directly from this JSON. Never invent, estimate, extrapolate, or "round for effect"
                any statistic that is not present in the data. If a metric isn't in the JSON, do not
                mention a number for it.

                The JSON includes a `dataNotTracked` array listing report topics this system does not
                currently measure. COMPLETELY OMIT any section covering those topics - do not create a
                placeholder section, do not write "no data available", just skip them entirely as if they
                were never requested. Similarly, there is only ONE day of data - do not fabricate a
                multi-day trend. Where a "trend" style section is natural, base it on the spread/variance
                ACROSS today's stations/employees instead, and say so explicitly rather than implying
                multi-day history.

                OUTPUT FORMAT - this is critical:
                - Output ONLY a single, complete, self-contained XHTML document. No markdown, no code
                  fences, no commentary before or after - the very first characters must be
                  "<!DOCTYPE html>" and the last must be "</html>".
                - The HTML will be rendered to PDF by a Java renderer (Flying Saucer / OpenPDF) that only
                  reliably supports CSS 2.1: use <table> for any grid/column layout, plain <div>/<span>
                  with inline-block or block display, and background-color for KPI cards. DO NOT use
                  flexbox, CSS grid, SVG, canvas, or JavaScript - none of it will render.
                - Build "charts" using simple HTML/CSS only: horizontal bar charts as a <div> with a fixed
                  pixel width container and an inner colored <div style="width:XX%"> proportional to the
                  value, or styled <table> cells for distributions. This is the only chart technique that
                  reliably renders in the PDF pipeline.
                - Use only base PDF fonts: Helvetica, Times New Roman, or Courier (via font-family) - any
                  other font will silently fall back and may look inconsistent.
                - Include an @page CSS rule for sensible print margins, and use page-break-inside: avoid
                  on cards/tables so they don't split awkwardly across pages.
                - Escape all XML special characters properly (&amp; not &, etc.) - the document must be
                  well-formed XML or PDF rendering will fail outright.
                - Valid XHTML must be self-closing: <br/>, <img .../>, no unclosed tags.

                Write in a clear, professional, data-grounded tone suitable for management review. Use
                real numbers from the JSON throughout - this must read as an analysis of actual factory
                data, not a generic template.

                """;

    private static final String PRODUCTION_PROMPT_TEMPLATE = """
                You are a senior manufacturing analytics consultant producing a professional, board-ready
                production report for a garment factory (GramTarang Garments), based on PALMS - their
                Parallel Assembly Line Management System.

                REPORT STRUCTURE:
                1. Title, exactly: "GramTarang Garments Employee Production Report"
                2. Directly below the title: "Report Date: {{REPORT_DATE}}"
                3. Executive Summary - a tight, decision-maker-focused overview of the day
                4. Overall Production Summary (with KPI cards: total pieces, total wages, avg efficiency, entries logged)
                5. Employee Performance Analysis
                6. Top Performers (from employeePerformance.topPerformers)
                7. Lowest Performers (from employeePerformance.lowestPerformers) - frame constructively, not punitively
                8. Station-wise Production Analysis (table from stationWise)
                9. Target vs. Actual Production (use targetVsActualPct per station)
                10. Efficiency Analysis and Production Distribution (use productionSummary.classificationDistribution as a bar chart)
                11. Attendance vs. Production Correlation (use attendance + allocation data)
                12. Station Manning / Utilization (use stationManning)
                13. Bottleneck Detection (use the bottleneck object - explain the difference between the
                    designed/SAM-based bottleneck and today's actual weakest-performing station)
                14. Stock Availability (use stockAvailability - only stations with issues are included, say so if the list is empty)
                15. Daily Stock Position (table from dailyStockPosition)
                16. WIP Analysis (use wipSnapshot - clearly label it as a live/current snapshot, not historical to the report date)
                17. Risk Indicators (use riskIndicators verbatim as the core list, you may add short qualitative context per item)
                18. Recommendations for Improving Productivity - concrete, grounded in the specific
                    numbers above (e.g. name the actual weakest station/employee), not generic advice
                19. AI-Powered Insights and Observations - your own deeper analysis: correlations,
                    anomalies, and what a factory manager should actually do tomorrow morning

                Write in a clear, professional, data-grounded tone suitable for management review. Use
                real numbers from the JSON throughout - this must read as an analysis of actual factory
                data, not a generic template.
                """;

    private static final String STOCK_PROMPT_TEMPLATE = """
                You are a senior supply-chain / production-planning analyst producing a professional,
                board-ready station-wise stock report for a garment factory (GramTarang Garments), based
                on PALMS - their Parallel Assembly Line Management System.

                REPORT STRUCTURE:
                1. Title, exactly: "GramTarang Garments Station-wise Stock Report"
                2. Directly below the title: "Report Date: {{REPORT_DATE}}"
                3. Executive Summary - overall stock health across the factory floor today
                4. Station-wise Stock Status (table from stationWiseStock - every station, its zone,
                   current stock qty, threshold, and status)
                5. Stock Shortages and Alerts (from stockShortages - stations currently NO_STOCK or
                   LOW_STOCK; say explicitly if none)
                6. Daily Stock Position by Zone (table from dailyStockPosition: opening balance,
                   additions, dispatched, closing balance, alert flag)
                7. WIP Snapshot (from wipSnapshot - clearly label it as a live/current snapshot, not
                   historical to the report date; highlight any stationsWithWipAlert)
                8. Risk Indicators (use riskIndicators verbatim as the core list, add short qualitative
                   context per item)
                9. Recommendations - concrete actions to prevent stockouts or clear WIP backlogs,
                   naming the actual affected stations/zones
                10. AI-Powered Insights - correlations between stock shortages and specific
                    zones/stations, and what a planner should act on first tomorrow morning

                Write in a clear, professional, data-grounded tone suitable for management review. Use
                real numbers from the JSON throughout - this must read as an analysis of actual factory
                data, not a generic template.
                """;

    private static final String ATTENDANCE_PROMPT_TEMPLATE = """
                You are a senior HR/workforce analyst producing a professional, board-ready attendance
                report for a garment factory (GramTarang Garments), based on PALMS - their Parallel
                Assembly Line Management System.

                REPORT STRUCTURE:
                1. Title, exactly: "GramTarang Garments Attendance Report"
                2. Directly below the title: "Report Date: {{REPORT_DATE}}"
                3. Executive Summary - overall attendance health for the day
                4. Attendance Summary (KPI cards from attendanceSummary: total present, total late,
                   on-time percentage)
                5. Attendance Register (table from attendanceRegister: employee, department, first-in,
                   last-out, total hours, status, lateness)
                6. Present vs. Allocated Operators (use allocation data - present operators who were
                   never assigned to a station today)
                7. Station Manning / Utilization (use stationManning - which stations were adequately
                   staffed today)
                8. Risk Indicators (use riskIndicators verbatim as the core list, add short qualitative
                   context per item)
                9. Recommendations - concrete workforce-planning actions grounded in the specific
                   numbers above (e.g. name actually-late employees or unmanned critical stations)
                10. AI-Powered Insights - patterns in lateness/absence and their likely operational
                    impact, and what an HR/floor manager should act on first tomorrow morning

                Write in a clear, professional, data-grounded tone suitable for management review. Use
                real numbers from the JSON throughout - this must read as an analysis of actual factory
                data, not a generic template.
                """;

    private static final String WORKER_PERFORMANCE_PROMPT_TEMPLATE = """
                You are a senior industrial-engineering / performance-management analyst producing a
                professional, board-ready worker performance report for a garment factory (GramTarang
                Garments), based on PALMS - their Parallel Assembly Line Management System.

                REPORT STRUCTURE:
                1. Title, exactly: "GramTarang Garments Worker Performance Report"
                2. Directly below the title: "Report Date: {{REPORT_DATE}}"
                3. Executive Summary - overall workforce performance for the day
                4. Full Worker Performance Ranking (table from fullEmployeeRanking: every employee with
                   entries today, ranked by average efficiency, with pieces produced, wages earned, and
                   classification)
                5. Top Performers (from employeePerformance.topPerformers) - highlight what they did well
                6. Lowest Performers (from employeePerformance.lowestPerformers) - frame constructively,
                   identify likely causes (station difficulty, attendance, etc.), not punitively
                7. Efficiency Distribution (bar chart of classification counts across fullEmployeeRanking)
                8. Individual Production Entries (table from productionEntries: employee, station,
                   pieces, time on machine, efficiency, wages)
                9. Attendance Context (use attendanceSummary - note whether performance issues correlate
                   with lateness/absence, only where the data actually supports it)
                10. Risk Indicators (use riskIndicators verbatim as the core list, add short qualitative
                    context per item)
                11. Recommendations - concrete, individual, grounded in specific named employees/stations,
                    not generic advice
                12. AI-Powered Insights - deeper analysis of performance patterns and what a supervisor
                    should act on first tomorrow morning

                Write in a clear, professional, data-grounded tone suitable for management review. Use
                real numbers from the JSON throughout - this must read as an analysis of actual factory
                data, not a generic template.
                """;
}
