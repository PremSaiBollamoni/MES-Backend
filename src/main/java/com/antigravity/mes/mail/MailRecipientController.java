package com.antigravity.mes.mail;

import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Managing the recipient list is SUPER_ADMIN-only (SMTP_CONFIGURATION). Payroll users can
 * read just the payroll addresses, so the Send Mail dialog can pre-fill them. */
@RestController
@RequestMapping("/api/mail-recipients")
@CrossOrigin(origins = "*")
public class MailRecipientController {

    @Autowired
    private MailRecipientService service;

    public record RecipientRequest(String email, String name, boolean receivesPayroll,
                                   boolean receivesAttendance, boolean receivesProduction, boolean active) {}

    @RequiresActivity("SMTP_CONFIGURATION")
    @GetMapping
    public ResponseEntity<List<MailRecipient>> list() {
        return ResponseEntity.ok(service.list());
    }

    @RequiresActivity("PAYROLL")
    @GetMapping("/payroll-emails")
    public ResponseEntity<List<String>> payrollEmails() {
        return ResponseEntity.ok(service.emailsFor(MailRecipientService.ReportKind.PAYROLL));
    }

    @RequiresActivity("SMTP_CONFIGURATION")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody RecipientRequest request) {
        try {
            return ResponseEntity.ok(service.create(request.email(), request.name(), request.receivesPayroll(),
                    request.receivesAttendance(), request.receivesProduction(), request.active()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @RequiresActivity("SMTP_CONFIGURATION")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody RecipientRequest request) {
        try {
            return ResponseEntity.ok(service.update(id, request.email(), request.name(), request.receivesPayroll(),
                    request.receivesAttendance(), request.receivesProduction(), request.active()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @RequiresActivity("SMTP_CONFIGURATION")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
