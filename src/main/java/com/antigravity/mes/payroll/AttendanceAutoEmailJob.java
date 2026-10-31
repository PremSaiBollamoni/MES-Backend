package com.antigravity.mes.payroll;

import com.antigravity.mes.mail.MailRecipientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Emails today's attendance summary every morning at 09:30. */
@Component
public class AttendanceAutoEmailJob {

    private static final Logger log = LoggerFactory.getLogger(AttendanceAutoEmailJob.class);

    @Value("${app.attendance.auto-email.enabled:false}")
    private boolean enabled;

    @Value("${app.attendance.auto-email.factory-id:1}")
    private Long factoryId;

    @Autowired
    private AttendanceEmailService attendanceEmailService;

    @Autowired
    private MailRecipientService mailRecipientService;

    @Scheduled(cron = "0 30 9 * * *")
    public void sendDailyAttendanceEmail() {
        if (!enabled) {
            return;
        }
        List<String> recipients = mailRecipientService.emailsFor(MailRecipientService.ReportKind.ATTENDANCE);
        if (recipients.isEmpty()) {
            log.warn("Daily attendance email is enabled but no active attendance recipients are set - skipping");
            return;
        }
        try {
            attendanceEmailService.sendDailyAttendanceEmail(LocalDate.now(), factoryId, recipients);
            log.info("Daily attendance email sent for {} to {}", LocalDate.now(), recipients);
        } catch (Exception e) {
            log.error("Daily attendance email failed", e);
        }
    }
}
