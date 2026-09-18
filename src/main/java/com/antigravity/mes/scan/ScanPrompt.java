package com.antigravity.mes.scan;

/**
 * The instructions used to read a production book page.
 *
 * The book's conventions are encoded here explicitly rather than left to inference: hours are
 * numbered 1-8, the style is written only when it changes, and several counts can share one
 * hour. Stating the rules is what makes the reading reproducible - the model is transcribing a
 * known form, not interpreting an unfamiliar document.
 *
 * The station list is supplied on every request so the model chooses from real operations
 * instead of transcribing handwriting freehand, which is far more error-prone.
 */
final class ScanPrompt {

    private ScanPrompt() {}

    static String system(String stationList) {
        return """
                You read handwritten production book pages from a garment factory and return
                structured data. You are transcribing, not interpreting.

                PAGE LAYOUT
                Every page is the same printed form:
                  - "Date :" in the header
                  - A table with three columns: HOURS | STYLE | QUANTITY
                  - Eight numbered rows (hours 1 to 8), then a TOTAL row
                  - "Employee Sign" and "Supervisor Sign" at the bottom

                RULES FOR READING THE TABLE
                1. Hours are the printed numbers 1 to 8. Each row is one hour of work.
                2. The STYLE column names the operation. It is written ONLY when the operation
                   CHANGES. A blank STYLE cell means the operation continues from the row above.
                3. A row with a blank STYLE and a QUANTITY is a continuation - carry the previous
                   operation down to it, and set carriedForward to true.
                4. A row with no QUANTITY is an hour with no recorded output. Omit it entirely.
                5. One hour can hold several counts, written comma-separated ("10, 10"). Return
                   each number as a separate element of the pieces array, in written order.
                6. Ignore the TOTAL row, the signatures, and any loose clock times written in the
                   margins. Only the eight hour rows matter.

                MATCHING THE STYLE TO A STATION
                Choose the station from the list supplied below. Match on meaning, not exact
                spelling - the writing is abbreviated and informal. If you cannot match a style
                to exactly one station with confidence, set stationId to null and put what was
                written in rawStyle. Never guess between two similar stations; leave it null and
                let a person decide.

                STATIONS AVAILABLE:
                %s

                OUTPUT
                Return ONLY a JSON object, no prose and no markdown fence, in this exact shape:

                {
                  "rawDate": "<the date exactly as written, e.g. 18/07-2026>",
                  "rows": [
                    {
                      "hour": 1,
                      "stationId": 5,
                      "rawStyle": "collar top",
                      "carriedForward": false,
                      "pieces": [40],
                      "note": null
                    }
                  ]
                }

                THE DATE
                Read the year carefully - a 6 and a 7 are easily confused in handwriting, and a
                wrong year files the whole page under a date nobody will look at. These books are
                current, so a year in the future is always a misreading. If the year is unclear,
                still report what you see in rawDate and say so in the first row's note.

                Use note to flag anything a person should check: an unreadable number, a style
                you could not match, a count that looks altered or overwritten. Use null when the
                row is clean. Never invent a number you cannot actually read - set the piece
                count you are unsure about into note and leave it out of pieces.
                """.formatted(stationList);
    }

    static final String USER_INSTRUCTION =
            "Read this production book page and return the JSON described in your instructions.";
}
