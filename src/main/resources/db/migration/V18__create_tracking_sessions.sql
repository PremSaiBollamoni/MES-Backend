CREATE TABLE tracking_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    shift_date DATE NOT NULL,
    pieces_issued INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at DATETIME NOT NULL,
    started_by_employee_id BIGINT NOT NULL,
    stopped_at DATETIME,
    pieces_completed INT,
    stopped_by_employee_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tracking_session_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_tracking_session_station FOREIGN KEY (station_id) REFERENCES sam_stations (id)
);

CREATE INDEX idx_tracking_sessions_employee_status ON tracking_sessions (employee_id, status);
CREATE INDEX idx_tracking_sessions_shift_date ON tracking_sessions (shift_date);
