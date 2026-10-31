package com.antigravity.mes.payroll;

import com.antigravity.mes.mail.MailRecipientService;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** HR-facing salary payroll sheet - a deterministic rollup of ProductionEntry wages per
 * operator for a date range, exported as a ready-to-use Excel workbook. Unlike the AI reports,
 * this is cheap arithmetic over existing data, so it is generated fresh on every request rather
 * than triggered-and-cached. */
@RestController
@RequestMapping("/api/payroll")
@CrossOrigin(origins = "*")
public class PayrollController {

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PayrollEmailService payrollEmailService;

    @Autowired
    private MailRecipientService mailRecipientService;

    public record SendEmailRequest(String startDate, String endDate, List<String> recipients) {}

    /** Same data the Excel sheet is built from, as JSON - lets HR preview every operator's
     * earnings breakdown live in the app before committing to a download. */
    @RequiresActivity("PAYROLL")
    @GetMapping("/preview")
    public ResponseEntity<List<PayrollService.OperatorPayroll>> preview(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestAttribute(required = false) Long factoryId) {
        return ResponseEntity.ok(payrollService.previewPayroll(LocalDate.parse(startDate), LocalDate.parse(endDate), factoryId));
    }

    @RequiresActivity("PAYROLL")
    @GetMapping("/xlsx")
    public ResponseEntity<byte[]> download(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestAttribute(required = false) Long factoryId) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        byte[] xlsx = payrollService.generatePayrollSheet(start, end, factoryId);
        String filename = "payroll_" + start + "_to_" + end + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(xlsx);
    }

    /** Manual "Send Mail" button - generates the same sheet as /xlsx and emails it
     * immediately instead of downloading, to whoever the caller lists as recipients. */
    @RequiresActivity("PAYROLL")
    @PostMapping("/send-email")
    public ResponseEntity<?> sendEmail(
            @RequestBody SendEmailRequest request,
            @RequestAttribute(required = false) Long factoryId) {
        List<String> recipients = request.recipients() == null || request.recipients().isEmpty()
                ? mailRecipientService.emailsFor(MailRecipientService.ReportKind.PAYROLL)
                : request.recipients();
        if (recipients.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No payroll recipients are set - add them in the Email tab"));
        }
        try {
            payrollEmailService.sendPayrollEmail(
                    LocalDate.parse(request.startDate()), LocalDate.parse(request.endDate()), factoryId, recipients);
            return ResponseEntity.ok(Map.of("status", "SENT"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Couldn't send the email - " + e.getMessage()));
        }
    }
}
