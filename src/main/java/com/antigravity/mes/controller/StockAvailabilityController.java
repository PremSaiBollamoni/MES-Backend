package com.antigravity.mes.controller;

import com.antigravity.mes.dto.LogStockAvailabilityRequest;
import com.antigravity.mes.dto.StockAvailabilityResponse;
import com.antigravity.mes.service.StockAvailabilityService;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stock-availability")
@CrossOrigin(origins = "*")
public class StockAvailabilityController {

    @Autowired
    private StockAvailabilityService stockAvailabilityService;

    @RequiresActivity("OPERATIONS")
    @GetMapping
    public ResponseEntity<List<StockAvailabilityResponse>> list(@RequestParam String date) {
        return ResponseEntity.ok(stockAvailabilityService.listForDate(LocalDate.parse(date)));
    }

    @RequiresActivity("OPERATIONS")
    @PostMapping
    public ResponseEntity<StockAvailabilityResponse> log(
            @RequestAttribute("employeeId") Long employeeId,
            @RequestBody LogStockAvailabilityRequest request) {
        return ResponseEntity.ok(stockAvailabilityService.logStock(
                request.stationId(), request.shiftDate(),
                request.currentStockQty() == null ? 0 : request.currentStockQty(), request.reason(), employeeId));
    }
}
