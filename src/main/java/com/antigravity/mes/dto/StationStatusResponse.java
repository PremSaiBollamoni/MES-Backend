package com.antigravity.mes.dto;

public record StationStatusResponse(
        Long stationId,
        String stationNumber,
        String operationName,
        String zone,
        double sam,
        Long allocatedEmployeeId,
        String allocatedEmployeeName,
        String status, // UNALLOCATED, MANNED, UNMANNED
        boolean critical // UNMANNED + this station is the current bottleneck (highest SAM)
) {}
