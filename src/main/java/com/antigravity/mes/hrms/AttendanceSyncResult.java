package com.antigravity.mes.hrms;

import java.time.LocalDate;

public record AttendanceSyncResult(
        LocalDate from,
        LocalDate to,
        int daysProcessed,
        int recordsSynced,
        int recordsUnmatched
) {}
