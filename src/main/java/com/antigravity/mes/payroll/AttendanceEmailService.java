package com.antigravity.mes.payroll;

import com.antigravity.mes.email.BrandedEmailTemplate;
import com.antigravity.mes.email.BrandedEmailTemplate.StatCard;
import com.antigravity.mes.model.AttendanceRecord;
import com.antigravity.mes.model.Factory;
import com.antigravity.mes.repository.AttendanceRecordRepository;
import com.antigravity.mes.repository.EmployeeRepository;
import com.antigravity.mes.repository.FactoryRepository;
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

/** Emails a same-day attendance summary - stat-card style, with an AI-written PDF attached
 * when the report model is available. */
@Service
public class AttendanceEmailService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceEmailService.class);
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private FactoryRepository factoryRepository;

    @Autowired
    private MailSenderProvider mailSenderProvider;

    @Autowired
    private ReportGenerationService reportGenerationService;

    public void sendDailyAttendanceEmail(LocalDate date, Long factoryId, List<String> recipients) throws Exception {
        String factoryName = factoryId == null ? "SMO" : factoryRepository.findById(factoryId).map(Factory::getFactoryName).orElse("SMO");

        // SUPER_ADMIN's own employee row carries a placeholder factoryId and is not a real
        // member of the workforce - excluded the same way EmployeeManagementService does.
        long totalActive = employeeRepository.findByFactoryIdAndActiveTrue(factoryId).stream()
                .filter(e -> !e.hasActiveRoleNamed("SUPER_ADMIN"))
                .count();

        List<AttendanceRecord> records = attendanceRecordRepository.findByFactoryIdAndWorkDate(factoryId, date);
        long presentCount = records.stream().filter(r -> "present".equalsIgnoreCase(r.getStatus())).count();
        long lateCount = records.stream().filter(r -> "late".equalsIgnoreCase(r.getStatus())).count();
        long absentCount = records.stream().filter(r -> "absent".equalsIgnoreCase(r.getStatus())).count();
        double attendancePct = totalActive == 0 ? 0 : (presentCount + lateCount) * 100.0 / totalActive;

        String dateLabel = date.format(DATE_LABEL);
        String overview = "This is the attendance snapshot for " + factoryName + " on " + dateLabel
                + ", synced from the HRMS biometric attendance system. Out of " + totalActive
                + " active employees on record, " + presentCount + " clocked in on time and " + lateCount
                + " clocked in late, for a combined attendance rate of " + String.format("%.1f", attendancePct)
                + "%. " + absentCount + " employees have no clock-in recorded for today so far. These figures "
                + "reflect whatever has synced from the biometric system up to the time this email was sent, so "
                + "a late sync can still change the day's final numbers after this email goes out. For the full "
                + "per-employee breakdown, open the Attendance screen in the app.";

        byte[] pdf = attendancePdfOrNull(date, factoryId);
        String attachmentName = pdf == null ? null : "attendance-report-" + date + ".pdf";
        String html = BrandedEmailTemplate.build(
                factoryName.toUpperCase(),
                "Daily Attendance Report",
                overview,
                "DATE",
                dateLabel,
                List.of(
                        new StatCard("&#9989;", String.valueOf(presentCount), "Present"),
                        new StatCard("&#8986;", String.valueOf(lateCount), "Late"),
                        new StatCard("&#10060;", String.valueOf(absentCount), "Absent")),
                attachmentName,
                factoryName);

        MimeMessage message = mailSenderProvider.current().createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(recipients.toArray(new String[0]));
        helper.setSubject(factoryName + " Daily Attendance Report (" + dateLabel + ")");
        helper.setText(html, true);
        if (pdf != null) {
            helper.addAttachment(attachmentName, new ByteArrayResource(pdf));
        }

        mailSenderProvider.current().send(message);
    }

    /** The AI report is best-effort: if the model is down or rate-limited, the stat-card
     * email still goes out, just without the PDF, rather than the whole daily email failing. */
    private byte[] attendancePdfOrNull(LocalDate date, Long factoryId) {
        try {
            return reportGenerationService.dailyReportPdf(ReportType.ATTENDANCE, date, factoryId);
        } catch (Exception e) {
            log.warn("Attendance PDF report unavailable for {}, sending without attachment: {}", date, e.getMessage());
            return null;
        }
    }
}
