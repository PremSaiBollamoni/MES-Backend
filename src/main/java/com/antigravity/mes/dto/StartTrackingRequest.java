package com.antigravity.mes.dto;

import java.time.LocalDate;
import java.util.List;

public record StartTrackingRequest(List<Long> employeeIds, Long stationId, LocalDate shiftDate, Integer piecesIssued) {}
