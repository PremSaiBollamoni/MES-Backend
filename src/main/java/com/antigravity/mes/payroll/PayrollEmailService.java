package com.antigravity.mes.payroll;

import com.antigravity.mes.email.BrandedEmailTemplate;
import com.antigravity.mes.email.BrandedEmailTemplate.StatCard;
import com.antigravity.mes.model.Factory;
import com.antigravity.mes.repository.FactoryRepository;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import com.antigravity.mes.mail.MailSenderProvider;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Emails the generated payroll Excel sheet - used by both the manual "Send Mail" button
 * (PayrollController) and the automatic month-end job (PayrollAutoEmailJob). */
@Service
public class PayrollEmailService {

    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private FactoryRepository factoryRepository;

    @Autowired
    private MailSenderProvider mailSenderProvider;

    public void sendPayrollEmail(LocalDate startDate, LocalDate endDate, Long factoryId, List<String> recipients) throws Exception {
        List<PayrollService.OperatorPayroll> rows = payrollService.previewPayroll(startDate, endDate, factoryId);
        byte[] xlsx = payrollService.generatePayrollSheet(startDate, endDate, factoryId);
        String filename = "payroll_" + startDate + "_to_" + endDate + ".xlsx";
        String factoryName = factoryId == null ? "SMO" : factoryRepository.findById(factoryId).map(Factory::getFactoryName).orElse("SMO");

        double grandTotal = rows.stream().mapToDouble(PayrollService.OperatorPayroll::totalEarnings).sum();
        String period = startDate.format(DATE_LABEL) + " &ndash; " + endDate.format(DATE_LABEL);
        String total = String.format("%,.2f", grandTotal);

        String overview = "This is the consolidated salary payroll sheet for " + factoryName
                + " covering the period from " + startDate.format(DATE_LABEL) + " to " + endDate.format(DATE_LABEL)
                + ". It has been generated directly from the production entries logged on the floor during this window, "
                + "so every figure in the attached workbook traces back to a real, timestamped entry rather than an "
                + "estimate. Each operator's earnings are broken down station by station, showing the pieces produced, "
                + "the currently configured rate per piece, and the resulting wage for that operation, so the calculation "
                + "behind every number is fully transparent and easy to verify against Station Management if needed. "
                + "The sheet also records who logged each entry, which helps trace any discrepancy back to its source "
                + "without having to dig through the app. Bank details are included wherever they are on file, so the "
                + "sheet can be used directly for processing payments without a separate lookup step. Please review the "
                + "figures before disbursing salaries, and reach out to the admin team if anything looks off so it can "
                + "be corrected at the source.";

        String html = BrandedEmailTemplate.build(
                factoryName.toUpperCase(),
                "Payroll Sheet Ready",
                overview,
                "PERIOD",
                period,
                List.of(
                        new StatCard("&#128101;", String.valueOf(rows.size()), "Operators"),
                        new StatCard("&#128176;", "&#8377;" + total, "Total Payout")),
                filename,
                factoryName);

        MimeMessage message = mailSenderProvider.current().createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(recipients.toArray(new String[0]));
        helper.setSubject(factoryName + " Payroll Sheet ("
                + startDate.format(DATE_LABEL) + " - " + endDate.format(DATE_LABEL) + ")");
        helper.setText(html, true);
        helper.addAttachment(filename, new ByteArrayResource(xlsx));

        mailSenderProvider.current().send(message);
    }
}
