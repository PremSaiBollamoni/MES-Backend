package com.antigravity.mes.scan.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The draft reading of one scanned page, returned for supervisor review. Nothing is written to
 * the production log until the supervisor confirms it.
 *
 * {@code shiftDate} is what was written on the page. {@code dateWarning} is set when that
 * disagrees with the date the caller expected from the book's page order - handwritten dates are
 * usually legible, but a mismatch is worth surfacing rather than trusting blindly.
 */
public record ScannedPage(
        Long employeeId,
        String employeeName,
        LocalDate shiftDate,
        String rawDate,
        String dateWarning,
        List<ScannedRow> rows,
        int rowsNeedingAttention
) {}
