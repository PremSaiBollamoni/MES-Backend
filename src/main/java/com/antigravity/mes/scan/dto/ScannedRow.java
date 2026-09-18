package com.antigravity.mes.scan.dto;

import java.util.List;

/**
 * One hour row lifted off a scanned production book page.
 *
 * {@code stationId} is null when the written style could not be matched to a station with
 * confidence - the supervisor picks it during review. {@code rawStyle} always carries what was
 * actually written so a wrong match is visible rather than silent, and {@code carriedForward}
 * marks rows whose style was inherited from the row above (the book only writes the style when
 * it changes).
 *
 * {@code pieces} is a list because one hour can hold several counts, written comma-separated
 * on the page ("10, 10").
 */
public record ScannedRow(
        int hour,
        Long stationId,
        String stationName,
        String rawStyle,
        boolean carriedForward,
        List<Integer> pieces,
        String note
) {
    /** A row the supervisor should look at before saving. */
    public boolean needsAttention() {
        return stationId == null || note != null;
    }
}
