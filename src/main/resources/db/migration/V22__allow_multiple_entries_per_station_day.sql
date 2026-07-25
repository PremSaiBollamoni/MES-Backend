-- An operator can legitimately work the same station more than once in a day (e.g. two
-- separate batches). The old unique constraint made every re-visit silently overwrite the
-- previous visit's pieces/time/efficiency instead of keeping both as separate log entries.
ALTER TABLE production_entries DROP FOREIGN KEY fk_production_entry_employee;
ALTER TABLE production_entries DROP INDEX uq_production_entry_employee_station_date;
ALTER TABLE production_entries ADD CONSTRAINT fk_production_entry_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE;
