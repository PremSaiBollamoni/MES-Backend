package com.antigravity.mes.dto;

/** targetWagePerDay/Month are PER OPERATOR (station total target divided by operatorCount), not the station's combined payroll. */
public record StationResponse(
        Long id,
        String stationNumber,
        String operationName,
        String zone,
        double sam,
        int operatorCount,
        int targetPiecesPerHour,
        int targetPiecesPerDay,
        double ratePerPiece,
        int minStockThreshold,
        int maxStockThreshold,
        boolean active,
        double targetWagePerDay,
        double targetWagePerMonth
) {}
