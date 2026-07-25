package com.antigravity.mes.service;

import com.antigravity.mes.dto.StationStatusResponse;
import com.antigravity.mes.hrms.AttendanceAvailabilityService;
import com.antigravity.mes.model.OperatorAllocation;
import com.antigravity.mes.model.Station;
import com.antigravity.mes.repository.AttendanceRecordRepository;
import com.antigravity.mes.repository.OperatorAllocationRepository;
import com.antigravity.mes.repository.StationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives PALMS §12's PRESENT/ABSENT-at-a-station view - not a table of its own, just a
 * live join of "who's allocated where" (Phase 2) against "who actually showed up"
 * (the existing HRMS-backed attendance_records). A station with an absent allocated
 * operator is Unmanned; if that station also happens to be the current bottleneck
 * (highest SAM, per PALMS §9), it's flagged critical.
 */
@Service
public class StationStatusService {

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private OperatorAllocationRepository allocationRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private AttendanceAvailabilityService attendanceAvailabilityService;

    public List<StationStatusResponse> getStatusForDate(LocalDate date) {
        attendanceAvailabilityService.ensureSyncedForDate(date);

        List<Station> activeStations = stationRepository.findAllByOrderByStationNumberAsc().stream()
                .filter(Station::isActive)
                .toList();

        Long bottleneckStationId = activeStations.stream()
                .max((a, b) -> Double.compare(a.getSam(), b.getSam()))
                .map(Station::getId)
                .orElse(null);

        Map<Long, OperatorAllocation> allocationByStation = new HashMap<>();
        for (OperatorAllocation allocation : allocationRepository.findByShiftDate(date)) {
            allocationByStation.put(allocation.getStation().getId(), allocation);
        }

        return activeStations.stream().map(station -> {
            OperatorAllocation allocation = allocationByStation.get(station.getId());
            if (allocation == null) {
                return new StationStatusResponse(
                        station.getId(), station.getStationNumber(), station.getOperationName(),
                        station.getZone().name(), station.getSam(), null, null, "UNALLOCATED", false);
            }

            Long employeeId = allocation.getEmployee().getId();
            boolean present = attendanceRecordRepository.findByEmployeeIdAndWorkDate(employeeId, date).isPresent();
            String status = present ? "MANNED" : "UNMANNED";
            boolean critical = !present && station.getId().equals(bottleneckStationId);

            return new StationStatusResponse(
                    station.getId(), station.getStationNumber(), station.getOperationName(),
                    station.getZone().name(), station.getSam(),
                    employeeId, allocation.getEmployee().getName(), status, critical);
        }).toList();
    }
}
