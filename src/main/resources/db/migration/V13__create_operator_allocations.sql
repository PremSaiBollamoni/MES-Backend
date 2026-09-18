-- Operator <-> station assignment per shift/day. Everything else in Phase 2
-- (station attendance status, production entry) reads "who's allocated where"
-- from this table.
CREATE TABLE operator_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    shift_date DATE NOT NULL,
    shift VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_allocation_employee_shift UNIQUE (employee_id, shift_date, shift),
    CONSTRAINT fk_allocation_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_allocation_station FOREIGN KEY (station_id) REFERENCES sam_stations(id) ON DELETE CASCADE
);

CREATE INDEX idx_allocation_date ON operator_allocations (shift_date);
CREATE INDEX idx_allocation_station_date ON operator_allocations (station_id, shift_date);
