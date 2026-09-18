package com.antigravity.mes.dto;

import java.time.LocalDateTime;

public record StockAvailabilityResponse(
        Long stationId,
        String stationNumber,
        String operationName,
        int minStockThreshold,
        int maxStockThreshold,
        int currentStockQty,
        String status, // NO_STOCK, LOW_STOCK, AVAILABLE, HIGH_STOCK, NOT_CHECKED
        String reason,
        LocalDateTime loggedAt
) {}
