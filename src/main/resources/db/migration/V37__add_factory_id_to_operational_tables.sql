-- Add factory_id to sam_stations table (table is named sam_stations, not stations)
ALTER TABLE sam_stations ADD COLUMN factory_id BIGINT NULL AFTER id;
UPDATE sam_stations SET factory_id = 1;
ALTER TABLE sam_stations MODIFY COLUMN factory_id BIGINT NOT NULL;
ALTER TABLE sam_stations ADD CONSTRAINT fk_sam_stations_factory
    FOREIGN KEY (factory_id) REFERENCES factories(factory_id) ON DELETE RESTRICT;
ALTER TABLE sam_stations DROP INDEX `station_number`;
ALTER TABLE sam_stations ADD CONSTRAINT uq_sam_stations_factory_number
    UNIQUE (factory_id, station_number);
CREATE INDEX idx_sam_stations_factory_id ON sam_stations(factory_id);

-- Add factory_id to employee_info table
ALTER TABLE employee_info ADD COLUMN factory_id BIGINT NULL AFTER id;
UPDATE employee_info ei
    JOIN employees e ON ei.employee_id = e.id
    SET ei.factory_id = e.factory_id;
ALTER TABLE employee_info MODIFY COLUMN factory_id BIGINT NOT NULL;
ALTER TABLE employee_info ADD CONSTRAINT fk_employee_info_factory
    FOREIGN KEY (factory_id) REFERENCES factories(factory_id) ON DELETE RESTRICT;
ALTER TABLE employee_info DROP INDEX `uq_employee_info_code`;
ALTER TABLE employee_info ADD CONSTRAINT uq_employee_info_factory_code
    UNIQUE (factory_id, employee_code);
CREATE INDEX idx_employee_info_factory_id ON employee_info(factory_id);

-- Add factory_id to bundles table (no station join needed - default factory)
ALTER TABLE bundles ADD COLUMN factory_id BIGINT NULL AFTER id;
UPDATE bundles SET factory_id = 1;
ALTER TABLE bundles MODIFY COLUMN factory_id BIGINT NOT NULL;
ALTER TABLE bundles ADD CONSTRAINT fk_bundles_factory
    FOREIGN KEY (factory_id) REFERENCES factories(factory_id) ON DELETE RESTRICT;
CREATE INDEX idx_bundles_factory_id ON bundles(factory_id);

-- Add factory_id to bundle_movements table
ALTER TABLE bundle_movements ADD COLUMN factory_id BIGINT NULL AFTER id;
UPDATE bundle_movements bm
    JOIN sam_stations s ON bm.station_id = s.id
    SET bm.factory_id = s.factory_id;
ALTER TABLE bundle_movements MODIFY COLUMN factory_id BIGINT NOT NULL;
ALTER TABLE bundle_movements ADD CONSTRAINT fk_bundle_movements_factory
    FOREIGN KEY (factory_id) REFERENCES factories(factory_id) ON DELETE RESTRICT;
CREATE INDEX idx_bundle_movements_factory_id ON bundle_movements(factory_id);

-- Add factory_id to attendance_records table
ALTER TABLE attendance_records ADD COLUMN factory_id BIGINT NULL AFTER id;
UPDATE attendance_records ar
    JOIN employees e ON ar.employee_id = e.id
    SET ar.factory_id = e.factory_id;
ALTER TABLE attendance_records MODIFY COLUMN factory_id BIGINT NOT NULL;
ALTER TABLE attendance_records ADD CONSTRAINT fk_attendance_records_factory
    FOREIGN KEY (factory_id) REFERENCES factories(factory_id) ON DELETE RESTRICT;
CREATE INDEX idx_attendance_records_factory_id ON attendance_records(factory_id);

-- Add factory_id to production_entries table
ALTER TABLE production_entries ADD COLUMN factory_id BIGINT NULL AFTER id;
UPDATE production_entries pe
    JOIN sam_stations s ON pe.station_id = s.id
    SET pe.factory_id = s.factory_id;
ALTER TABLE production_entries MODIFY COLUMN factory_id BIGINT NOT NULL;
ALTER TABLE production_entries ADD CONSTRAINT fk_production_entries_factory
    FOREIGN KEY (factory_id) REFERENCES factories(factory_id) ON DELETE RESTRICT;
CREATE INDEX idx_production_entries_factory_id ON production_entries(factory_id);

-- Add factory_id to tracking_sessions table
ALTER TABLE tracking_sessions ADD COLUMN factory_id BIGINT NULL AFTER id;
UPDATE tracking_sessions ts
    JOIN sam_stations s ON ts.station_id = s.id
    SET ts.factory_id = s.factory_id;
ALTER TABLE tracking_sessions MODIFY COLUMN factory_id BIGINT NOT NULL;
ALTER TABLE tracking_sessions ADD CONSTRAINT fk_tracking_sessions_factory
    FOREIGN KEY (factory_id) REFERENCES factories(factory_id) ON DELETE RESTRICT;
CREATE INDEX idx_tracking_sessions_factory_id ON tracking_sessions(factory_id);

-- Add factory_id to operator_allocations table
ALTER TABLE operator_allocations ADD COLUMN factory_id BIGINT NULL AFTER id;
UPDATE operator_allocations oa
    JOIN sam_stations s ON oa.station_id = s.id
    SET oa.factory_id = s.factory_id;
ALTER TABLE operator_allocations MODIFY COLUMN factory_id BIGINT NOT NULL;
ALTER TABLE operator_allocations ADD CONSTRAINT fk_operator_allocations_factory
    FOREIGN KEY (factory_id) REFERENCES factories(factory_id) ON DELETE RESTRICT;
CREATE INDEX idx_operator_allocations_factory_id ON operator_allocations(factory_id);

-- Add factory_id to stock_availabilities table (plural)
ALTER TABLE stock_availabilities ADD COLUMN factory_id BIGINT NULL AFTER id;
UPDATE stock_availabilities sa
    JOIN sam_stations s ON sa.station_id = s.id
    SET sa.factory_id = s.factory_id;
ALTER TABLE stock_availabilities MODIFY COLUMN factory_id BIGINT NOT NULL;
ALTER TABLE stock_availabilities ADD CONSTRAINT fk_stock_availabilities_factory
    FOREIGN KEY (factory_id) REFERENCES factories(factory_id) ON DELETE RESTRICT;
CREATE INDEX idx_stock_availabilities_factory_id ON stock_availabilities(factory_id);

-- Add factory_id to stock_zones table (PK is zone_code, not id)
ALTER TABLE stock_zones ADD COLUMN factory_id BIGINT NULL AFTER zone_code;
UPDATE stock_zones SET factory_id = 1;
ALTER TABLE stock_zones MODIFY COLUMN factory_id BIGINT NOT NULL;
ALTER TABLE stock_zones ADD CONSTRAINT fk_stock_zones_factory
    FOREIGN KEY (factory_id) REFERENCES factories(factory_id) ON DELETE RESTRICT;
CREATE INDEX idx_stock_zones_factory_id ON stock_zones(factory_id);

-- Add factory_id to daily_stock_positions table (plural, no station join needed)
ALTER TABLE daily_stock_positions ADD COLUMN factory_id BIGINT NULL AFTER id;
UPDATE daily_stock_positions SET factory_id = 1;
ALTER TABLE daily_stock_positions MODIFY COLUMN factory_id BIGINT NOT NULL;
ALTER TABLE daily_stock_positions ADD CONSTRAINT fk_daily_stock_positions_factory
    FOREIGN KEY (factory_id) REFERENCES factories(factory_id) ON DELETE RESTRICT;
CREATE INDEX idx_daily_stock_positions_factory_id ON daily_stock_positions(factory_id);

-- Add factory_id to generated_reports table
ALTER TABLE generated_reports ADD COLUMN factory_id BIGINT NULL AFTER id;
UPDATE generated_reports gr
    JOIN employees e ON gr.generated_by_employee_id = e.id
    SET gr.factory_id = e.factory_id;
ALTER TABLE generated_reports MODIFY COLUMN factory_id BIGINT NOT NULL;
ALTER TABLE generated_reports ADD CONSTRAINT fk_generated_reports_factory
    FOREIGN KEY (factory_id) REFERENCES factories(factory_id) ON DELETE RESTRICT;
CREATE INDEX idx_generated_reports_factory_id ON generated_reports(factory_id);
