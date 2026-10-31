package com.antigravity.mes.service;

import com.antigravity.mes.dto.FactoryKpiSummary;
import com.antigravity.mes.hrms.HrmsClient;
import com.antigravity.mes.hrms.dto.HrmsDailyAttendance;
import com.antigravity.mes.model.AttendanceRecord;
import com.antigravity.mes.model.Employee;
import com.antigravity.mes.model.Factory;
import com.antigravity.mes.model.ProductionEntry;
import com.antigravity.mes.repository.AttendanceRecordRepository;
import com.antigravity.mes.repository.EmployeeRepository;
import com.antigravity.mes.repository.FactoryRepository;
import com.antigravity.mes.repository.ProductionEntryRepository;
import com.antigravity.mes.repository.StationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Cross-factory KPI overview for SUPER_ADMIN - workforce, today's production, today's
 * attendance, and month-to-date payroll, one row per factory so they can be compared
 * side by side. Every query here is deliberately factory-scoped (never findAll()
 * unfiltered) - this is exactly the class of endpoint that leaked cross-factory data
 * before the Employees list was fixed, so it's built factory-scoped from the start.
 */
@Service
public class FactoryKpiService {

    private static final Logger log = LoggerFactory.getLogger(FactoryKpiService.class);

    @Autowired
    private FactoryRepository factoryRepository;

    @Autowired
    private HrmsClient hrmsClient;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private ProductionEntryRepository productionEntryRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Transactional(readOnly = true)
    public List<FactoryKpiSummary> getAllFactoryKpis() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        return factoryRepository.findAll().stream()
                .map(factory -> buildSummary(factory, today, monthStart))
                .toList();
    }

    private FactoryKpiSummary buildSummary(Factory factory, LocalDate today, LocalDate monthStart) {
        Long factoryId = factory.getFactoryId();

        // SUPER_ADMIN's own employee row carries a placeholder factory_id (see
        // EmployeeManagementService) and must never be counted as this factory's staff.
        List<Employee> employees = employeeRepository.findByFactoryId(factoryId).stream()
                .filter(e -> !e.hasActiveRoleNamed("SUPER_ADMIN"))
                .toList();
        long total = employees.size();
        long active = employees.stream().filter(Employee::isActive).count();
        long inactive = total - active;

        long stationCount = stationRepository.countByFactoryId(factoryId);

        List<ProductionEntry> todayEntries = productionEntryRepository.findByFactoryIdAndShiftDate(factoryId, today);
        int todayPieces = todayEntries.stream().mapToInt(ProductionEntry::getPiecesProduced).sum();

        // Prefer live HRMS data over the locally-synced table: attendance_records is only
        // ever populated when someone explicitly hits the Attendance screen's sync action,
        // so on a day nobody has synced yet it silently shows 0/0 even though HRMS has the
        // real numbers right now. Falls back to the local table (and, failing that, 0/0)
        // for a factory with no HRMS, or if the live call fails for any reason - this KPI
        // endpoint must never break just because HRMS had a hiccup.
        //
        // The denominator is always this factory's active employee count, never the size
        // of the attendance list itself - HRMS (and our local mirror of it) only returns
        // one row per employee who actually had a clock event today, not a full roster
        // with explicit "absent" rows for everyone who didn't. Using the list's own size
        // as the total silently reports 100% whenever nobody happens to be marked ABSENT,
        // even if most of the workforce never showed up in the response at all.
        long presentCount;
        long attendanceTotal = active;
        List<HrmsDailyAttendance> liveAttendance = tryFetchLiveAttendance(factory, today);
        if (liveAttendance != null) {
            presentCount = liveAttendance.stream()
                    .filter(a -> a.status() != null && !a.status().equalsIgnoreCase("ABSENT"))
                    .count();
        } else {
            List<AttendanceRecord> todayAttendance = attendanceRecordRepository.findByFactoryIdAndWorkDate(factoryId, today);
            presentCount = todayAttendance.stream()
                    .filter(a -> a.getStatus() != null && !a.getStatus().equalsIgnoreCase("ABSENT"))
                    .count();
        }
        presentCount = Math.min(presentCount, attendanceTotal);

        // Same earnings formula as PayrollService: pieces * station rate / station
        // operator count, summed across every entry this month so far.
        List<ProductionEntry> monthEntries = productionEntryRepository.findByFactoryIdAndShiftDateBetween(factoryId, monthStart, today);
        double payroll = monthEntries.stream()
                .mapToDouble(e -> (e.getPiecesProduced() * e.getStation().getRatePerPiece()) / e.getStation().getOperatorCount())
                .sum();

        return new FactoryKpiSummary(
                factoryId,
                factory.getFactoryName(),
                factory.getFactoryCode(),
                factory.isActive(),
                total,
                active,
                inactive,
                stationCount,
                todayPieces,
                todayEntries.size(),
                presentCount,
                attendanceTotal,
                payroll);
    }

    /**
     * Returns today's attendance straight from HRMS if this factory has HRMS enabled
     * and a usable connection resolves for it (this factory's own saved config, or the
     * default env-var connection if it has none), or null to fall back to the local
     * table. A factory with HRMS disabled, or any failure calling out, returns null
     * rather than throwing.
     */
    private List<HrmsDailyAttendance> tryFetchLiveAttendance(Factory factory, LocalDate today) {
        Long factoryId = factory.getFactoryId();
        if (!factory.isHrmsEnabled() || !hrmsClient.isEnabled(factoryId)) {
            return null;
        }
        try {
            return hrmsClient.getDailyAttendance(factoryId, today);
        } catch (Exception e) {
            log.warn("Live HRMS attendance fetch failed for factory {} - falling back to locally synced data: {}",
                    factory.getFactoryId(), e.getMessage());
            return null;
        }
    }
}
