package com.antigravity.mes.controller;

import com.antigravity.mes.dto.EditProductionEntryRequest;
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
                request.piecesProduced(), request.timeOnMachineMinutes(), request.colour(), employeeId));
    }

    /** Corrects a logged entry - station, times and/or pieces. Capped at two edits per entry;
     * a third attempt returns ERR_EDIT_LIMIT_REACHED (see ProductionEntryService). */
    @RequiresActivity("OPERATIONS")
    @PutMapping("/{id}")
    public ResponseEntity<ProductionEntryResponse> edit(
            @PathVariable Long id, @RequestBody EditProductionEntryRequest request) {
        return ResponseEntity.ok(productionEntryService.editEntry(
                id, request.stationId(), request.startTime(), request.endTime(), request.piecesProduced(), request.colour()));
    }

    /** Removes a logged entry - used to undo a page confirmed against the wrong date or operator. */
    @RequiresActivity("OPERATIONS")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productionEntryService.deleteEntry(id);
        return ResponseEntity.noContent().build();
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
