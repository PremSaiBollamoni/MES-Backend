ALTER TABLE sam_stations
    ADD COLUMN min_stock_threshold INT NOT NULL DEFAULT 0 AFTER rate_per_piece;

ALTER TABLE stock_availabilities
    ADD COLUMN current_stock_qty INT NOT NULL DEFAULT 0 AFTER status;
