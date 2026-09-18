-- Free-text colour of the fabric/batch being worked, captured per production entry.
ALTER TABLE production_entries
    ADD COLUMN colour VARCHAR(50) NULL AFTER pieces_produced;
