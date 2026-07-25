package com.antigravity.mes.dto;

public record StationUpdateRequest(
        String operationName,
        String zone,
        Double sam,
        Integer operatorCount,
        Integer targetPiecesPerHour,
        Integer targetPiecesPerDay,
        Double ratePerPiece,
        Integer minStockThreshold,
        Integer maxStockThreshold,
        Boolean active
) {}
