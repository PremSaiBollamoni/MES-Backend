-- PALMS §6/§7/§21: the post-sewing pipeline (Finished Garments -> Washing -> Ironing
-- Rough -> QC -> Ironing Final -> Packing -> Ready To Market), each with its own
-- Opening/Additions/Dispatched/Closing formula and alert threshold. This is a
-- distinct concept from sam_stations (the 37 sewing operations feeding into FGS) -
-- these 7 zones are downstream of sewing entirely.
CREATE TABLE stock_zones (
    zone_code VARCHAR(10) PRIMARY KEY,
    zone_name VARCHAR(100) NOT NULL,
    sequence_no INT NOT NULL,
    max_alert_pieces INT
);

INSERT INTO stock_zones (zone_code, zone_name, sequence_no, max_alert_pieces) VALUES
('FGS',   'Finished Garments Station', 1, 50),
('WSH',   'Washing Station',           2, 200),
('IRN-R', 'Ironing Rough Press',       3, 180),
('QCV',   'QC Verification',           4, NULL),
('IRN-F', 'Ironing Final Press',       5, 160),
('PKG',   'Packing Station',           6, 240),
('RTM',   'Ready To Market',           7, NULL);

CREATE TABLE daily_stock_positions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_code VARCHAR(10) NOT NULL,
    position_date DATE NOT NULL,
    opening_balance INT NOT NULL DEFAULT 0,
    additions INT NOT NULL DEFAULT 0,
    dispatched INT NOT NULL DEFAULT 0,
    entered_by_employee_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_stock_position_zone_date UNIQUE (zone_code, position_date),
    CONSTRAINT fk_stock_position_zone FOREIGN KEY (zone_code) REFERENCES stock_zones(zone_code),
    CONSTRAINT fk_stock_position_entered_by FOREIGN KEY (entered_by_employee_id) REFERENCES employees(id)
);

CREATE INDEX idx_stock_position_date ON daily_stock_positions (position_date);
