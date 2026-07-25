package com.antigravity.mes.repository;

import com.antigravity.mes.model.DailyStockPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyStockPositionRepository extends JpaRepository<DailyStockPosition, Long> {
    List<DailyStockPosition> findByPositionDate(LocalDate positionDate);
    Optional<DailyStockPosition> findByZoneZoneCodeAndPositionDate(String zoneCode, LocalDate positionDate);
    List<DailyStockPosition> findByZoneZoneCodeAndPositionDateBetweenOrderByPositionDateAsc(String zoneCode, LocalDate from, LocalDate to);
}
