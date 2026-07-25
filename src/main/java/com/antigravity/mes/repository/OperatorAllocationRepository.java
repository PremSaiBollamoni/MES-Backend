package com.antigravity.mes.repository;

import com.antigravity.mes.model.OperatorAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OperatorAllocationRepository extends JpaRepository<OperatorAllocation, Long> {
    List<OperatorAllocation> findByShiftDate(LocalDate shiftDate);
    Optional<OperatorAllocation> findByEmployeeIdAndShiftDateAndShift(Long employeeId, LocalDate shiftDate, String shift);
    List<OperatorAllocation> findByStationIdAndShiftDate(Long stationId, LocalDate shiftDate);
}
