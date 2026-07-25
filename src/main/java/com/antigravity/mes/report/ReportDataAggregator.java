package com.antigravity.mes.report;

import com.antigravity.mes.dto.StationStatusResponse;
import com.antigravity.mes.dto.StockAvailabilityResponse;
import com.antigravity.mes.dto.StockPositionResponse;
import com.antigravity.mes.dto.StationWipSummary;
import com.antigravity.mes.model.AttendanceRecord;
import com.antigravity.mes.model.OperatorAllocation;
import com.antigravity.mes.model.ProductionEntry;
import com.antigravity.mes.model.Station;
import com.antigravity.mes.repository.AttendanceRecordRepository;
import com.antigravity.mes.repository.OperatorAllocationRepository;
import com.antigravity.mes.repository.ProductionEntryRepository;
import com.antigravity.mes.repository.StationRepository;
import com.antigravity.mes.service.BundleTrackingService;
import com.antigravity.mes.service.StationStatusService;
import com.antigravity.mes.service.StockAvailabilityService;
import com.antigravity.mes.service.StockPositionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pulls every real, currently-tracked production metric for one calendar day into a single
 * structured map. This is the ONLY source of numbers the AI report generator is allowed to use -
 * it never invents a statistic; it only narrates what's in here. Sections PALMS doesn't actually
 * track yet (shift-wise breakdown, hourly trends, quality/rework) are deliberately absent - the
 * prompt is told to omit them rather than iterate over an empty/fake structure.
 */
@Service
public class ReportDataAggregator {

    @Autowired private ProductionEntryRepository productionEntryRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private OperatorAllocationRepository operatorAllocationRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private StationStatusService stationStatusService;
    @Autowired private StockAvailabilityService stockAvailabilityService;
    @Autowired private StockPositionService stockPositionService;
    @Autowired private BundleTrackingService bundleTrackingService;

    public Map<String, Object> aggregateProduction(LocalDate date) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportDate", date.toString());
        data.put("dataNotTracked", List.of(
                "Shift-wise Performance (no shift field beyond a default value)",
                "Hourly Production Trends (no hourly granularity is recorded, only per-entry totals)",
                "Quality & Rework Statistics (not tracked in this system at all)"
        ));

        List<ProductionEntry> entries = productionEntryRepository.findByShiftDate(date);
        List<AttendanceRecord> attendance = attendanceRecordRepository
                .findByWorkDateBetweenOrderByWorkDateDescEmployeeNameAsc(date, date);
        List<OperatorAllocation> allocations = operatorAllocationRepository.findByShiftDate(date);
        List<Station> activeStations = stationRepository.findAllByOrderByStationNumberAsc().stream()
                .filter(Station::isActive).toList();

        data.put("attendance", attendanceSection(attendance));
        data.put("productionSummary", productionSummary(entries));
        data.put("productionEntries", entryList(entries));
        data.put("stationWise", stationWise(entries, activeStations));
        data.put("employeePerformance", employeePerformance(entries));
        data.put("stationManning", stationManning(date));
        data.put("bottleneck", bottleneck(activeStations, entries));
        data.put("allocation", Map.of(
                "presentOperators", attendance.size(),
                "allocatedOperators", allocations.size(),
                "unallocatedPresentOperators", Math.max(0, attendance.size() - allocations.size())
        ));
        data.put("stockAvailability", stockAvailability(date));
        data.put("dailyStockPosition", stockPosition(date));
        data.put("wipSnapshot", wipSnapshot());
        data.put("riskIndicators", riskIndicators(entries, attendance, date));
        return data;
    }

    /** Station-wise stock: every station's current stock status (not just the shortages), plus
     * the zone-level daily stock position and the live WIP snapshot. */
    public Map<String, Object> aggregateStock(LocalDate date) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportDate", date.toString());
        data.put("dataNotTracked", List.of(
                "Raw material supplier / purchase-order data (not tracked in this system)",
                "Multi-day stock trend history (only the selected date's position is available)"
        ));

        List<Station> activeStations = stationRepository.findAllByOrderByStationNumberAsc().stream()
                .filter(Station::isActive).toList();

        data.put("stationWiseStock", allStationStock(date, activeStations));
        data.put("stockShortages", stockAvailability(date));
        data.put("dailyStockPosition", stockPosition(date));
        data.put("wipSnapshot", wipSnapshot());
        data.put("riskIndicators", stockRiskIndicators(date));
        return data;
    }

    /** Full attendance register for the date: per-employee check-in/out and lateness, plus the
     * present-vs-allocated operator gap. */
    public Map<String, Object> aggregateAttendance(LocalDate date) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportDate", date.toString());
        data.put("dataNotTracked", List.of(
                "Leave/absence reason codes (not tracked in this system, only presence/lateness)",
                "Multi-day attendance trend history (only the selected date's register is available)"
        ));

        List<AttendanceRecord> attendance = attendanceRecordRepository
                .findByWorkDateBetweenOrderByWorkDateDescEmployeeNameAsc(date, date);
        List<OperatorAllocation> allocations = operatorAllocationRepository.findByShiftDate(date);
        List<ProductionEntry> entries = productionEntryRepository.findByShiftDate(date);

        data.put("attendanceSummary", attendanceSection(attendance));
        data.put("attendanceRegister", attendanceRegister(attendance));
        data.put("allocation", Map.of(
                "presentOperators", attendance.size(),
                "allocatedOperators", allocations.size(),
                "unallocatedPresentOperators", Math.max(0, attendance.size() - allocations.size())
        ));
        data.put("stationManning", stationManning(date));
        data.put("riskIndicators", riskIndicators(entries, attendance, date));
        return data;
    }

    /** Full worker performance ranking (not just top/bottom 5), with attendance correlation. */
    public Map<String, Object> aggregateWorkerPerformance(LocalDate date) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportDate", date.toString());
        data.put("dataNotTracked", List.of(
                "Quality & Rework Statistics per worker (not tracked in this system)",
                "Multi-day performance trend history (only the selected date's entries are available)"
        ));

        List<ProductionEntry> entries = productionEntryRepository.findByShiftDate(date);
        List<AttendanceRecord> attendance = attendanceRecordRepository
                .findByWorkDateBetweenOrderByWorkDateDescEmployeeNameAsc(date, date);

        data.put("fullEmployeeRanking", fullEmployeeRanking(entries));
        data.put("employeePerformance", employeePerformance(entries));
        data.put("productionEntries", entryList(entries));
        data.put("attendanceSummary", attendanceSection(attendance));
        data.put("riskIndicators", riskIndicators(entries, attendance, date));
        return data;
    }

    private Map<String, Object> attendanceSection(List<AttendanceRecord> attendance) {
        long lateCount = attendance.stream().filter(AttendanceRecord::isLate).count();
        return Map.of(
                "totalPresent", attendance.size(),
                "totalLate", lateCount,
                "onTimePercent", attendance.isEmpty() ? 0 : round((attendance.size() - lateCount) * 100.0 / attendance.size())
        );
    }

    private Map<String, Object> productionSummary(List<ProductionEntry> entries) {
        int totalPieces = entries.stream().mapToInt(ProductionEntry::getPiecesProduced).sum();
        double totalWages = entries.stream()
                .mapToDouble(e -> (e.getPiecesProduced() * e.getStation().getRatePerPiece()) / e.getStation().getOperatorCount())
                .sum();
        double avgEfficiency = entries.stream().mapToDouble(ProductionEntry::getEfficiencyPct).average().orElse(0);
        Map<String, Long> classificationCounts = entries.stream()
                .collect(Collectors.groupingBy(this::classify, Collectors.counting()));
        return Map.of(
                "totalEntries", entries.size(),
                "totalPiecesProduced", totalPieces,
                "totalWagesEarned", round(totalWages),
                "averageEfficiencyPct", round(avgEfficiency),
                "classificationDistribution", classificationCounts
        );
    }

    private List<Map<String, Object>> entryList(List<ProductionEntry> entries) {
        return entries.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeName", e.getEmployee().getName());
            m.put("stationNumber", e.getStation().getStationNumber());
            m.put("operationName", e.getStation().getOperationName());
            m.put("piecesProduced", e.getPiecesProduced());
            m.put("timeOnMachineMinutes", round(e.getTimeOnMachineMinutes()));
            m.put("efficiencyPct", round(e.getEfficiencyPct()));
            m.put("classification", classify(e));
            m.put("wagesEarned", round((e.getPiecesProduced() * e.getStation().getRatePerPiece()) / e.getStation().getOperatorCount()));
            return m;
        }).toList();
    }

    private List<Map<String, Object>> stationWise(List<ProductionEntry> entries, List<Station> activeStations) {
        Map<Long, List<ProductionEntry>> byStation = entries.stream()
                .collect(Collectors.groupingBy(e -> e.getStation().getId()));
        return activeStations.stream().map(station -> {
            List<ProductionEntry> stationEntries = byStation.getOrDefault(station.getId(), List.of());
            int totalPieces = stationEntries.stream().mapToInt(ProductionEntry::getPiecesProduced).sum();
            double avgEff = stationEntries.stream().mapToDouble(ProductionEntry::getEfficiencyPct).average().orElse(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stationNumber", station.getStationNumber());
            m.put("operationName", station.getOperationName());
            m.put("zone", station.getZone().name());
            m.put("standardSam", station.getSam());
            m.put("targetPiecesPerDay", station.getTargetPiecesPerDay());
            m.put("entriesLogged", stationEntries.size());
            m.put("totalPiecesProduced", totalPieces);
            m.put("targetVsActualPct", station.getTargetPiecesPerDay() == 0 ? null : round(totalPieces * 100.0 / station.getTargetPiecesPerDay()));
            m.put("averageEfficiencyPct", round(avgEff));
            return m;
        }).toList();
    }

    private Map<String, Object> employeePerformance(List<ProductionEntry> entries) {
        Map<String, List<ProductionEntry>> byEmployee = entries.stream()
                .collect(Collectors.groupingBy(e -> e.getEmployee().getName()));
        List<Map<String, Object>> ranked = byEmployee.entrySet().stream().map(en -> {
            double avgEff = en.getValue().stream().mapToDouble(ProductionEntry::getEfficiencyPct).average().orElse(0);
            int totalPieces = en.getValue().stream().mapToInt(ProductionEntry::getPiecesProduced).sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeName", en.getKey());
            m.put("entriesLogged", en.getValue().size());
            m.put("totalPiecesProduced", totalPieces);
            m.put("averageEfficiencyPct", round(avgEff));
            return m;
        }).sorted((a, b) -> Double.compare((double) b.get("averageEfficiencyPct"), (double) a.get("averageEfficiencyPct"))).toList();

        int topN = Math.min(5, ranked.size());
        return Map.of(
                "topPerformers", ranked.subList(0, topN),
                "lowestPerformers", ranked.subList(Math.max(0, ranked.size() - topN), ranked.size())
        );
    }

    /** Every employee with production entries today, ranked by average efficiency (not just top/bottom 5). */
    private List<Map<String, Object>> fullEmployeeRanking(List<ProductionEntry> entries) {
        Map<String, List<ProductionEntry>> byEmployee = entries.stream()
                .collect(Collectors.groupingBy(e -> e.getEmployee().getName()));
        return byEmployee.entrySet().stream().map(en -> {
            double avgEff = en.getValue().stream().mapToDouble(ProductionEntry::getEfficiencyPct).average().orElse(0);
            int totalPieces = en.getValue().stream().mapToInt(ProductionEntry::getPiecesProduced).sum();
            double totalWages = en.getValue().stream()
                    .mapToDouble(e -> (e.getPiecesProduced() * e.getStation().getRatePerPiece()) / e.getStation().getOperatorCount())
                    .sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeName", en.getKey());
            m.put("entriesLogged", en.getValue().size());
            m.put("totalPiecesProduced", totalPieces);
            m.put("totalWagesEarned", round(totalWages));
            m.put("averageEfficiencyPct", round(avgEff));
            m.put("classification", classify(en.getValue().get(0)));
            return m;
        }).sorted((a, b) -> Double.compare((double) b.get("averageEfficiencyPct"), (double) a.get("averageEfficiencyPct"))).toList();
    }

    private List<Map<String, Object>> attendanceRegister(List<AttendanceRecord> attendance) {
        return attendance.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeName", a.getEmployeeName());
            m.put("employeeCode", a.getEmployeeCode());
            m.put("departmentName", a.getDepartmentName());
            m.put("firstIn", a.getFirstIn());
            m.put("lastOut", a.getLastOut());
            m.put("totalHours", a.getTotalHours());
            m.put("status", a.getStatus());
            m.put("isLate", a.isLate());
            m.put("lateMinutes", a.getLateMinutes());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> allStationStock(LocalDate date, List<Station> activeStations) {
        Map<String, StockAvailabilityResponse> byStationNumber = stockAvailabilityService.listForDate(date).stream()
                .collect(Collectors.toMap(StockAvailabilityResponse::stationNumber, r -> r, (a, b) -> a));
        return activeStations.stream().map(station -> {
            StockAvailabilityResponse r = byStationNumber.get(station.getStationNumber());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stationNumber", station.getStationNumber());
            m.put("operationName", station.getOperationName());
            m.put("zone", station.getZone().name());
            m.put("status", r == null ? "UNKNOWN" : r.status());
            m.put("currentStockQty", r == null ? null : r.currentStockQty());
            m.put("minStockThreshold", r == null ? null : r.minStockThreshold());
            return m;
        }).toList();
    }

    private List<String> stockRiskIndicators(LocalDate date) {
        List<String> risks = new ArrayList<>();
        long noStock = stockAvailabilityService.listForDate(date).stream().filter(r -> "NO_STOCK".equals(r.status())).count();
        if (noStock > 0) risks.add(noStock + " station(s) have NO_STOCK today");
        long lowStock = stockAvailabilityService.listForDate(date).stream().filter(r -> "LOW_STOCK".equals(r.status())).count();
        if (lowStock > 0) risks.add(lowStock + " station(s) are at LOW_STOCK today");
        List<StationWipSummary> wip = bundleTrackingService.getWipSummary();
        long alerting = wip.stream().filter(StationWipSummary::alertTriggered).count();
        if (alerting > 0) risks.add(alerting + " station(s) have a WIP bundle-count alert right now");
        return risks;
    }

    private Map<String, Object> stationManning(LocalDate date) {
        List<StationStatusResponse> statuses = stationStatusService.getStatusForDate(date);
        Map<String, Long> counts = statuses.stream()
                .collect(Collectors.groupingBy(StationStatusResponse::status, Collectors.counting()));
        List<String> unmannedCritical = statuses.stream()
                .filter(StationStatusResponse::critical)
                .map(s -> s.stationNumber() + " (" + s.operationName() + ")")
                .toList();
        return Map.of("statusCounts", counts, "criticalUnmannedStations", unmannedCritical);
    }

    private Map<String, Object> bottleneck(List<Station> activeStations, List<ProductionEntry> entries) {
        Optional<Station> designBottleneck = activeStations.stream().max(Comparator.comparingDouble(Station::getSam));
        Map<Long, List<ProductionEntry>> byStation = entries.stream().collect(Collectors.groupingBy(e -> e.getStation().getId()));
        Optional<Map.Entry<Long, List<ProductionEntry>>> weakestByEfficiency = byStation.entrySet().stream()
                .min(Comparator.comparingDouble(en -> en.getValue().stream().mapToDouble(ProductionEntry::getEfficiencyPct).average().orElse(100)));

        Map<String, Object> result = new LinkedHashMap<>();
        designBottleneck.ifPresent(s -> result.put("designBottleneckStation", s.getStationNumber() + " (" + s.getOperationName() + ", SAM " + s.getSam() + ")"));
        weakestByEfficiency.ifPresent(en -> {
            Station s = en.getValue().get(0).getStation();
            double avgEff = en.getValue().stream().mapToDouble(ProductionEntry::getEfficiencyPct).average().orElse(0);
            result.put("todaysWeakestPerformingStation", s.getStationNumber() + " (" + s.getOperationName() + ", avg efficiency " + round(avgEff) + "%)");
        });
        return result;
    }

    private List<Map<String, Object>> stockAvailability(LocalDate date) {
        return stockAvailabilityService.listForDate(date).stream()
                .filter(r -> !"AVAILABLE".equals(r.status()))
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("stationNumber", r.stationNumber());
                    m.put("operationName", r.operationName());
                    m.put("status", r.status());
                    m.put("currentStockQty", r.currentStockQty());
                    m.put("minStockThreshold", r.minStockThreshold());
                    return m;
                }).toList();
    }

    private List<Map<String, Object>> stockPosition(LocalDate date) {
        return stockPositionService.listForDate(date).stream().map((StockPositionResponse r) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("zoneName", r.zoneName());
            m.put("openingBalance", r.openingBalance());
            m.put("additions", r.additions());
            m.put("dispatched", r.dispatched());
            m.put("closingBalance", r.closingBalance());
            m.put("alertTriggered", r.alertTriggered());
            return m;
        }).toList();
    }

    private Map<String, Object> wipSnapshot() {
        List<StationWipSummary> summary = bundleTrackingService.getWipSummary();
        List<Map<String, Object>> alerting = summary.stream()
                .filter(StationWipSummary::alertTriggered)
                .map(s -> Map.<String, Object>of("stationNumber", s.stationNumber(), "operationName", s.operationName(), "bundleCount", s.bundleCount()))
                .toList();
        return Map.of(
                "note", "This is the CURRENT live WIP state at report-generation time, not historical to the report date.",
                "activeBundleCount", bundleTrackingService.listActive().size(),
                "stationsWithWipAlert", alerting
        );
    }

    private List<String> riskIndicators(List<ProductionEntry> entries, List<AttendanceRecord> attendance, LocalDate date) {
        List<String> risks = new ArrayList<>();
        long criticalCount = entries.stream().filter(e -> "CRITICAL".equals(classify(e))).count();
        if (criticalCount > 0) risks.add(criticalCount + " production entr" + (criticalCount == 1 ? "y is" : "ies are") + " at CRITICAL efficiency (<50%)");
        long lowStockCount = stockAvailabilityService.listForDate(date).stream()
                .filter(r -> "NO_STOCK".equals(r.status()) || "LOW_STOCK".equals(r.status())).count();
        if (lowStockCount > 0) risks.add(lowStockCount + " station(s) flagged NO_STOCK or LOW_STOCK today");
        List<StationStatusResponse> statuses = stationStatusService.getStatusForDate(date);
        long criticalUnmanned = statuses.stream().filter(StationStatusResponse::critical).count();
        if (criticalUnmanned > 0) risks.add(criticalUnmanned + " bottleneck station(s) UNMANNED today");
        long unallocatedPresent = Math.max(0, attendance.size() - entries.stream().map(e -> e.getEmployee().getId()).distinct().count());
        if (unallocatedPresent > 0) risks.add(unallocatedPresent + " present operator(s) have no logged production today");
        return risks;
    }

    private String classify(ProductionEntry e) {
        double eff = e.getEfficiencyPct();
        if (eff < 50) return "CRITICAL";
        if (eff < 85) return "LOW";
        if (eff < 100) return "MARGINAL";
        if (eff < 150) return "GOOD";
        return "EXCELLENT";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
