-- generated_reports already has factory_id (added by V37) but it was never mapped onto
-- the Java entity, and the uniqueness constraint (added by a later migration, V29) is
-- still (report_date, report_type, period_type) globally - two factories generating,
-- say, a PRODUCTION report for the same date would silently collide/overwrite each
-- other's report. Scopes uniqueness to include factory_id.
ALTER TABLE generated_reports DROP INDEX `uq_generated_reports_date_type_period`;
ALTER TABLE generated_reports ADD CONSTRAINT uq_generated_reports_factory_date_type_period
    UNIQUE (factory_id, report_date, report_type, period_type);
