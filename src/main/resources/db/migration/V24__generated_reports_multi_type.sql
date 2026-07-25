-- Support multiple report types (production, stock, attendance, worker performance) per date,
-- instead of a single AI report per day. Existing rows are all production reports.
ALTER TABLE generated_reports
    ADD COLUMN report_type VARCHAR(30) NOT NULL DEFAULT 'PRODUCTION' AFTER report_date;

ALTER TABLE generated_reports
    DROP INDEX report_date;

ALTER TABLE generated_reports
    ADD CONSTRAINT uq_generated_reports_date_type UNIQUE (report_date, report_type);
