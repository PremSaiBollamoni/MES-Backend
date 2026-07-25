package com.antigravity.mes.dto;

import java.time.LocalDate;

public record AssignAllocationRequest(Long employeeId, Long stationId, LocalDate shiftDate, String shift) {}
