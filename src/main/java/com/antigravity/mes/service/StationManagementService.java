package com.antigravity.mes.service;

import com.antigravity.mes.dto.BulkDeleteResult;
import com.antigravity.mes.dto.StationCreateRequest;
import com.antigravity.mes.dto.StationResponse;
import com.antigravity.mes.dto.StationUpdateRequest;
import com.antigravity.mes.exception.ConflictException;
import com.antigravity.mes.exception.NotFoundException;
import com.antigravity.mes.model.Station;
import com.antigravity.mes.repository.StationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * CRUD for the Operation Bulletin / SAM sheet (stations). Owned by the IE / SAM Analyst
 * role - every wage, efficiency, and bottleneck calculation downstream reads this data.
 */
@Service
public class StationManagementService {

    @Autowired
    private StationRepository stationRepository;

    public List<StationResponse> listStations() {
        return stationRepository.findAllByOrderByStationNumberAsc().stream().map(this::toResponse).toList();
    }

    public StationResponse getStation(Long id) {
        return toResponse(requireStation(id));
    }

    @Transactional
    public StationResponse createStation(StationCreateRequest request) {
        if (isBlank(request.stationNumber())) {
            throw new IllegalArgumentException("Station number is required");
        }
        if (isBlank(request.operationName())) {
            throw new IllegalArgumentException("Operation name is required");
        }
        if (stationRepository.findByStationNumber(request.stationNumber().trim()).isPresent()) {
            throw new ConflictException("Station number already exists: " + request.stationNumber());
        }

        Station station = new Station();
        station.setStationNumber(request.stationNumber().trim());
        station.setOperationName(request.operationName().trim());
        station.setZone(parseZone(request.zone()));
        station.setSam(requireNonNull(request.sam(), "SAM"));
        station.setOperatorCount(request.operatorCount() == null ? 1 : request.operatorCount());
        station.setTargetPiecesPerHour(requireNonNull(request.targetPiecesPerHour(), "Target pieces per hour"));
        station.setTargetPiecesPerDay(requireNonNull(request.targetPiecesPerDay(), "Target pieces per day"));
        station.setRatePerPiece(requireNonNull(request.ratePerPiece(), "Rate per piece"));
        station.setMinStockThreshold(request.minStockThreshold() == null ? 0 : request.minStockThreshold());
        station.setMaxStockThreshold(request.maxStockThreshold() == null ? 0 : request.maxStockThreshold());
        return toResponse(stationRepository.save(station));
    }

    @Transactional
    public StationResponse updateStation(Long id, StationUpdateRequest request) {
        Station station = requireStation(id);

        if (!isBlank(request.operationName())) {
            station.setOperationName(request.operationName().trim());
        }
        if (!isBlank(request.zone())) {
            station.setZone(parseZone(request.zone()));
        }
        if (request.sam() != null) {
            station.setSam(request.sam());
        }
        if (request.operatorCount() != null) {
            station.setOperatorCount(request.operatorCount());
        }
        if (request.targetPiecesPerHour() != null) {
            station.setTargetPiecesPerHour(request.targetPiecesPerHour());
        }
        if (request.targetPiecesPerDay() != null) {
            station.setTargetPiecesPerDay(request.targetPiecesPerDay());
        }
        if (request.ratePerPiece() != null) {
            station.setRatePerPiece(request.ratePerPiece());
        }
        if (request.minStockThreshold() != null) {
            station.setMinStockThreshold(request.minStockThreshold());
        }
        if (request.maxStockThreshold() != null) {
            station.setMaxStockThreshold(request.maxStockThreshold());
        }
        if (request.active() != null) {
            station.setActive(request.active());
        }
        return toResponse(stationRepository.save(station));
    }

    @Transactional
    public void deleteStation(Long id) {
        Station station = requireStation(id);
        try {
            stationRepository.delete(station);
            stationRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Cannot delete this station - it is still referenced elsewhere (e.g. production records).");
        }
    }

    /** Deletes each station independently, so one that's still referenced doesn't block the rest of the batch. */
    public BulkDeleteResult deleteStations(List<Long> ids) {
        int deleted = 0;
        List<BulkDeleteResult.BulkDeleteFailure> failures = new ArrayList<>();
        for (Long id : ids) {
            try {
                deleteStation(id);
                deleted++;
            } catch (Exception e) {
                failures.add(new BulkDeleteResult.BulkDeleteFailure(id, e.getMessage()));
            }
        }
        return new BulkDeleteResult(deleted, failures);
    }

    private Station.Zone parseZone(String zone) {
        try {
            return Station.Zone.valueOf(zone.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Zone must be S1 or S2");
        }
    }

    private Station requireStation(Long id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Station not found: " + id));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private StationResponse toResponse(Station station) {
        // targetPiecesPerDay is the station's combined daily target across all its
        // operators; wage is paid per-operator on their own output, so divide the
        // station total by operator count before applying the piece rate. Verified
        // against the factory's OB sheet: Station 1 (2 operators) - 1260/2 * 0.6 = 378.
        double perOperatorTarget = station.getTargetPiecesPerDay() / (double) station.getOperatorCount();
        double targetWagePerDay = perOperatorTarget * station.getRatePerPiece();
        return new StationResponse(
                station.getId(),
                station.getStationNumber(),
                station.getOperationName(),
                station.getZone().name(),
                station.getSam(),
                station.getOperatorCount(),
                station.getTargetPiecesPerHour(),
                station.getTargetPiecesPerDay(),
                station.getRatePerPiece(),
                station.getMinStockThreshold(),
                station.getMaxStockThreshold(),
                station.isActive(),
                targetWagePerDay,
                targetWagePerDay * 26
        );
    }
}
