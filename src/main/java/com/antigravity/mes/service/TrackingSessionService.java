package com.antigravity.mes.service;

import com.antigravity.mes.dto.BundleResponse;
import com.antigravity.mes.dto.TrackingSessionResponse;
import com.antigravity.mes.exception.ConflictException;
import com.antigravity.mes.exception.NotFoundException;
import com.antigravity.mes.model.Bundle;
import com.antigravity.mes.model.Employee;
import com.antigravity.mes.model.Station;
import com.antigravity.mes.model.TrackingSession;
import com.antigravity.mes.repository.BundleRepository;
import com.antigravity.mes.repository.EmployeeRepository;
import com.antigravity.mes.repository.StationRepository;
import com.antigravity.mes.repository.TrackingSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Live "team is working right now" sessions (PALMS §10). A multi-operator station (e.g. Fusing,
 * 2 operators) shares ONE session across its whole team - one shared piece count, one shared
 * timer - because that's how the work is physically done. Exactly {@code station.operatorCount}
 * operators must be provided to start one, no more, no less. Stopping folds the shared result
 * into one {@link ProductionEntryService} entry per team member, each carrying the full team
 * output and elapsed time; wage is then split per-operator by dividing on
 * {@code station.operatorCount} at read time (see {@link ProductionEntryService#toResponse}).
 */
@Service
public class TrackingSessionService {

    @Autowired
    private TrackingSessionRepository trackingSessionRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private ProductionEntryService productionEntryService;

    @Autowired
    private BundleTrackingService bundleTrackingService;

    @Autowired
    private BundleRepository bundleRepository;

    public List<TrackingSessionResponse> listForDate(LocalDate date) {
        return trackingSessionRepository.findByShiftDateOrderByStartedAtDesc(date).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TrackingSessionResponse start(List<Long> employeeIds, Long stationId, LocalDate shiftDate, int piecesIssued, Long startedBy) {
        if (piecesIssued <= 0) {
            throw new IllegalArgumentException("Pieces issued must be greater than zero");
        }
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new IllegalArgumentException("At least one operator is required");
        }
        Set<Long> distinctIds = new LinkedHashSet<>(employeeIds);
        if (distinctIds.size() != employeeIds.size()) {
            throw new IllegalArgumentException("The same operator was selected more than once");
        }

        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new NotFoundException("Station not found: " + stationId));

        if (distinctIds.size() != station.getOperatorCount()) {
            throw new ConflictException(
                    station.getOperationName() + " requires exactly " + station.getOperatorCount()
                            + " operator(s) to start tracking (got " + distinctIds.size() + ").");
        }

        Set<Employee> employees = new LinkedHashSet<>();
        for (Long employeeId : distinctIds) {
            if (trackingSessionRepository.findActiveForEmployee(employeeId, TrackingSession.Status.ACTIVE).isPresent()) {
                throw new ConflictException("One of the selected operators already has a tracking session running.");
            }
            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new NotFoundException("Employee not found: " + employeeId));
            employees.add(employee);
        }

        TrackingSession session = new TrackingSession();
        session.setEmployees(employees);
        session.setStation(station);
        session.setShiftDate(shiftDate);
        session.setPiecesIssued(piecesIssued);
        session.setStatus(TrackingSession.Status.ACTIVE);
        session.setStartedAt(LocalDateTime.now());
        session.setStartedByEmployeeId(startedBy);
        session = trackingSessionRepository.save(session);

        // Auto-create the WIP bundle for these issued pieces (PALMS §8/§10 linkage) - the bundle
        // ref is derived from the session id so it's always unique and traceable back here.
        BundleResponse bundleResponse = bundleTrackingService.createBundle(
                "TRK-" + session.getId(), piecesIssued, stationId, startedBy);
        Bundle bundle = bundleRepository.findById(bundleResponse.id())
                .orElseThrow(() -> new NotFoundException("Bundle not found: " + bundleResponse.id()));
        session.setBundle(bundle);

        return toResponse(trackingSessionRepository.save(session));
    }

    /** Stops the session and logs one Production Entry per team member from the real elapsed time
     * and the pieces actually completed (defaults to the pieces originally issued if not overridden) -
     * each entry carries the full team output, with wages split per-operator at read time. */
    @Transactional
    public TrackingSessionResponse stop(Long sessionId, Integer piecesCompleted, Long stoppedBy) {
        TrackingSession session = trackingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Tracking session not found: " + sessionId));
        if (session.getStatus() != TrackingSession.Status.ACTIVE) {
            throw new ConflictException("This tracking session has already been stopped.");
        }

        int completed = piecesCompleted != null ? piecesCompleted : session.getPiecesIssued();
        if (completed < 0) {
            throw new IllegalArgumentException("Pieces completed cannot be negative");
        }

        LocalDateTime stoppedAt = LocalDateTime.now();
        double elapsedMinutes = Math.max(1, ChronoUnit.SECONDS.between(session.getStartedAt(), stoppedAt)) / 60.0;

        session.setStatus(TrackingSession.Status.STOPPED);
        session.setStoppedAt(stoppedAt);
        session.setPiecesCompleted(completed);
        session.setStoppedByEmployeeId(stoppedBy);
        session = trackingSessionRepository.save(session);

        for (Employee employee : session.getEmployees()) {
            // The session already knows exactly when work started and stopped, so record
            // those clock times on the entry too - that is what lets the hourly view show
            // tracked work in the right slot alongside manually logged entries. The
            // elapsed minutes are still passed explicitly, since they are measured to the
            // second and are more accurate than re-deriving them from whole minutes.
            productionEntryService.logEntry(
                    employee.getId(), session.getStation().getId(), session.getShiftDate(),
                    session.getStartedAt().toLocalTime(), stoppedAt.toLocalTime(),
                    completed, elapsedMinutes, stoppedBy);
        }

        if (session.getBundle() != null) {
            bundleTrackingService.completeBundle(session.getBundle().getId(), stoppedBy);
        }

        return toResponse(session);
    }

    private TrackingSessionResponse toResponse(TrackingSession session) {
        Station station = session.getStation();
        double estimatedWages = session.getPiecesIssued() * station.getRatePerPiece();

        List<TrackingSessionResponse.TrackingSessionEmployee> employees = session.getEmployees().stream()
                .map(e -> new TrackingSessionResponse.TrackingSessionEmployee(e.getId(), e.getName()))
                .toList();

        return new TrackingSessionResponse(
                session.getId(),
                employees,
                station.getId(),
                station.getStationNumber(),
                station.getOperationName(),
                station.getOperatorCount(),
                station.getSam(),
                station.getRatePerPiece(),
                session.getShiftDate(),
                session.getPiecesIssued(),
                estimatedWages,
                session.getStatus().name(),
                session.getStartedAt(),
                session.getStoppedAt(),
                session.getPiecesCompleted()
        );
    }
}
