package com.antigravity.mes.scan;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The eight numbered hours on a production book page, mapped to the shift slots the app already
 * uses. The 13:00-13:30 lunch break sits between hours 4 and 5 and is not itself an hour.
 */
final class ShiftHours {

    private ShiftHours() {}

    private static final LocalTime[][] SLOTS = {
            {LocalTime.of(9, 0), LocalTime.of(10, 0)},
            {LocalTime.of(10, 0), LocalTime.of(11, 0)},
            {LocalTime.of(11, 0), LocalTime.of(12, 0)},
            {LocalTime.of(12, 0), LocalTime.of(13, 0)},
            {LocalTime.of(13, 30), LocalTime.of(14, 30)},
            {LocalTime.of(14, 30), LocalTime.of(15, 30)},
            {LocalTime.of(15, 30), LocalTime.of(16, 30)},
            {LocalTime.of(16, 30), LocalTime.of(17, 30)},
    };

    static boolean isValid(int hour) {
        return hour >= 1 && hour <= SLOTS.length;
    }

    /**
     * Splits one hour into consecutive slices, one per count written in it.
     *
     * The page records several counts against a single hour but no times, so the hour is divided
     * evenly. This keeps each entry inside its own stretch, which matters because entries for one
     * operator may not overlap and an hour may not exceed sixty minutes - two entries both
     * claiming the full hour would be rejected, and would halve the efficiency figures if they
     * weren't.
     */
    static List<LocalTime[]> slice(int hour, int parts) {
        LocalTime start = SLOTS[hour - 1][0];
        LocalTime end = SLOTS[hour - 1][1];
        int total = (int) java.time.Duration.between(start, end).toMinutes();

        List<LocalTime[]> slices = new ArrayList<>();
        int consumed = 0;
        for (int i = 0; i < parts; i++) {
            // Give the remainder to the earlier slices so the last one still lands exactly on the
            // slot boundary rather than drifting past it.
            int length = total / parts + (i < total % parts ? 1 : 0);
            LocalTime from = start.plusMinutes(consumed);
            consumed += length;
            slices.add(new LocalTime[]{from, start.plusMinutes(consumed)});
        }
        return slices;
    }
}
