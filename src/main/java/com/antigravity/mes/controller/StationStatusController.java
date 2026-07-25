package com.antigravity.mes.controller;

import com.antigravity.mes.dto.StationStatusResponse;
import com.antigravity.mes.service.StationStatusService;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/station-status")
@CrossOrigin(origins = "*")
public class StationStatusController {

    @Autowired
    private StationStatusService stationStatusService;

    @RequiresActivity("OPERATIONS")
    @GetMapping
    public ResponseEntity<List<StationStatusResponse>> getStatus(@RequestParam String date) {
        return ResponseEntity.ok(stationStatusService.getStatusForDate(LocalDate.parse(date)));
    }
}
