package com.antigravity.mes.service;

import com.antigravity.mes.dto.BundleResponse;
import com.antigravity.mes.dto.StationWipSummary;
import com.antigravity.mes.exception.ConflictException;
import com.antigravity.mes.exception.NotFoundException;
import com.antigravity.mes.model.Bundle;
import com.antigravity.mes.model.BundleMovement;
import com.antigravity.mes.model.Station;
import com.antigravity.mes.repository.BundleMovementRepository;
import com.antigravity.mes.repository.BundleRepository;
import com.antigravity.mes.repository.StationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WIP tracking (PALMS §8), scoped to bundle-ref + quantity rather than a full
 * order/style/colour/size link (that master data doesn't exist yet). A bundle's
 * movement between stations is logged as OUT-at-old / IN-at-new so the full
 * transit history survives even though only the current station is queried day-to-day.
 */
@Service
public class BundleTrackingService {

    private static final int WIP_ALERT_THRESHOLD = 2; // PALMS §8: "> 2 Bundles Ahead"

    @Autowired
    private BundleRepository bundleRepository;

    @Autowired
    private BundleMovementRepository bundleMovementRepository;

    @Autowired
    private StationRepository stationRepository;

    public List<BundleResponse> listActive() {
        return bundleRepository.findByStatusOrderByCreatedAtDesc(Bundle.Status.IN_PROGRESS).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BundleResponse createBundle(String bundleRef, int quantity, Long initialStationId, Long createdBy) {
        if (bundleRef == null || bundleRef.isBlank()) {
            throw new IllegalArgumentException("Bundle reference is required");
        }
        if (bundleRepository.existsByBundleRef(bundleRef.trim())) {
            throw new ConflictException("Bundle reference already exists: " + bundleRef);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        Station station = stationRepository.findById(initialStationId)
                .orElseThrow(() -> new NotFoundException("Station not found: " + initialStationId));

        Bundle bundle = new Bundle();
        bundle.setBundleRef(bundleRef.trim());
        bundle.setQuantity(quantity);
        bundle.setCurrentStation(station);
        bundle.setStatus(Bundle.Status.IN_PROGRESS);
        bundle.setCreatedByEmployeeId(createdBy);
        bundle = bundleRepository.save(bundle);

        logMovement(bundle, station, BundleMovement.ScanType.IN, createdBy);
        return toResponse(bundle);
    }

    @Transactional
    public BundleResponse moveBundle(Long bundleId, Long toStationId, Long recordedBy) {
        Bundle bundle = requireInProgressBundle(bundleId);
        Station toStation = stationRepository.findById(toStationId)
                .orElseThrow(() -> new NotFoundException("Station not found: " + toStationId));

        if (bundle.getCurrentStation() != null) {
            logMovement(bundle, bundle.getCurrentStation(), BundleMovement.ScanType.OUT, recordedBy);
        }
        bundle.setCurrentStation(toStation);
        bundleRepository.save(bundle);
        logMovement(bundle, toStation, BundleMovement.ScanType.IN, recordedBy);

        return toResponse(bundle);
    }

    @Transactional
    public BundleResponse completeBundle(Long bundleId, Long recordedBy) {
        Bundle bundle = requireInProgressBundle(bundleId);
        if (bundle.getCurrentStation() != null) {
            logMovement(bundle, bundle.getCurrentStation(), BundleMovement.ScanType.OUT, recordedBy);
        }
        bundle.setStatus(Bundle.Status.COMPLETED);
        bundle.setCurrentStation(null);
        return toResponse(bundleRepository.save(bundle));
    }

    /** Current WIP count per active station, flagged when it exceeds the §8 threshold. */
    public List<StationWipSummary> getWipSummary() {
        List<Bundle> active = bundleRepository.findByStatusOrderByCreatedAtDesc(Bundle.Status.IN_PROGRESS);
        Map<Long, Long> countByStation = active.stream()
                .filter(b -> b.getCurrentStation() != null)
                .collect(Collectors.groupingBy(b -> b.getCurrentStation().getId(), Collectors.counting()));

        return stationRepository.findAllByOrderByStationNumberAsc().stream()
                .filter(Station::isActive)
                .map(station -> {
                    long count = countByStation.getOrDefault(station.getId(), 0L);
                    return new StationWipSummary(station.getId(), station.getStationNumber(), station.getOperationName(),
                            count, count > WIP_ALERT_THRESHOLD);
                })
                .toList();
    }

    private Bundle requireInProgressBundle(Long bundleId) {
        Bundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(() -> new NotFoundException("Bundle not found: " + bundleId));
        if (bundle.getStatus() != Bundle.Status.IN_PROGRESS) {
            throw new ConflictException("This bundle is already completed.");
        }
        return bundle;
    }

    private void logMovement(Bundle bundle, Station station, BundleMovement.ScanType scanType, Long recordedBy) {
        BundleMovement movement = new BundleMovement();
        movement.setBundle(bundle);
        movement.setStation(station);
        movement.setScanType(scanType);
        movement.setScannedAt(LocalDateTime.now());
        movement.setRecordedByEmployeeId(recordedBy);
        bundleMovementRepository.save(movement);
    }

    private BundleResponse toResponse(Bundle bundle) {
        Station station = bundle.getCurrentStation();
        return new BundleResponse(
                bundle.getId(),
                bundle.getBundleRef(),
                bundle.getQuantity(),
                station != null ? station.getId() : null,
                station != null ? station.getStationNumber() : null,
                station != null ? station.getOperationName() : null,
                bundle.getStatus().name(),
                bundle.getCreatedAt()
        );
    }
}
