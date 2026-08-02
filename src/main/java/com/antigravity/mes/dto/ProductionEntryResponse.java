package com.antigravity.mes.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record ProductionEntryResponse(
        Long id,
        Long employeeId,
        String employeeName,
        Long stationId,
        String stationNumber,
        String operationName,
        LocalDate shiftDate,
        // Null for entries logged before hourly slots existed - the app groups those
        // under the day rather than assigning them to an hour.
        LocalTime startTime,
        LocalTime endTime,
        int piecesProduced,
        double timeOnMachineMinutes,
        double samEarned,
        double wagesEarned,
        double efficiencyPct,
        String classification // CRITICAL, LOW, MARGINAL, GOOD, EXCELLENT - PALMS §11
) {}
