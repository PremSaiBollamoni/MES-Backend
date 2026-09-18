package com.antigravity.mes.repository;

import com.antigravity.mes.model.StockZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockZoneRepository extends JpaRepository<StockZone, String> {
    List<StockZone> findAllByOrderBySequenceNoAsc();
}
