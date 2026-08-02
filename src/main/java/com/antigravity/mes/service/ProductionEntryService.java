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

import com.antigravity.mes.web.ErrorCodes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
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

    /**
     * Logs one stretch of work.
     *
     * {@code startTime}/{@code endTime} are stored whenever given, so the entry can be
     * placed in the right hourly slot when read back. Several entries inside one hour
     * therefore each carry their real length instead of all claiming the full hour and
     * inflating machine time.
     *
     * Duration prefers an explicit {@code timeOnMachineMinutes}: live tracking measures
     * elapsed time to the second, which is finer than whole minutes derived from two
     * clock times. Only when no explicit duration is supplied is it computed from the
     * times - that is the hourly-slot flow, where the clock is all the supervisor enters.
     */
    @Transactional
    public ProductionEntryResponse logEntry(
            Long employeeId,
            Long stationId,
            LocalDate shiftDate,
            LocalTime startTime,
            LocalTime endTime,
            int piecesProduced,
            Double timeOnMachineMinutes,
            Long enteredByEmployeeId) {

        if (piecesProduced < 0) {
            throw new IllegalArgumentException(ErrorCodes.PIECES_NEGATIVE);
        }

        // A supplied end-before-start is a mistake worth rejecting even when the caller
        // also passes its own measured duration.
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(ErrorCodes.END_BEFORE_START);
        }

        // One person cannot be at two stations at once, and an hour holds 60 minutes and no
        // more. Without this an hour could accumulate several entries each claiming the full
        // 60 minutes, which silently understates efficiency for every figure derived from it.
        // Enforced here rather than only in the app so a stale client cannot corrupt the data.
        if (startTime != null && endTime != null) {
            boolean clashes = productionEntryRepository.findByEmployeeIdAndShiftDate(employeeId, shiftDate).stream()
                    .anyMatch(existing -> existing.getStartTime() != null
                            && existing.getEndTime() != null
                            && startTime.isBefore(existing.getEndTime())
                            && existing.getStartTime().isBefore(endTime));
            if (clashes) {
                throw new IllegalArgumentException(ErrorCodes.TIME_OVERLAP);
            }
        }

        final double minutes;
        if (timeOnMachineMinutes != null) {
            minutes = timeOnMachineMinutes;
        } else if (startTime != null && endTime != null) {
            minutes = Duration.between(startTime, endTime).toMinutes();
        } else {
            throw new IllegalArgumentException(ErrorCodes.TIME_REQUIRED);
        }
        if (minutes <= 0) {
            throw new IllegalArgumentException(ErrorCodes.TIME_NOT_POSITIVE);
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorCodes.EMPLOYEE_NOT_FOUND));
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorCodes.STATION_NOT_FOUND));

        double samEarned = piecesProduced * station.getSam();
        double efficiencyPct = (samEarned / minutes) * 100;

        // Always a new row, never an upsert - an operator can legitimately work the same
        // station more than once in a day (e.g. two separate batches), and each is its own
        // log entry. Collapsing them into one row per employee+station+day used to silently
        // overwrite everything but the last visit.
        ProductionEntry entry = new ProductionEntry();
        entry.setEmployee(employee);
        entry.setStation(station);
        entry.setShiftDate(shiftDate);
        entry.setStartTime(startTime);
        entry.setEndTime(endTime);
        entry.setPiecesProduced(piecesProduced);
        entry.setTimeOnMachineMinutes(minutes);
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
                entry.getStartTime(),
                entry.getEndTime(),
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
