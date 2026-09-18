package com.antigravity.mes.dto;

import java.time.LocalTime;

/**
 * A correction to an already-logged entry. Deliberately narrower than
 * {@link LogProductionEntryRequest}: the employee and shift date identify which entry is being
 * corrected via the path, not the body, so a correction can never quietly move an entry onto a
 * different day or a different person - that would be a delete-and-relog, not an edit.
 */
public record EditProductionEntryRequest(
        Long stationId,
        LocalTime startTime,
        LocalTime endTime,
        Integer piecesProduced,
        String colour
) {}
