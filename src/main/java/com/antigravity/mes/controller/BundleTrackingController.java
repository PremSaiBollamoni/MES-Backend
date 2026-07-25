package com.antigravity.mes.controller;

import com.antigravity.mes.dto.BundleResponse;
import com.antigravity.mes.dto.CreateBundleRequest;
import com.antigravity.mes.dto.MoveBundleRequest;
import com.antigravity.mes.dto.StationWipSummary;
import com.antigravity.mes.service.BundleTrackingService;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wip")
@CrossOrigin(origins = "*")
public class BundleTrackingController {

    @Autowired
    private BundleTrackingService bundleTrackingService;

    @RequiresActivity("OPERATIONS")
    @GetMapping("/bundles")
    public ResponseEntity<List<BundleResponse>> listActive() {
        return ResponseEntity.ok(bundleTrackingService.listActive());
    }

    @RequiresActivity("OPERATIONS")
    @PostMapping("/bundles")
    public ResponseEntity<BundleResponse> createBundle(
            @RequestAttribute("employeeId") Long employeeId,
            @RequestBody CreateBundleRequest request) {
        return ResponseEntity.ok(bundleTrackingService.createBundle(
                request.bundleRef(), request.quantity(), request.initialStationId(), employeeId));
    }

    @RequiresActivity("OPERATIONS")
    @PostMapping("/bundles/{id}/move")
    public ResponseEntity<BundleResponse> moveBundle(
            @PathVariable Long id,
            @RequestAttribute("employeeId") Long employeeId,
            @RequestBody MoveBundleRequest request) {
        return ResponseEntity.ok(bundleTrackingService.moveBundle(id, request.toStationId(), employeeId));
    }

    @RequiresActivity("OPERATIONS")
    @PostMapping("/bundles/{id}/complete")
    public ResponseEntity<BundleResponse> completeBundle(
            @PathVariable Long id,
            @RequestAttribute("employeeId") Long employeeId) {
        return ResponseEntity.ok(bundleTrackingService.completeBundle(id, employeeId));
    }

    @RequiresActivity("OPERATIONS")
    @GetMapping("/summary")
    public ResponseEntity<List<StationWipSummary>> getWipSummary() {
        return ResponseEntity.ok(bundleTrackingService.getWipSummary());
    }
}
