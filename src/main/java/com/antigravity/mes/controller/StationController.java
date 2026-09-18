package com.antigravity.mes.controller;

import com.antigravity.mes.dto.BulkDeleteRequest;
import com.antigravity.mes.dto.BulkDeleteResult;
import com.antigravity.mes.dto.StationCreateRequest;
import com.antigravity.mes.dto.StationResponse;
import com.antigravity.mes.dto.StationUpdateRequest;
import com.antigravity.mes.service.StationManagementService;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Station / Operation Bulletin (SAM sheet) management - owned by the IE / SAM Analyst role. */
@RestController
@RequestMapping("/api/stations")
@CrossOrigin(origins = "*")
public class StationController {

    @Autowired
    private StationManagementService stationManagementService;

    @RequiresActivity("STATION_LIST")
    @GetMapping
    public ResponseEntity<List<StationResponse>> list() {
        return ResponseEntity.ok(stationManagementService.listStations());
    }

    @RequiresActivity("STATION_LIST")
    @GetMapping("/{id}")
    public ResponseEntity<StationResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(stationManagementService.getStation(id));
    }

    @RequiresActivity("STATION_FORM")
    @PostMapping
    public ResponseEntity<StationResponse> create(@RequestBody StationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stationManagementService.createStation(request));
    }

    @RequiresActivity("STATION_FORM")
    @PutMapping("/{id}")
    public ResponseEntity<StationResponse> update(@PathVariable Long id, @RequestBody StationUpdateRequest request) {
        return ResponseEntity.ok(stationManagementService.updateStation(id, request));
    }

    @RequiresActivity("STATION_FORM")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        stationManagementService.deleteStation(id);
        return ResponseEntity.noContent().build();
    }

    @RequiresActivity("STATION_FORM")
    @PostMapping("/bulk-delete")
    public ResponseEntity<BulkDeleteResult> bulkDelete(@RequestBody BulkDeleteRequest request) {
        return ResponseEntity.ok(stationManagementService.deleteStations(request.ids()));
    }
}
