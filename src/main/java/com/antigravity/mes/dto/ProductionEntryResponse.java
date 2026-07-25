package com.antigravity.mes.dto;

import java.time.LocalDate;

public record ProductionEntryResponse(
        Long id,
        Long employeeId,
        String employeeName,
        Long stationId,
        String stationNumber,
        String operationName,
        LocalDate shiftDate,
        int piecesProduced,
        double timeOnMachineMinutes,
        double samEarned,
        double wagesEarned,
        double efficiencyPct,
        String classification // CRITICAL, LOW, MARGINAL, GOOD, EXCELLENT - PALMS §11
) {}
