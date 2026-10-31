package com.antigravity.mes.payroll;

import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Manual "send now" triggers for the daily attendance and production emails - same
 * recipient-list shape as the payroll send-email endpoint. */
@RestController
@RequestMapping("/api/reports/email")
@CrossOrigin(origins = "*")
public class DailyReportEmailController {

    @Autowired
    private AttendanceEmailService attendanceEmailService;

    @Autowired
    private ProductionEmailService productionEmailService;

    public record DailyEmailRequest(String date, List<String> recipients) {}

    @RequiresActivity("REPORTS")
    @PostMapping("/attendance")
    public ResponseEntity<?> sendAttendance(
            @RequestBody DailyEmailRequest request,
            @RequestAttribute(required = false) Long factoryId) {
        return send(request, factoryId, true);
    }

    @RequiresActivity("REPORTS")
    @PostMapping("/production")
    public ResponseEntity<?> sendProduction(
            @RequestBody DailyEmailRequest request,
            @RequestAttribute(required = false) Long factoryId) {
        return send(request, factoryId, false);
    }

    private ResponseEntity<?> send(DailyEmailRequest request, Long factoryId, boolean attendance) {
        if (request.recipients() == null || request.recipients().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "At least one recipient is required"));
        }
        try {
            LocalDate date = request.date() == null ? LocalDate.now() : LocalDate.parse(request.date());
            if (attendance) {
                attendanceEmailService.sendDailyAttendanceEmail(date, factoryId, request.recipients());
            } else {
                productionEmailService.sendDailyProductionEmail(date, factoryId, request.recipients());
            }
            return ResponseEntity.ok(Map.of("status", "SENT"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Couldn't send the email - " + e.getMessage()));
        }
    }
}
