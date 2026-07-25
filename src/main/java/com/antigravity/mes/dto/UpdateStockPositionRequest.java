package com.antigravity.mes.dto;

import java.time.LocalDate;

/** openingBalance is optional - omit it to auto-carry-forward from the previous day's closing. */
public record UpdateStockPositionRequest(String zoneCode, LocalDate positionDate, Integer openingBalance, int additions, int dispatched) {}
