package com.antigravity.mes.dto;

import java.time.LocalDateTime;

public record BundleResponse(
        Long id,
        String bundleRef,
        int quantity,
        Long currentStationId,
        String currentStationNumber,
        String currentOperationName,
        String status,
        LocalDateTime createdAt
) {}
