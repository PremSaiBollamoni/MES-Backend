package com.antigravity.mes.controller;

import com.antigravity.mes.dto.StartTrackingRequest;
import com.antigravity.mes.dto.StopTrackingRequest;
import com.antigravity.mes.dto.TrackingSessionResponse;
import com.antigravity.mes.service.TrackingSessionService;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Live per-operator tracking (PALMS §10) - Production Supervisor starts/stops, everything else derives from it. */
@RestController
@RequestMapping("/api/tracking")
@CrossOrigin(origins = "*")
public class TrackingSessionController {

    @Autowired
    private TrackingSessionService trackingSessionService;

    @RequiresActivity("OPERATIONS")
    @GetMapping
    public ResponseEntity<List<TrackingSessionResponse>> list(
            @RequestParam String date,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        return ResponseEntity.ok(trackingSessionService.listForDate(LocalDate.parse(date), factoryId, Boolean.TRUE.equals(isSuperAdmin)));
    }

    @RequiresActivity("OPERATIONS")
    @PostMapping("/start")
    public ResponseEntity<TrackingSessionResponse> start(
            @RequestAttribute("employeeId") Long startedBy,
            @RequestBody StartTrackingRequest request,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        return ResponseEntity.ok(trackingSessionService.start(
                request.employeeIds(), request.stationId(), request.shiftDate(), request.piecesIssued(), startedBy,
                factoryId, Boolean.TRUE.equals(isSuperAdmin)));
    }

    @RequiresActivity("OPERATIONS")
    @PostMapping("/{id}/stop")
    public ResponseEntity<TrackingSessionResponse> stop(
            @PathVariable Long id,
            @RequestAttribute("employeeId") Long stoppedBy,
            @RequestBody(required = false) StopTrackingRequest request,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        Integer piecesCompleted = request != null ? request.piecesCompleted() : null;
        return ResponseEntity.ok(trackingSessionService.stop(id, piecesCompleted, stoppedBy, factoryId, Boolean.TRUE.equals(isSuperAdmin)));
    }
}
