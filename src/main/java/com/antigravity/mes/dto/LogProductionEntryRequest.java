package com.antigravity.mes.dto;

import java.time.LocalDate;

public record LogProductionEntryRequest(Long employeeId, Long stationId, LocalDate shiftDate, Integer piecesProduced, Double timeOnMachineMinutes) {}
