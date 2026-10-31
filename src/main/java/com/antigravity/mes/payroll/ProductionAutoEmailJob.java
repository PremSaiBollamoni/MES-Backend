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

/** Emails today's production summary every evening at 19:00. */
@Component
public class ProductionAutoEmailJob {

    private static final Logger log = LoggerFactory.getLogger(ProductionAutoEmailJob.class);

    @Value("${app.production.auto-email.enabled:false}")
    private boolean enabled;

    @Value("${app.production.auto-email.factory-id:1}")
    private Long factoryId;

    @Autowired
    private ProductionEmailService productionEmailService;

    @Autowired
    private MailRecipientService mailRecipientService;

    @Scheduled(cron = "0 0 19 * * *")
    public void sendDailyProductionEmail() {
        if (!enabled) {
            return;
        }
        List<String> recipients = mailRecipientService.emailsFor(MailRecipientService.ReportKind.PRODUCTION);
        if (recipients.isEmpty()) {
            log.warn("Daily production email is enabled but no active production recipients are set - skipping");
            return;
        }
        try {
            productionEmailService.sendDailyProductionEmail(LocalDate.now(), factoryId, recipients);
            log.info("Daily production email sent for {} to {}", LocalDate.now(), recipients);
        } catch (Exception e) {
            log.error("Daily production email failed", e);
        }
    }
}
