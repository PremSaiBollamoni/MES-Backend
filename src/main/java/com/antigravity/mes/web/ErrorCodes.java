package com.antigravity.mes.web;

/**
 * Stable identifiers for errors the user will see.
 *
 * The backend deliberately knows nothing about languages. It returns a code plus an
 * English fallback; the app looks the code up in its own translation files and renders
 * the user's language. That keeps every translation in one place and means adding a new
 * language needs no backend change or redeploy at all.
 *
 * These strings are part of the API contract - the app matches on them exactly. Renaming
 * one silently degrades the app to the English fallback, so treat them as fixed.
 */
public final class ErrorCodes {

    private ErrorCodes() {}

    // Authorisation and session
    public static final String NO_ACCESS = "ERR_NO_ACCESS";
    public static final String SESSION_INVALID = "ERR_SESSION_INVALID";

    // Production entry
    public static final String PIECES_NEGATIVE = "ERR_PIECES_NEGATIVE";
    public static final String TIME_NOT_POSITIVE = "ERR_TIME_NOT_POSITIVE";
    public static final String TIME_REQUIRED = "ERR_TIME_REQUIRED";
    public static final String END_BEFORE_START = "ERR_END_BEFORE_START";
    public static final String TIME_OVERLAP = "ERR_TIME_OVERLAP";

    // Lookups
    public static final String EMPLOYEE_NOT_FOUND = "ERR_EMPLOYEE_NOT_FOUND";
    public static final String STATION_NOT_FOUND = "ERR_STATION_NOT_FOUND";

    // HRMS integration
    public static final String HRMS_NOT_CONFIGURED = "ERR_HRMS_NOT_CONFIGURED";
    public static final String HRMS_UNREACHABLE = "ERR_HRMS_UNREACHABLE";
    public static final String HRMS_REJECTED = "ERR_HRMS_REJECTED";

    // AI reports
    public static final String REPORTS_DISABLED = "ERR_REPORTS_DISABLED";
    public static final String REPORT_FAILED = "ERR_REPORT_FAILED";

    /** English text used when the app doesn't recognise a code - never shown if the app
     * is up to date, but better than a bare code if the two ever drift apart. */
    public static String fallback(String code) {
        return switch (code) {
            case NO_ACCESS -> "You do not have access to this feature";
            case SESSION_INVALID -> "Invalid or expired session - please log in again";
            case PIECES_NEGATIVE -> "Pieces produced cannot be negative";
            case TIME_NOT_POSITIVE -> "Time on machine must be greater than zero";
            case TIME_REQUIRED -> "A time range or duration is required";
            case END_BEFORE_START -> "End time must be after start time";
            case TIME_OVERLAP -> "This time overlaps another entry already logged for this operator";
            case EMPLOYEE_NOT_FOUND -> "Employee not found";
            case STATION_NOT_FOUND -> "Station not found";
            case HRMS_NOT_CONFIGURED -> "HRMS integration is not configured yet";
            case HRMS_UNREACHABLE -> "Couldn't reach the HRMS attendance portal";
            case HRMS_REJECTED -> "The HRMS portal rejected this server's login";
            case REPORTS_DISABLED -> "AI reports are disabled";
            case REPORT_FAILED -> "Report generation failed";
            default -> code;
        };
    }
}
