package com.antigravity.mes.service;

import com.antigravity.mes.dto.StockPositionResponse;
import com.antigravity.mes.model.DailyStockPosition;
import com.antigravity.mes.model.StockZone;
import com.antigravity.mes.repository.DailyStockPositionRepository;
import com.antigravity.mes.repository.StockZoneRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Daily Stock Position Board (PALMS §6/§7/§21) - owned by the Production Supervisor
 * (entry) with Factory Manager visibility. Every zone follows the same
 * Opening + Additions - Dispatched = Closing formula. */
@Service
public class StockPositionService {

    @Autowired
    private StockZoneRepository stockZoneRepository;

    @Autowired
    private DailyStockPositionRepository positionRepository;

    public List<StockPositionResponse> listForDate(LocalDate date) {
        return stockZoneRepository.findAllByOrderBySequenceNoAsc().stream()
                .map(zone -> toResponseForDate(zone, date))
                .toList();
    }

    @Transactional
    public StockPositionResponse update(String zoneCode, LocalDate date, Integer openingBalanceOverride, int additions, int dispatched, Long enteredByEmployeeId) {
        StockZone zone = stockZoneRepository.findById(zoneCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown zone: " + zoneCode));

        DailyStockPosition record = positionRepository.findByZoneZoneCodeAndPositionDate(zoneCode, date)
                .orElseGet(DailyStockPosition::new);
        record.setZone(zone);
        record.setPositionDate(date);
        record.setOpeningBalance(openingBalanceOverride != null ? openingBalanceOverride : carryForwardOpening(zoneCode, date));
        record.setAdditions(additions);
        record.setDispatched(dispatched);
        record.setEnteredByEmployeeId(enteredByEmployeeId);
        DailyStockPosition saved = positionRepository.save(record);

        return toResponse(zone, saved.getPositionDate(), saved.getOpeningBalance(), saved.getAdditions(), saved.getDispatched());
    }

    private StockPositionResponse toResponseForDate(StockZone zone, LocalDate date) {
        return positionRepository.findByZoneZoneCodeAndPositionDate(zone.getZoneCode(), date)
                .map(record -> toResponse(zone, date, record.getOpeningBalance(), record.getAdditions(), record.getDispatched()))
                .orElseGet(() -> toResponse(zone, date, carryForwardOpening(zone.getZoneCode(), date), 0, 0));
    }

    /** A day with no entry yet starts from yesterday's closing balance, not zero - stock doesn't
     * reset overnight (PALMS §6 is a running ledger, RTM explicitly says so in §19 but it holds
     * for every zone in practice). */
    private int carryForwardOpening(String zoneCode, LocalDate date) {
        return positionRepository.findByZoneZoneCodeAndPositionDate(zoneCode, date.minusDays(1))
                .map(DailyStockPosition::getClosingBalance)
                .orElse(0);
    }

    private StockPositionResponse toResponse(StockZone zone, LocalDate date, int opening, int additions, int dispatched) {
        int closing = opening + additions - dispatched;
        boolean alert = zone.getMaxAlertPieces() != null && closing > zone.getMaxAlertPieces();
        return new StockPositionResponse(zone.getZoneCode(), zone.getZoneName(), date, opening, additions, dispatched, closing, zone.getMaxAlertPieces(), alert);
    }
}
