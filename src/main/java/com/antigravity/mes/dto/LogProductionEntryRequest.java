package com.antigravity.mes.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Logs one stretch of work at one station.
 *
 * Supply {@code startTime}/{@code endTime} (the hourly-slot flow - duration is derived
 * from the clock) or {@code timeOnMachineMinutes} directly. An explicit duration wins when
 * both are given, since a measured elapsed time is finer-grained than whole minutes
 * derived from two clock times; the times are still stored so the entry lands in the
 * right hourly slot.
 */
public record LogProductionEntryRequest(
        Long employeeId,
        Long stationId,
        LocalDate shiftDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer piecesProduced,
        Double timeOnMachineMinutes,
        String colour
) {}
