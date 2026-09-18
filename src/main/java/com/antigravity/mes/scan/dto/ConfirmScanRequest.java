package com.antigravity.mes.scan.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * What the supervisor confirmed after reviewing a scanned page. This is the only thing that
 * writes to the production log - the scan itself never does.
 *
 * The rows sent back are the corrected ones, not the draft: the supervisor may have fixed a
 * station, changed a count, or removed a row entirely.
 */
public record ConfirmScanRequest(
        Long employeeId,
        LocalDate shiftDate,
        List<ConfirmedRow> rows
) {
    public record ConfirmedRow(int hour, Long stationId, List<Integer> pieces) {}
}
