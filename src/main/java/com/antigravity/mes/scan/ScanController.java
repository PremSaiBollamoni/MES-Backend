package com.antigravity.mes.scan;

import com.antigravity.mes.scan.dto.ConfirmScanRequest;
import com.antigravity.mes.scan.dto.ScannedPage;
import com.antigravity.mes.web.RequiresActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Reads photographed production book pages.
 *
 * Scanning never writes anything. A page is read into a draft, the supervisor reviews and
 * corrects it, and only the explicit confirm call reaches the production log - which is what
 * makes it safe to import handwriting at all.
 */
@RestController
@RequestMapping("/api/production-scan")
@CrossOrigin(origins = "*")
public class ScanController {

    private static final Logger log = LoggerFactory.getLogger(ScanController.class);

    @Autowired private ScanImportService scanImportService;
    @Autowired private PageScanService pageScanService;

    /** Whether scanning is configured, so the app can hide the button rather than fail on tap. */
    @RequiresActivity("PRODUCTION_SCAN")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status() {
        return ResponseEntity.ok(Map.of("enabled", pageScanService.isEnabled()));
    }

    /** Who a book can belong to. Not the daily roster - see the service for why. */
    @RequiresActivity("PRODUCTION_SCAN")
    @GetMapping("/employees")
    public ResponseEntity<List<ScanImportService.ScannableEmployee>> employees() {
        return ResponseEntity.ok(scanImportService.scannableEmployees());
    }

    @RequiresActivity("PRODUCTION_SCAN")
    @PostMapping(value = "/read", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> read(
            @RequestParam("image") MultipartFile image,
            @RequestParam Long employeeId,
            @RequestParam(required = false) String expectedDate) {
        try {
            LocalDate expected = expectedDate == null || expectedDate.isBlank()
                    ? null : LocalDate.parse(expectedDate);
            ScannedPage page = scanImportService.scan(
                    image.getBytes(), image.getContentType(), employeeId, expected);
            return ResponseEntity.ok(page);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("code", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", e.getMessage()));
        } catch (Exception e) {
            log.error("Page scan failed for employee {}", employeeId, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("code", "ERR_SCAN_FAILED", "error", e.getMessage()));
        }
    }

    /** The supervisor's confirmed rows - this is the only path that writes production entries. */
    @RequiresActivity("PRODUCTION_SCAN")
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Integer>> confirm(
            @RequestAttribute("employeeId") Long enteredBy,
            @RequestBody ConfirmScanRequest request) {
        int saved = scanImportService.confirm(request, enteredBy);
        return ResponseEntity.ok(Map.of("entriesCreated", saved));
    }
}
