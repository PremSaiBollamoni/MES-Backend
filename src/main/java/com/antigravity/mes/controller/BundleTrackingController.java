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
    public ResponseEntity<List<BundleResponse>> listActive(
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        return ResponseEntity.ok(bundleTrackingService.listActive(factoryId, Boolean.TRUE.equals(isSuperAdmin)));
    }

    @RequiresActivity("OPERATIONS")
    @PostMapping("/bundles")
    public ResponseEntity<BundleResponse> createBundle(
            @RequestAttribute("employeeId") Long employeeId,
            @RequestBody CreateBundleRequest request,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        return ResponseEntity.ok(bundleTrackingService.createBundle(
                request.bundleRef(), request.quantity(), request.initialStationId(), employeeId,
                factoryId, Boolean.TRUE.equals(isSuperAdmin)));
    }

    @RequiresActivity("OPERATIONS")
    @PostMapping("/bundles/{id}/move")
    public ResponseEntity<BundleResponse> moveBundle(
            @PathVariable Long id,
            @RequestAttribute("employeeId") Long employeeId,
            @RequestBody MoveBundleRequest request,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        return ResponseEntity.ok(bundleTrackingService.moveBundle(id, request.toStationId(), employeeId, factoryId, Boolean.TRUE.equals(isSuperAdmin)));
    }

    @RequiresActivity("OPERATIONS")
    @PostMapping("/bundles/{id}/complete")
    public ResponseEntity<BundleResponse> completeBundle(
            @PathVariable Long id,
            @RequestAttribute("employeeId") Long employeeId,
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        return ResponseEntity.ok(bundleTrackingService.completeBundle(id, employeeId, factoryId, Boolean.TRUE.equals(isSuperAdmin)));
    }

    @RequiresActivity("OPERATIONS")
    @GetMapping("/summary")
    public ResponseEntity<List<StationWipSummary>> getWipSummary(
            @RequestAttribute(required = false) Long factoryId,
            @RequestAttribute(required = false) Boolean isSuperAdmin) {
        return ResponseEntity.ok(bundleTrackingService.getWipSummary(factoryId, Boolean.TRUE.equals(isSuperAdmin)));
    }
}
