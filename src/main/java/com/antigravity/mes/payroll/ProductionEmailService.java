package com.antigravity.mes.payroll;

import com.antigravity.mes.email.BrandedEmailTemplate;
import com.antigravity.mes.email.BrandedEmailTemplate.StatCard;
import com.antigravity.mes.model.Factory;
import com.antigravity.mes.model.ProductionEntry;
import com.antigravity.mes.repository.FactoryRepository;
import com.antigravity.mes.repository.ProductionEntryRepository;
import com.antigravity.mes.report.ReportGenerationService;
import com.antigravity.mes.report.ReportType;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import com.antigravity.mes.mail.MailSenderProvider;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/** Emails a same-day production summary - stat-card style (same shared template as payroll
 * and attendance), no AI call and no attachment, so it's fast and free to send every evening. */
@Service
public class ProductionEmailService {

    private static final Logger log = LoggerFactory.getLogger(ProductionEmailService.class);
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @Autowired
    private ProductionEntryRepository productionEntryRepository;

    @Autowired
    private FactoryRepository factoryRepository;

    @Autowired
    private MailSenderProvider mailSenderProvider;

    @Autowired
    private ReportGenerationService reportGenerationService;

    public void sendDailyProductionEmail(LocalDate date, Long factoryId, List<String> recipients) throws Exception {
        String factoryName = factoryId == null ? "SMO" : factoryRepository.findById(factoryId).map(Factory::getFactoryName).orElse("SMO");

        List<ProductionEntry> entries = productionEntryRepository.findByFactoryIdAndShiftDate(factoryId, date);

        int totalPieces = entries.stream().mapToInt(ProductionEntry::getPiecesProduced).sum();
        double totalEarnings = entries.stream()
                .mapToDouble(e -> (e.getPiecesProduced() * e.getStation().getRatePerPiece()) / e.getStation().getOperatorCount())
                .sum();
        Set<Long> distinctOperators = entries.stream().map(e -> e.getEmployee().getId()).collect(java.util.stream.Collectors.toSet());
        double avgEfficiency = entries.isEmpty() ? 0 : entries.stream().mapToDouble(ProductionEntry::getEfficiencyPct).average().orElse(0);

        String dateLabel = date.format(DATE_LABEL);
        String total = String.format("%,.2f", totalEarnings);
        String overview = "This is the production summary for " + factoryName + " on " + dateLabel
                + ", compiled from every production entry logged on the floor during the day. "
                + distinctOperators.size() + " operators logged work across the day's entries, producing a combined "
                + totalPieces + " pieces at an average efficiency of " + String.format("%.1f", avgEfficiency)
                + "%. Based on the currently configured rate per piece for each station, the day's work represents "
                + "a wage value of ₹" + total + " once rolled up into tomorrow's or month-end's payroll run. "
                + "These numbers reflect whatever had been logged in the app at the time this email was sent, so a late "
                + "entry can still add to today's totals after this email goes out. For the full per-operator, "
                + "per-station breakdown, open Reports or Payroll preview in the app.";

        byte[] pdf = productionPdfOrNull(date, factoryId);
        String attachmentName = pdf == null ? null : "production-report-" + date + ".pdf";
        String html = BrandedEmailTemplate.build(
                factoryName.toUpperCase(),
                "Daily Production Report",
                overview,
                "DATE",
                dateLabel,
                List.of(
                        new StatCard("&#128101;", String.valueOf(distinctOperators.size()), "Operators"),
                        new StatCard("&#9986;", String.valueOf(totalPieces), "Pieces"),
                        new StatCard("&#9889;", String.format("%.0f", avgEfficiency) + "%", "Avg Efficiency")),
                attachmentName,
                factoryName);

        MimeMessage message = mailSenderProvider.current().createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(recipients.toArray(new String[0]));
        helper.setSubject(factoryName + " Daily Production Report (" + dateLabel + ")");
        helper.setText(html, true);
        if (pdf != null) {
            helper.addAttachment(attachmentName, new ByteArrayResource(pdf));
        }

        mailSenderProvider.current().send(message);
    }

    /** The AI report is best-effort: if the model is down or rate-limited, the stat-card
     * email still goes out, just without the PDF, rather than the whole daily email failing. */
    private byte[] productionPdfOrNull(LocalDate date, Long factoryId) {
        try {
            return reportGenerationService.dailyReportPdf(ReportType.PRODUCTION, date, factoryId);
        } catch (Exception e) {
            log.warn("Production PDF report unavailable for {}, sending without attachment: {}", date, e.getMessage());
            return null;
        }
    }
}
