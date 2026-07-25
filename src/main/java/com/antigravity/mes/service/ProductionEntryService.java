package com.antigravity.mes.service;

import com.antigravity.mes.dto.ProductionEntryResponse;
import com.antigravity.mes.model.Employee;
import com.antigravity.mes.model.ProductionEntry;
import com.antigravity.mes.model.Station;
import com.antigravity.mes.repository.EmployeeRepository;
import com.antigravity.mes.repository.ProductionEntryRepository;
import com.antigravity.mes.repository.StationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Piece counts entered by the Production Supervisor on behalf of each operator
 * (PALMS §10/§11) - operators only ever read their own numbers back, via a
 * separately-gated endpoint, never enter them.
 */
@Service
public class ProductionEntryService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private ProductionEntryRepository productionEntryRepository;

    public List<ProductionEntryResponse> listForDate(LocalDate date) {
        return productionEntryRepository.findByShiftDate(date).stream().map(this::toResponse).toList();
    }

    /** An operator's own entries over a rolling window - self-scoped, no other employee's data ever included. */
    public List<ProductionEntryResponse> listForEmployee(Long employeeId, int days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(Math.max(days - 1, 0));
        return productionEntryRepository.findByEmployeeIdAndShiftDateBetweenOrderByShiftDateDesc(employeeId, from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductionEntryResponse logEntry(Long employeeId, Long stationId, LocalDate shiftDate, int piecesProduced, double timeOnMachineMinutes, Long enteredByEmployeeId) {
        if (piecesProduced < 0) {
            throw new IllegalArgumentException("Pieces produced cannot be negative");
        }
        if (timeOnMachineMinutes <= 0) {
            throw new IllegalArgumentException("Time on machine must be greater than zero");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException("Station not found: " + stationId));

        double samEarned = piecesProduced * station.getSam();
        double efficiencyPct = (samEarned / timeOnMachineMinutes) * 100;

        // Always a new row, never an upsert - an operator can legitimately work the same
        // station more than once in a day (e.g. two separate batches), and each is its own
        // log entry. Collapsing them into one row per employee+station+day used to silently
        // overwrite everything but the last visit.
        ProductionEntry entry = new ProductionEntry();
        entry.setEmployee(employee);
        entry.setStation(station);
        entry.setShiftDate(shiftDate);
        entry.setPiecesProduced(piecesProduced);
        entry.setTimeOnMachineMinutes(timeOnMachineMinutes);
        entry.setSamEarned(samEarned);
        entry.setEfficiencyPct(efficiencyPct);
        entry.setEnteredByEmployeeId(enteredByEmployeeId);

        return toResponse(productionEntryRepository.save(entry));
    }

    /** PALMS §11 efficiency classification. */
    private String classify(double efficiencyPct) {
        if (efficiencyPct < 50) return "CRITICAL";
        if (efficiencyPct < 85) return "LOW";
        if (efficiencyPct < 100) return "MARGINAL";
        if (efficiencyPct < 150) return "GOOD";
        return "EXCELLENT";
    }

    private ProductionEntryResponse toResponse(ProductionEntry entry) {
        return new ProductionEntryResponse(
                entry.getId(),
                entry.getEmployee().getId(),
                entry.getEmployee().getName(),
                entry.getStation().getId(),
                entry.getStation().getStationNumber(),
                entry.getStation().getOperationName(),
                entry.getShiftDate(),
                entry.getPiecesProduced(),
                entry.getTimeOnMachineMinutes(),
                entry.getSamEarned(),
                // Multi-operator stations (e.g. Fusing, 2 operators) share one collective output -
                // each operator's entry carries the full team total, so wages are split per-operator here.
                (entry.getPiecesProduced() * entry.getStation().getRatePerPiece()) / entry.getStation().getOperatorCount(),
                entry.getEfficiencyPct(),
                classify(entry.getEfficiencyPct())
        );
    }
}
