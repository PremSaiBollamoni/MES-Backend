-- PALMS §13: before each shift, every station is marked AVAILABLE or NO_STOCK.
-- NO_STOCK stations are "starved" and should raise a supervisor alert.
CREATE TABLE stock_availabilities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_id BIGINT NOT NULL,
    shift_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(255),
    logged_by_employee_id BIGINT NOT NULL,
    logged_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_stock_avail_station_date UNIQUE (station_id, shift_date),
    CONSTRAINT fk_stock_avail_station FOREIGN KEY (station_id) REFERENCES sam_stations(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_avail_logged_by FOREIGN KEY (logged_by_employee_id) REFERENCES employees(id)
);

CREATE INDEX idx_stock_avail_date ON stock_availabilities (shift_date);
