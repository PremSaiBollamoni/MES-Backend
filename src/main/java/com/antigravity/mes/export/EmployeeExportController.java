package com.antigravity.mes.export;

import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** SUPER_ADMIN-only employee export. FACTORY_MANAGEMENT is granted to no role except SUPER_ADMIN. */
@RestController
@RequestMapping("/api/employee-export")
@CrossOrigin(origins = "*")
public class EmployeeExportController {

    @Autowired
    private EmployeeExportService exportService;

    public record ExportRequest(boolean resetPasswords) {}
    public record EmailExportRequest(boolean resetPasswords, List<String> recipients) {}

    @RequiresActivity("FACTORY_MANAGEMENT")
    @PostMapping("/factories/{factoryId}/download")
    public ResponseEntity<?> download(@PathVariable Long factoryId, @RequestBody ExportRequest request) {
        try {
            EmployeeExportService.Export export = exportService.export(factoryId, request.resetPasswords());
            String filename = export.factoryName().replaceAll("[^A-Za-z0-9]+", "-") + "-employees.xlsx";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(export.xlsx());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @RequiresActivity("FACTORY_MANAGEMENT")
    @PostMapping("/factories/{factoryId}/email")
    public ResponseEntity<?> email(@PathVariable Long factoryId, @RequestBody EmailExportRequest request) {
        if (request.recipients() == null || request.recipients().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "At least one recipient is required"));
        }
        try {
            exportService.emailExport(factoryId, request.resetPasswords(), request.recipients());
            return ResponseEntity.ok(Map.of("status", "SENT"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Couldn't send the email - " + e.getMessage()));
        }
    }
}
