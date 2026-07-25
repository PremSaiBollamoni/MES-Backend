package com.antigravity.mes.repository;

import com.antigravity.mes.model.StockAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockAvailabilityRepository extends JpaRepository<StockAvailability, Long> {
    List<StockAvailability> findByShiftDate(LocalDate shiftDate);
    Optional<StockAvailability> findByStationIdAndShiftDate(Long stationId, LocalDate shiftDate);
}
