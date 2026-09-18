package com.antigravity.mes.payroll;

import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** HR-facing salary payroll sheet - a deterministic rollup of ProductionEntry wages per
 * operator for a date range, exported as a ready-to-use Excel workbook. Unlike the AI reports,
 * this is cheap arithmetic over existing data, so it is generated fresh on every request rather
 * than triggered-and-cached. */
@RestController
@RequestMapping("/api/payroll")
@CrossOrigin(origins = "*")
public class PayrollController {

    @Autowired
    private PayrollService payrollService;

    /** Same data the Excel sheet is built from, as JSON - lets HR preview every operator's
     * earnings breakdown live in the app before committing to a download. */
    @RequiresActivity("PAYROLL")
    @GetMapping("/preview")
    public ResponseEntity<List<PayrollService.OperatorPayroll>> preview(@RequestParam String startDate, @RequestParam String endDate) {
        return ResponseEntity.ok(payrollService.previewPayroll(LocalDate.parse(startDate), LocalDate.parse(endDate)));
    }

    @RequiresActivity("PAYROLL")
    @GetMapping("/xlsx")
    public ResponseEntity<byte[]> download(@RequestParam String startDate, @RequestParam String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        byte[] xlsx = payrollService.generatePayrollSheet(start, end);
        String filename = "payroll_" + start + "_to_" + end + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(xlsx);
    }
}
