-- Record WHEN a production entry happened, not just how long it took.
--
-- Production is now logged against hourly slots (09:00-10:00, 10:00-11:00, ...), and an
-- operator can have several entries within one hour at different stations. Storing only
-- time_on_machine_minutes made those entries indistinguishable once reloaded - the app
-- could send "20 minutes" but had no way to know which hour it belonged to.
--
-- Nullable so the existing entries, logged before slots existed, remain valid.
ALTER TABLE production_entries
    ADD COLUMN start_time TIME NULL AFTER shift_date,
    ADD COLUMN end_time   TIME NULL AFTER start_time;

-- Entries are read back a whole day at a time and grouped by hour in the UI.
CREATE INDEX idx_production_entries_date_start
    ON production_entries (shift_date, start_time);
