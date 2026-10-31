package com.antigravity.mes.mail;

import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** SUPER_ADMIN-only SMTP configuration. SUPER_ADMIN bypasses activity checks, and the
 * SMTP_CONFIGURATION activity is granted to no other role. */
@RestController
@RequestMapping("/api/mail-settings")
@CrossOrigin(origins = "*")
public class MailSettingsController {

    @Autowired
    private MailSettingsService mailSettingsService;

    public record SaveRequest(String smtpHost, Integer smtpPort, String smtpUsername, String smtpPassword) {}
    public record TestRequest(String recipient) {}

    @RequiresActivity("SMTP_CONFIGURATION")
    @GetMapping
    public ResponseEntity<MailSettingsService.View> get() {
        return ResponseEntity.ok(mailSettingsService.get());
    }

    @RequiresActivity("SMTP_CONFIGURATION")
    @PutMapping
    public ResponseEntity<?> save(@RequestBody SaveRequest request) {
        if (request.smtpHost() == null || request.smtpHost().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "SMTP host is required"));
        }
        if (request.smtpPort() == null || request.smtpPort() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "SMTP port is required"));
        }
        return ResponseEntity.ok(mailSettingsService.save(
                request.smtpHost(), request.smtpPort(), request.smtpUsername(), request.smtpPassword()));
    }

    @RequiresActivity("SMTP_CONFIGURATION")
    @PostMapping("/test")
    public ResponseEntity<?> test(@RequestBody TestRequest request) {
        if (request.recipient() == null || request.recipient().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recipient is required"));
        }
        try {
            mailSettingsService.sendTest(request.recipient().trim());
            return ResponseEntity.ok(Map.of("status", "SENT"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Test email failed - " + e.getMessage()));
        }
    }
}
