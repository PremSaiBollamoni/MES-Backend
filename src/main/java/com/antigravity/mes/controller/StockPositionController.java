package com.antigravity.mes.controller;

import com.antigravity.mes.dto.StockPositionResponse;
import com.antigravity.mes.dto.UpdateStockPositionRequest;
import com.antigravity.mes.service.StockPositionService;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stock-position")
@CrossOrigin(origins = "*")
public class StockPositionController {

    @Autowired
    private StockPositionService stockPositionService;

    @RequiresActivity("OPERATIONS")
    @GetMapping
    public ResponseEntity<List<StockPositionResponse>> list(@RequestParam String date) {
        return ResponseEntity.ok(stockPositionService.listForDate(LocalDate.parse(date)));
    }

    @RequiresActivity("OPERATIONS")
    @PostMapping
    public ResponseEntity<StockPositionResponse> update(
            @RequestAttribute("employeeId") Long employeeId,
            @RequestBody UpdateStockPositionRequest request) {
        return ResponseEntity.ok(stockPositionService.update(
                request.zoneCode(), request.positionDate(), request.openingBalance(), request.additions(), request.dispatched(), employeeId));
    }
}
