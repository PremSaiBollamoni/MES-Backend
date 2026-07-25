package com.antigravity.mes.model;

import com.antigravity.mes.report.ReportType;
import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One AI-generated production report for one calendar day. HTML is stored once;
 * the PDF is rendered from it on demand at download time (see PdfRenderingService),
 * so re-downloading a report never re-calls Claude. */
@Entity
@Table(name = "generated_reports")
public class GeneratedReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30)
    private ReportType reportType;

    @Column(name = "generated_by_employee_id", nullable = false)
    private Long generatedByEmployeeId;

    @Lob
    @Column(name = "html_content", nullable = false, columnDefinition = "LONGTEXT")
    private String htmlContent;

    @Column(name = "model_used", nullable = false, length = 50)
    private String modelUsed;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }

    public ReportType getReportType() { return reportType; }
    public void setReportType(ReportType reportType) { this.reportType = reportType; }

    public Long getGeneratedByEmployeeId() { return generatedByEmployeeId; }
    public void setGeneratedByEmployeeId(Long generatedByEmployeeId) { this.generatedByEmployeeId = generatedByEmployeeId; }

    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
