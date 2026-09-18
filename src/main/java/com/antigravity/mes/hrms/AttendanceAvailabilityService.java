package com.antigravity.mes.hrms;

import com.antigravity.mes.repository.AttendanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * There's no scheduled attendance sync job - HRMS data only lands in our local
 * attendance_records table when someone explicitly hits the sync endpoint. Left alone, that
 * means every new day the Operator Allocation roster and Station Status board (both derived
 * from that local table, not live HRMS) would show nobody present until someone remembers to
 * sync. This makes them self-healing: the first time a date is needed and nothing's synced for
 * it yet, it kicks off pulling that single day from HRMS in the background.
 *
 * Deliberately fire-and-forget rather than awaited inline - syncing a full day's worth of
 * employees against HRMS can take longer than the roster/status board's own request timeout,
 * so blocking on it would turn a missing sync into a timeout instead of just stale-for-now data.
 * The caller gets whatever's synced so far immediately; a refresh shortly after picks up the rest.
 */
@Service
public class AttendanceAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceAvailabilityService.class);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "attendance-sync");
        t.setDaemon(true);
        return t;
    });

    private final Set<LocalDate> syncInFlight = ConcurrentHashMap.newKeySet();

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private AttendanceSyncService attendanceSyncService;

    @Autowired
    private HrmsClient hrmsClient;

    public void ensureSyncedForDate(LocalDate date) {
        if (!hrmsClient.isEnabled() || attendanceRecordRepository.existsByWorkDate(date)) {
            return;
        }
        if (!syncInFlight.add(date)) {
            return; // Already syncing this date on another request - don't pile up duplicates.
        }
        executor.submit(() -> {
            try {
                attendanceSyncService.syncRange(date, date);
            } catch (Exception e) {
                log.warn("Background attendance sync failed for {}: {}", date, e.getMessage());
            } finally {
                syncInFlight.remove(date);
            }
        });
    }
}
