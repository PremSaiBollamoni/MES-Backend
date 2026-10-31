package com.antigravity.mes.service;

import com.antigravity.mes.dto.StockAvailabilityResponse;
import com.antigravity.mes.model.Station;
import com.antigravity.mes.model.StockAvailability;
import com.antigravity.mes.repository.StationRepository;
import com.antigravity.mes.repository.StockAvailabilityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Auto-derived 4-tier stock check per station (PALMS §13) - the Supervisor logs a raw on-hand
 * quantity and the status is computed from that against the station's min/maxStockThreshold, not
 * chosen directly. Recomputed live on every read so changing a station's thresholds takes effect
 * immediately, without needing to re-log the quantity. */
@Service
public class StockAvailabilityService {

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private StockAvailabilityRepository stockAvailabilityRepository;

    /** SUPER_ADMIN sees every factory's stock; anyone else sees only their own - this was
     * previously unfiltered (findAllByOrderByStationNumberAsc across ALL factories). */
    public List<StockAvailabilityResponse> listForDate(LocalDate date, Long factoryId, boolean isSuperAdmin) {
        List<Station> allStations = isSuperAdmin
                ? stationRepository.findAllByOrderByStationNumberAsc()
                : stationRepository.findByFactoryIdOrderByStationNumberAsc(factoryId);
        List<Station> activeStations = allStations.stream()
                .filter(Station::isActive)
                .toList();

        Map<Long, StockAvailability> byStation = new HashMap<>();
        for (StockAvailability record : stockAvailabilityRepository.findByShiftDate(date)) {
            byStation.put(record.getStation().getId(), record);
        }

        return activeStations.stream().map(station -> {
            StockAvailability record = byStation.get(station.getId());
            if (record == null) {
                return new StockAvailabilityResponse(
                        station.getId(), station.getStationNumber(), station.getOperationName(),
                        station.getMinStockThreshold(), station.getMaxStockThreshold(), 0, "NOT_CHECKED", null, null);
            }
            String status = deriveStatus(station, record.getCurrentStockQty());
            return new StockAvailabilityResponse(
                    station.getId(), station.getStationNumber(), station.getOperationName(),
                    station.getMinStockThreshold(), station.getMaxStockThreshold(),
                    record.getCurrentStockQty(), status, record.getReason(), record.getLoggedAt());
        }).toList();
    }

    @Transactional
    public StockAvailabilityResponse logStock(
            Long stationId, LocalDate shiftDate, int currentStockQty, String reason, Long loggedByEmployeeId,
            Long factoryId, boolean isSuperAdmin) {
        if (currentStockQty < 0) {
            throw new IllegalArgumentException("Current stock quantity cannot be negative");
        }
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException("Station not found: " + stationId));
        if (!isSuperAdmin && factoryId != null && !factoryId.equals(station.getFactoryId())) {
            throw new IllegalArgumentException("Station not found: " + stationId);
        }

        String status = deriveStatus(station, currentStockQty);

        StockAvailability record = stockAvailabilityRepository.findByStationIdAndShiftDate(stationId, shiftDate)
                .orElseGet(StockAvailability::new);
        record.setStation(station);
        record.setShiftDate(shiftDate);
        record.setCurrentStockQty(currentStockQty);
        record.setStatus(StockAvailability.Status.valueOf(status));
        record.setReason(status.equals("NO_STOCK") || status.equals("LOW_STOCK") ? reason : null);
        record.setLoggedByEmployeeId(loggedByEmployeeId);
        record.setLoggedAt(LocalDateTime.now());
        StockAvailability saved = stockAvailabilityRepository.save(record);

        return new StockAvailabilityResponse(
                station.getId(), station.getStationNumber(), station.getOperationName(),
                station.getMinStockThreshold(), station.getMaxStockThreshold(),
                saved.getCurrentStockQty(), status, saved.getReason(), saved.getLoggedAt());
    }

    /** NO_STOCK only at exactly zero; LOW_STOCK is 0 < qty < min; HIGH_STOCK only applies once a
     * ceiling is configured (max > 0) and qty exceeds it; everything else is the normal AVAILABLE band. */
    private String deriveStatus(Station station, int currentStockQty) {
        if (currentStockQty == 0) {
            return "NO_STOCK";
        }
        if (currentStockQty < station.getMinStockThreshold()) {
            return "LOW_STOCK";
        }
        if (station.getMaxStockThreshold() > 0 && currentStockQty > station.getMaxStockThreshold()) {
            return "HIGH_STOCK";
        }
        return "AVAILABLE";
    }
}
