package com.antigravity.mes.controller;

import com.antigravity.mes.dto.AllocationResponse;
import com.antigravity.mes.dto.AssignAllocationRequest;
import com.antigravity.mes.dto.RosterEntryResponse;
import com.antigravity.mes.service.OperatorAllocationService;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Operator <-> station allocation - Production Supervisor's daily assignment board. */
@RestController
@RequestMapping("/api/allocations")
@CrossOrigin(origins = "*")
public class OperatorAllocationController {

    @Autowired
    private OperatorAllocationService allocationService;

    @RequiresActivity("OPERATIONS")
    @GetMapping
    public ResponseEntity<List<AllocationResponse>> list(@RequestParam String date) {
        return ResponseEntity.ok(allocationService.listForDate(LocalDate.parse(date)));
    }

    @RequiresActivity("OPERATIONS")
    @GetMapping("/roster")
    public ResponseEntity<List<RosterEntryResponse>> roster(@RequestParam String date) {
        return ResponseEntity.ok(allocationService.rosterForDate(LocalDate.parse(date)));
    }

    @RequiresActivity("OPERATIONS")
    @PostMapping
    public ResponseEntity<AllocationResponse> assign(@RequestBody AssignAllocationRequest request) {
        return ResponseEntity.ok(allocationService.assign(request.employeeId(), request.stationId(), request.shiftDate(), request.shift()));
    }

    @RequiresActivity("OPERATIONS")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unassign(@PathVariable Long id) {
        allocationService.unassign(id);
        return ResponseEntity.noContent().build();
    }
}
