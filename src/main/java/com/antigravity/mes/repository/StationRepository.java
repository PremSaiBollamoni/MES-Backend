package com.antigravity.mes.repository;

import com.antigravity.mes.model.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StationRepository extends JpaRepository<Station, Long> {
    Optional<Station> findByStationNumber(String stationNumber);
    List<Station> findAllByOrderByStationNumberAsc();
}
