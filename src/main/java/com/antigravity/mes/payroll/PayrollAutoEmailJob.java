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

/** Auto-generates and emails the payroll sheet for the month that's ending, on the last day
 * of that month at 21:00. Cron has no native "last day of month" expression (months vary in
 * length), so this runs daily at 21:00 and simply no-ops unless today really is the last day -
 * checked by comparing today's date to the first day of next month. */
@Component
public class PayrollAutoEmailJob {

    private static final Logger log = LoggerFactory.getLogger(PayrollAutoEmailJob.class);

    @Value("${app.payroll.auto-email.enabled:false}")
    private boolean enabled;

    @Value("${app.payroll.auto-email.factory-id:1}")
    private Long factoryId;

    @Autowired
    private PayrollEmailService payrollEmailService;

    @Autowired
    private MailRecipientService mailRecipientService;

    @Scheduled(cron = "0 0 21 * * *")
    public void sendIfLastDayOfMonth() {
        if (!enabled) {
            return;
        }
        LocalDate today = LocalDate.now();
        boolean isLastDayOfMonth = today.plusDays(1).getMonthValue() != today.getMonthValue();
        if (!isLastDayOfMonth) {
            return;
        }

        List<String> recipients = mailRecipientService.emailsFor(MailRecipientService.ReportKind.PAYROLL);
        if (recipients.isEmpty()) {
            log.warn("Month-end payroll email is enabled but no active payroll recipients are set - skipping");
            return;
        }

        LocalDate monthStart = today.withDayOfMonth(1);
        try {
            payrollEmailService.sendPayrollEmail(monthStart, today, factoryId, recipients);
            log.info("Month-end payroll email sent for {} to {}", monthStart.getMonth(), recipients);
        } catch (Exception e) {
            log.error("Month-end payroll email failed", e);
        }
    }
}
