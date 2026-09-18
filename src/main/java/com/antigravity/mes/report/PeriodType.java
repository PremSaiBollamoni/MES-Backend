package com.antigravity.mes.report;

/** Whether a generated report covers one calendar day or one calendar month. Only ATTENDANCE
 * supports both; EARNINGS is always MONTHLY; every other report type is always DAILY. */
public enum PeriodType {
    DAILY,
    MONTHLY
}
