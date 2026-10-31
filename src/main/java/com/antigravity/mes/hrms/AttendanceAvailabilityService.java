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

    private final Set<String> syncInFlight = ConcurrentHashMap.newKeySet();

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private AttendanceSyncService attendanceSyncService;

    @Autowired
    private HrmsClient hrmsClient;

    /** A null factoryId (e.g. a SUPER_ADMIN context with no single factory) is a safe
     * no-op - there's no one factory's HRMS to proactively sync here. */
    public void ensureSyncedForDate(Long factoryId, LocalDate date) {
        if (factoryId == null || !hrmsClient.isEnabled(factoryId)
                || attendanceRecordRepository.existsByFactoryIdAndWorkDate(factoryId, date)) {
            return;
        }
        // Two factories needing the same date synced at the same time must not block each
        // other - keyed by (factory, date), not date alone.
        String key = factoryId + ":" + date;
        if (!syncInFlight.add(key)) {
            return; // Already syncing this factory+date on another request - don't pile up duplicates.
        }
        executor.submit(() -> {
            try {
                attendanceSyncService.syncRange(factoryId, date, date);
            } catch (Exception e) {
                log.warn("Background attendance sync failed for factory {} date {}: {}", factoryId, date, e.getMessage());
            } finally {
                syncInFlight.remove(key);
            }
        });
    }
}
