package com.antigravity.mes.controller;

import com.antigravity.mes.hrms.AttendanceSyncResult;
import com.antigravity.mes.hrms.AttendanceSyncService;
import com.antigravity.mes.hrms.HrmsClient;
import com.antigravity.mes.model.AttendanceRecord;
import com.antigravity.mes.repository.AttendanceRecordRepository;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Proxies the HRMS biometric attendance portal - credentials stay server-side, never reach the client. */
@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*")
public class AttendanceController {

    private static final int DEFAULT_SYNC_DAYS = 90;
    private static final int MAX_SYNC_DAYS = 366;

    @Autowired
    private HrmsClient hrmsClient;

    @Autowired
    private AttendanceSyncService attendanceSyncService;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @RequiresActivity("ATTENDANCE")
    @GetMapping("/live")
    public ResponseEntity<?> live() {
        if (!hrmsClient.isEnabled()) {
            return unavailable();
        }
        try {
            return ResponseEntity.ok(hrmsClient.getLiveStatus());
        } catch (ResourceAccessException e) {
            return unreachable();
        }
    }

    @RequiresActivity("ATTENDANCE")
    @GetMapping("/daily")
    public ResponseEntity<?> daily(@RequestParam(required = false) String date) {
        if (!hrmsClient.isEnabled()) {
            return unavailable();
        }
        try {
            LocalDate parsed = date != null ? LocalDate.parse(date) : null;
            return ResponseEntity.ok(hrmsClient.getDailyAttendance(parsed));
        } catch (ResourceAccessException e) {
            return unreachable();
        }
    }

    /** Pulls every day in the window from HRMS and upserts it into our local history. Safe to call repeatedly - re-syncs just overwrite the same rows. */
    @RequiresActivity("ATTENDANCE")
    @PostMapping("/sync")
    public ResponseEntity<?> sync(@RequestParam(required = false) Integer days) {
        if (!hrmsClient.isEnabled()) {
            return unavailable();
        }
        try {
            int window = days == null ? DEFAULT_SYNC_DAYS : Math.min(Math.max(days, 1), MAX_SYNC_DAYS);
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(window - 1L);
            AttendanceSyncResult result = attendanceSyncService.syncRange(from, to);
            return ResponseEntity.ok(result);
        } catch (ResourceAccessException e) {
            return unreachable();
        }
    }

    /**
     * Local attendance history (independent of HRMS's own retention window).
     * Supply an explicit {@code from}/{@code to} ISO date range (used by the Monthly view),
     * or a rolling {@code days} window, or an {@code employeeId} for one person's full history.
     */
    @RequiresActivity("ATTENDANCE")
    @GetMapping("/history")
    public ResponseEntity<List<AttendanceRecord>> history(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        if (employeeId != null) {
            return ResponseEntity.ok(attendanceRecordRepository.findByEmployeeIdOrderByWorkDateDesc(employeeId));
        }
        if (from != null && to != null) {
            return ResponseEntity.ok(
                    attendanceRecordRepository.findByWorkDateBetweenOrderByWorkDateDescEmployeeNameAsc(
                            LocalDate.parse(from), LocalDate.parse(to)));
        }
        int window = days == null ? 30 : Math.min(Math.max(days, 1), MAX_SYNC_DAYS);
        LocalDate rangeTo = LocalDate.now();
        LocalDate rangeFrom = rangeTo.minusDays(window - 1L);
        return ResponseEntity.ok(attendanceRecordRepository.findByWorkDateBetweenOrderByWorkDateDescEmployeeNameAsc(rangeFrom, rangeTo));
    }

    private ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "HRMS integration is not configured yet"));
    }

    /** The HRMS portal itself is unreachable right now (DNS/network/timeout) - not a config problem. */
    private ResponseEntity<Map<String, String>> unreachable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Couldn't reach the HRMS attendance portal - it may be offline or unreachable from this network. Try again shortly."));
    }
}
