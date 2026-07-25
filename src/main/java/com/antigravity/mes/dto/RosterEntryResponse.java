package com.antigravity.mes.dto;

/** One present-today employee, with whatever allocation/tracking state they currently have (if any),
 * for the Supervisor's daily allocation board. */
public record RosterEntryResponse(
        Long employeeId,
        String employeeName,
        String employeeCode,
        Long allocationId,
        Long stationId,
        String stationNumber,
        String operationName,
        Long activeTrackingSessionId
) {}
