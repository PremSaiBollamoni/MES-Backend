package com.antigravity.mes.controller;

import com.antigravity.mes.dto.LogProductionEntryRequest;
import com.antigravity.mes.dto.ProductionEntryResponse;
import com.antigravity.mes.service.ProductionEntryService;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/production-entries")
@CrossOrigin(origins = "*")
public class ProductionEntryController {

    @Autowired
    private ProductionEntryService productionEntryService;

    /** Supervisor's full view of a day's entries across every operator. */
    @RequiresActivity("OPERATIONS")
    @GetMapping
    public ResponseEntity<List<ProductionEntryResponse>> list(@RequestParam String date) {
        return ResponseEntity.ok(productionEntryService.listForDate(LocalDate.parse(date)));
    }

    /** Supervisor enters pieces produced on behalf of an operator - operators never call this. */
    @RequiresActivity("OPERATIONS")
    @PostMapping
    public ResponseEntity<ProductionEntryResponse> log(
            @RequestAttribute("employeeId") Long employeeId,
            @RequestBody LogProductionEntryRequest request) {
        return ResponseEntity.ok(productionEntryService.logEntry(
                request.employeeId(), request.stationId(), request.shiftDate(),
                request.startTime(), request.endTime(),
                request.piecesProduced(), request.timeOnMachineMinutes(), employeeId));
    }

    /** An operator's own read-only view - self-scoped from the JWT, no path/query employeeId accepted. */
    @RequiresActivity("MY_PRODUCTION")
    @GetMapping("/me")
    public ResponseEntity<List<ProductionEntryResponse>> myEntries(
            @RequestAttribute("employeeId") Long employeeId,
            @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(productionEntryService.listForEmployee(employeeId, days == null ? 30 : days));
    }
}
