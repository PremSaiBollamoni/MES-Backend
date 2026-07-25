-- PALMS §8 WIP tracking, scoped down: no Order/Style/Buyer master data exists yet
-- (that's RTM-prep, a later phase), so a bundle is identified by a free-text
-- reference rather than a formal order/style/colour/size link for now.
CREATE TABLE bundles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bundle_ref VARCHAR(100) NOT NULL UNIQUE,
    quantity INT NOT NULL,
    current_station_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    created_by_employee_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bundle_current_station FOREIGN KEY (current_station_id) REFERENCES sam_stations(id),
    CONSTRAINT fk_bundle_created_by FOREIGN KEY (created_by_employee_id) REFERENCES employees(id)
);

CREATE TABLE bundle_movements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bundle_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    scan_type VARCHAR(10) NOT NULL,
    scanned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recorded_by_employee_id BIGINT NOT NULL,
    CONSTRAINT fk_bundle_movement_bundle FOREIGN KEY (bundle_id) REFERENCES bundles(id) ON DELETE CASCADE,
    CONSTRAINT fk_bundle_movement_station FOREIGN KEY (station_id) REFERENCES sam_stations(id),
    CONSTRAINT fk_bundle_movement_recorded_by FOREIGN KEY (recorded_by_employee_id) REFERENCES employees(id)
);

CREATE INDEX idx_bundle_movement_bundle_time ON bundle_movements (bundle_id, scanned_at);
CREATE INDEX idx_bundle_current_station ON bundles (current_station_id);
