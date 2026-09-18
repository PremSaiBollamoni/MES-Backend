package com.antigravity.mes.dto;

import java.time.LocalDate;

public record StockPositionResponse(
        String zoneCode,
        String zoneName,
        LocalDate positionDate,
        int openingBalance,
        int additions,
        int dispatched,
        int closingBalance,
        Integer maxAlertPieces,
        boolean alertTriggered
) {}
