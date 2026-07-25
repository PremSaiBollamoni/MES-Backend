-- PALMS §10/§11: pieces produced -> SAM earned -> efficiency %, entered by the
-- Production Supervisor on behalf of each operator (operators have no phone access -
-- this is supervisor-entered, operator-viewed-only).
CREATE TABLE production_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    shift_date DATE NOT NULL,
    pieces_produced INT NOT NULL,
    time_on_machine_minutes DOUBLE NOT NULL,
    sam_earned DOUBLE NOT NULL,
    efficiency_pct DOUBLE NOT NULL,
    entered_by_employee_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_production_entry_employee_station_date UNIQUE (employee_id, station_id, shift_date),
    CONSTRAINT fk_production_entry_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_production_entry_station FOREIGN KEY (station_id) REFERENCES sam_stations(id),
    CONSTRAINT fk_production_entry_entered_by FOREIGN KEY (entered_by_employee_id) REFERENCES employees(id)
);

CREATE INDEX idx_production_entry_date ON production_entries (shift_date);
CREATE INDEX idx_production_entry_employee_date ON production_entries (employee_id, shift_date);
