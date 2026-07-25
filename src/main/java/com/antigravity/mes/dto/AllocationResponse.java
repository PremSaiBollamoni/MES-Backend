package com.antigravity.mes.dto;

import java.time.LocalDate;

public record AllocationResponse(
        Long id,
        Long employeeId,
        String employeeName,
        String employeeCode,
        Long stationId,
        String stationNumber,
        String operationName,
        LocalDate shiftDate,
        String shift
) {}
