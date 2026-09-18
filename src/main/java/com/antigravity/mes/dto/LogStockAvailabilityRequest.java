package com.antigravity.mes.dto;

import java.time.LocalDate;

/** Supervisor logs a raw quantity, not a status - AVAILABLE/NO_STOCK is derived server-side
 * from this against the station's minStockThreshold (PALMS §13 auto-availability). */
public record LogStockAvailabilityRequest(Long stationId, LocalDate shiftDate, Integer currentStockQty, String reason) {}
