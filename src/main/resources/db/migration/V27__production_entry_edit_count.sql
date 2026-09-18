-- Tracks how many times a production entry has been corrected, so a supervisor can fix a
-- genuine mistake but not quietly rewrite history an unbounded number of times. The limit
-- itself (2) is enforced in ProductionEntryService, not here - this column only has to
-- survive across requests and app restarts, which a database column does and an in-memory
-- counter would not.

ALTER TABLE production_entries
    ADD COLUMN edit_count INT NOT NULL DEFAULT 0 AFTER entered_by_employee_id;
