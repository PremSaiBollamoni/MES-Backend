package com.antigravity.mes.service;

import com.antigravity.mes.dto.AllocationResponse;
import com.antigravity.mes.dto.RosterEntryResponse;
import com.antigravity.mes.exception.ConflictException;
import com.antigravity.mes.exception.NotFoundException;
import com.antigravity.mes.hrms.AttendanceAvailabilityService;
import com.antigravity.mes.model.Employee;
import com.antigravity.mes.model.OperatorAllocation;
import com.antigravity.mes.model.Station;
import com.antigravity.mes.model.TrackingSession;
import com.antigravity.mes.repository.AttendanceRecordRepository;
import com.antigravity.mes.repository.EmployeeInfoRepository;
import com.antigravity.mes.repository.EmployeeRepository;
import com.antigravity.mes.repository.OperatorAllocationRepository;
import com.antigravity.mes.repository.StationRepository;
import com.antigravity.mes.repository.TrackingSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Which operator is assigned to which station, per shift/day - owned by the Production Supervisor. */
@Service
public class OperatorAllocationService {

    @Autowired
    private OperatorAllocationRepository allocationRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeInfoRepository employeeInfoRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private TrackingSessionRepository trackingSessionRepository;

    @Autowired
    private AttendanceAvailabilityService attendanceAvailabilityService;

    /** SUPER_ADMIN sees every factory's allocations; anyone else sees only their own -
     * this was previously unfiltered. Filtered via each allocation's station's factory. */
    public List<AllocationResponse> listForDate(LocalDate date, Long factoryId, boolean isSuperAdmin) {
        return allocationRepository.findByShiftDate(date).stream()
                .filter(a -> isSuperAdmin || factoryId == null || factoryId.equals(a.getStation().getFactoryId()))
                .map(this::toResponse)
                .toList();
    }

    /** Every OPERATOR who showed up on {@code date}, with their current allocation (if any) and
     * whether a live tracking session is running for them right now - the Supervisor's daily
     * board. SUPER_ADMIN sees every factory's roster; anyone else sees only their own - this
     * was previously unfiltered (every factory's operators, mixed together). */
    public List<RosterEntryResponse> rosterForDate(LocalDate date, Long factoryId, boolean isSuperAdmin) {
        attendanceAvailabilityService.ensureSyncedForDate(factoryId, date);

        Map<Long, OperatorAllocation> allocationByEmployee = new HashMap<>();
        for (OperatorAllocation allocation : allocationRepository.findByShiftDate(date)) {
            if (isSuperAdmin || factoryId == null || factoryId.equals(allocation.getStation().getFactoryId())) {
                allocationByEmployee.put(allocation.getEmployee().getId(), allocation);
            }
        }

        List<Employee> employees = isSuperAdmin
                ? employeeRepository.findAll()
                : employeeRepository.findByFactoryId(factoryId);
        return employees.stream()
                .filter(Employee::isActive)
                .filter(e -> e.hasActiveRoleNamed("OPERATOR"))
                .filter(e -> attendanceRecordRepository.findByEmployeeIdAndWorkDate(e.getId(), date).isPresent())
                .map(employee -> {
                    String employeeCode = employeeInfoRepository.findByEmployeeId(employee.getId())
                            .map(info -> info.getEmployeeCode())
                            .orElse(null);
                    OperatorAllocation allocation = allocationByEmployee.get(employee.getId());
                    Long activeSessionId = trackingSessionRepository
                            .findActiveForEmployee(employee.getId(), TrackingSession.Status.ACTIVE)
                            .map(TrackingSession::getId)
                            .orElse(null);

                    if (allocation == null) {
                        return new RosterEntryResponse(employee.getId(), employee.getName(), employeeCode,
                                null, null, null, null, activeSessionId);
                    }
                    Station station = allocation.getStation();
                    return new RosterEntryResponse(employee.getId(), employee.getName(), employeeCode,
                            allocation.getId(), station.getId(), station.getStationNumber(), station.getOperationName(),
                            activeSessionId);
                })
                .toList();
    }

    /** Assigns (or re-assigns) one employee to one station for a shift/day. Replaces any existing
     * allocation for that employee on that shift/day - an operator can only be in one place at once. */
    @Transactional
    public AllocationResponse assign(Long employeeId, Long stationId, LocalDate shiftDate, String shift, Long factoryId, boolean isSuperAdmin) {
        String resolvedShift = (shift == null || shift.isBlank()) ? "GENERAL" : shift.trim();

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee not found: " + employeeId));
        if (!isSuperAdmin && factoryId != null && !factoryId.equals(employee.getFactoryId())) {
            throw new NotFoundException("Employee not found: " + employeeId);
        }
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new NotFoundException("Station not found: " + stationId));
        if (!isSuperAdmin && factoryId != null && !factoryId.equals(station.getFactoryId())) {
            throw new NotFoundException("Station not found: " + stationId);
        }

        OperatorAllocation allocation = allocationRepository
                .findByEmployeeIdAndShiftDateAndShift(employeeId, shiftDate, resolvedShift)
                .orElseGet(OperatorAllocation::new);
        allocation.setEmployee(employee);
        allocation.setStation(station);
        allocation.setShiftDate(shiftDate);
        allocation.setShift(resolvedShift);

        try {
            OperatorAllocation saved = allocationRepository.save(allocation);
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("This operator is already allocated for that shift/day.");
        }
    }

    @Transactional
    public void unassign(Long id, Long factoryId, boolean isSuperAdmin) {
        OperatorAllocation allocation = allocationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Allocation not found: " + id));
        if (!isSuperAdmin && factoryId != null && !factoryId.equals(allocation.getStation().getFactoryId())) {
            throw new NotFoundException("Allocation not found: " + id);
        }
        allocationRepository.deleteById(id);
    }

    private AllocationResponse toResponse(OperatorAllocation allocation) {
        String employeeCode = employeeInfoRepository.findByEmployeeId(allocation.getEmployee().getId())
                .map(info -> info.getEmployeeCode())
                .orElse(null);
        return new AllocationResponse(
                allocation.getId(),
                allocation.getEmployee().getId(),
                allocation.getEmployee().getName(),
                employeeCode,
                allocation.getStation().getId(),
                allocation.getStation().getStationNumber(),
                allocation.getStation().getOperationName(),
                allocation.getShiftDate(),
                allocation.getShift()
        );
    }
}
