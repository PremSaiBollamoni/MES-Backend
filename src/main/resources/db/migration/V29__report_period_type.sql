-- Supports monthly (as well as daily) AI reports: ATTENDANCE gains a monthly mode, and a new
-- EARNINGS report type is monthly-only (report_type is a plain string column, so no schema
-- change is needed to add that value). report_date continues to mean "period start" -
-- for a monthly report that is the 1st of the month; period_type disambiguates it from a daily
-- report that happens to land on the 1st.
ALTER TABLE generated_reports
    ADD COLUMN period_type VARCHAR(10) NOT NULL DEFAULT 'DAILY' AFTER report_type;

ALTER TABLE generated_reports
    DROP INDEX uq_generated_reports_date_type;

ALTER TABLE generated_reports
    ADD CONSTRAINT uq_generated_reports_date_type_period UNIQUE (report_date, report_type, period_type);
