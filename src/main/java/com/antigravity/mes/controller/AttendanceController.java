package com.antigravity.mes.controller;

import com.antigravity.mes.hrms.AttendanceSyncResult;
import com.antigravity.mes.hrms.AttendanceSyncService;
import com.antigravity.mes.hrms.HrmsClient;
import com.antigravity.mes.model.AttendanceRecord;
import com.antigravity.mes.repository.AttendanceRecordRepository;
import com.antigravity.mes.web.ErrorCodes;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
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
    public ResponseEntity<?> live(@RequestAttribute(required = false) Long factoryId) {
        if (!hrmsClient.isEnabled(factoryId)) {
            return unavailable();
        }
        try {
            return ResponseEntity.ok(hrmsClient.getLiveStatus(factoryId));
        } catch (ResourceAccessException e) {
            return unreachable();
        } catch (HttpClientErrorException.Unauthorized e) {
            return rejected();
        }
    }

    @RequiresActivity("ATTENDANCE")
    @GetMapping("/daily")
    public ResponseEntity<?> daily(
            @RequestParam(required = false) String date,
            @RequestAttribute(required = false) Long factoryId) {
        if (!hrmsClient.isEnabled(factoryId)) {
            return unavailable();
        }
        try {
            LocalDate parsed = date != null ? LocalDate.parse(date) : null;
            return ResponseEntity.ok(hrmsClient.getDailyAttendance(factoryId, parsed));
        } catch (ResourceAccessException e) {
            return unreachable();
        } catch (HttpClientErrorException.Unauthorized e) {
            return rejected();
        }
    }

    /** Pulls every day in the window from HRMS and upserts it into our local history. Safe to call repeatedly - re-syncs just overwrite the same rows. */
    @RequiresActivity("ATTENDANCE")
    @PostMapping("/sync")
    public ResponseEntity<?> sync(
            @RequestParam(required = false) Integer days,
            @RequestAttribute(required = false) Long factoryId) {
        if (!hrmsClient.isEnabled(factoryId)) {
            return unavailable();
        }
        try {
            int window = days == null ? DEFAULT_SYNC_DAYS : Math.min(Math.max(days, 1), MAX_SYNC_DAYS);
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(window - 1L);
            AttendanceSyncResult result = attendanceSyncService.syncRange(factoryId, from, to);
            return ResponseEntity.ok(result);
        } catch (ResourceAccessException e) {
            return unreachable();
        } catch (HttpClientErrorException.Unauthorized e) {
            return rejected();
        }
    }

    /**
     * Local attendance history (independent of HRMS's own retention window).
     * Supply an explicit {@code from}/{@code to} ISO date range (used by the Monthly view),
     * or a rolling {@code days} window, or an {@code employeeId} for one person's full history.
     * SUPER_ADMIN sees every factory's history; anyone else sees only their own - this was
     * previously unfiltered, and the employeeId path had no ownership check at all.
     */
    @RequiresActivity("ATTENDANCE")
    @GetMapping("/history")
    public ResponseEntity<List<AttendanceRecord>> history(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        boolean superAdmin = Boolean.TRUE.equals(isSuperAdmin);
        if (employeeId != null) {
            List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeIdOrderByWorkDateDesc(employeeId);
            if (!superAdmin && factoryId != null) {
                records = records.stream().filter(r -> factoryId.equals(r.getFactoryId())).toList();
            }
            return ResponseEntity.ok(records);
        }
        if (from != null && to != null) {
            List<AttendanceRecord> records = superAdmin
                    ? attendanceRecordRepository.findByWorkDateBetweenOrderByWorkDateDescEmployeeNameAsc(LocalDate.parse(from), LocalDate.parse(to))
                    : attendanceRecordRepository.findByFactoryIdAndWorkDateBetweenOrderByWorkDateDescEmployeeNameAsc(factoryId, LocalDate.parse(from), LocalDate.parse(to));
            return ResponseEntity.ok(records);
        }
        int window = days == null ? 30 : Math.min(Math.max(days, 1), MAX_SYNC_DAYS);
        LocalDate rangeTo = LocalDate.now();
        LocalDate rangeFrom = rangeTo.minusDays(window - 1L);
        List<AttendanceRecord> records = superAdmin
                ? attendanceRecordRepository.findByWorkDateBetweenOrderByWorkDateDescEmployeeNameAsc(rangeFrom, rangeTo)
                : attendanceRecordRepository.findByFactoryIdAndWorkDateBetweenOrderByWorkDateDescEmployeeNameAsc(factoryId, rangeFrom, rangeTo);
        return ResponseEntity.ok(records);
    }

    private ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("code", ErrorCodes.HRMS_NOT_CONFIGURED, "error", ErrorCodes.fallback(ErrorCodes.HRMS_NOT_CONFIGURED)));
    }

    /** The HRMS portal itself is unreachable right now (DNS/network/timeout) - not a config problem. */
    private ResponseEntity<Map<String, String>> unreachable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("code", ErrorCodes.HRMS_UNREACHABLE, "error", ErrorCodes.fallback(ErrorCodes.HRMS_UNREACHABLE)));
    }

    /** HRMS answered but refused the login. Without this the raw Spring exception text
     * ("401 Unauthorized: [no body]") reached the UI, which tells a factory user nothing. */
    private ResponseEntity<Map<String, String>> rejected() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("code", ErrorCodes.HRMS_REJECTED, "error", ErrorCodes.fallback(ErrorCodes.HRMS_REJECTED)));
    }
}
