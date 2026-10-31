package com.antigravity.mes.controller;

import com.antigravity.mes.dto.DashboardSummaryResponse;
import com.antigravity.mes.service.DashboardService;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @RequiresActivity("DASHBOARD")
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary(@RequestAttribute(required = false) Long factoryId) {
        return ResponseEntity.ok(dashboardService.getSummary(factoryId));
    }
}
