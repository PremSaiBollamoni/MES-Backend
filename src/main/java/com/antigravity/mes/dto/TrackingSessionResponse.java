package com.antigravity.mes.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TrackingSessionResponse(
        Long id,
        List<TrackingSessionEmployee> employees,
        Long stationId,
        String stationNumber,
        String operationName,
        int requiredOperatorCount,
        double standardSam,
        double ratePerPiece,
        LocalDate shiftDate,
        int piecesIssued,
        double estimatedWages, // TOTAL for the team - divide by requiredOperatorCount for per-operator pay
        String status,
        LocalDateTime startedAt,
        LocalDateTime stoppedAt,
        Integer piecesCompleted
) {
    public record TrackingSessionEmployee(Long employeeId, String employeeName) {}
}
