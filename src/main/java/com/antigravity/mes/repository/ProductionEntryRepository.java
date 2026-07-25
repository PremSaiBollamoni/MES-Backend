package com.antigravity.mes.repository;

import com.antigravity.mes.model.ProductionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProductionEntryRepository extends JpaRepository<ProductionEntry, Long> {
    List<ProductionEntry> findByShiftDate(LocalDate shiftDate);
    List<ProductionEntry> findByEmployeeIdAndShiftDateBetweenOrderByShiftDateDesc(Long employeeId, LocalDate from, LocalDate to);
}
