ALTER TABLE sam_stations
    ADD COLUMN max_stock_threshold INT NOT NULL DEFAULT 0 AFTER min_stock_threshold;

ALTER TABLE tracking_sessions
    ADD COLUMN bundle_id BIGINT NULL AFTER station_id,
    ADD CONSTRAINT fk_tracking_session_bundle FOREIGN KEY (bundle_id) REFERENCES bundles (id);
