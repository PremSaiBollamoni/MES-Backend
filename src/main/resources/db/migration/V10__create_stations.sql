CREATE TABLE sam_stations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_number VARCHAR(20) NOT NULL UNIQUE,
    operation_name VARCHAR(150) NOT NULL,
    zone VARCHAR(10) NOT NULL,
    sam DOUBLE NOT NULL,
    operator_count INT NOT NULL DEFAULT 1,
    target_pieces_per_hour INT NOT NULL,
    target_pieces_per_day INT NOT NULL,
    rate_per_piece DOUBLE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_sam_stations_zone ON sam_stations (zone);

-- Seed with the factory's real Operation Bulletin / SAM sheet (37 stations).
-- Named sam_stations (not "stations") because a legacy, unused table from the
-- original V1 schema draft already occupies that name.
INSERT INTO sam_stations (station_number, operation_name, zone, sam, operator_count, target_pieces_per_hour, target_pieces_per_day, rate_per_piece) VALUES
('STATION-01', 'Fusing', 'S1', 0.5, 2, 180, 1260, 0.60),
('STATION-02', 'Collar Run', 'S1', 1.2, 1, 80, 560, 0.75),
('STATION-03', 'Collar Bone Preparing and Attaching', 'S1', 0.5, 1, 190, 1340, 0.30),
('STATION-04', 'Edge Cutting', 'S1', 0.3, 1, 150, 1050, 0.40),
('STATION-05', 'Collar Top', 'S1', 1.0, 1, 80, 560, 0.75),
('STATION-06', 'Neck Band Hemming', 'S1', 1.2, 1, 100, 700, 0.60),
('STATION-07', 'Collar Middle', 'S1', 0.8, 1, 80, 560, 0.75),
('STATION-08', 'Collar Band Attach', 'S1', 1.5, 1, 50, 350, 1.25),
('STATION-09', 'Cuff Hemming', 'S1', 0.8, 1, 90, 630, 0.60),
('STATION-10', 'Cuff Run', 'S1', 1.0, 1, 70, 490, 0.80),
('STATION-11', 'Cuff Turning and Collar Marking', 'S1', 0.4, 1, 130, 900, 0.40),
('STATION-12', 'Cuff Top Stitch', 'S1', 1.0, 1, 70, 500, 0.80),
('STATION-13', 'Back Main Label Adding and Attaching', 'S1', 0.4, 1, 90, 630, 0.80),
('STATION-14', 'Back Yoke Adding', 'S1', 1.2, 1, 70, 500, 0.80),
('STATION-15', 'Back Top Stitch', 'S1', 0.8, 1, 80, 560, 0.70),
('STATION-16', 'Front Kansai Left Placket', 'S1', 2.0, 1, 70, 500, 1.00),
('STATION-17', 'Front Kansai Right Placket', 'S1', 1.2, 1, 70, 500, 1.00),
('STATION-18', 'Pocket Ironing', 'S1', 0.6, 1, 70, 500, 0.80),
('STATION-19', 'Pocket Hemming', 'S1', 0.8, 1, 120, 900, 0.50),
('STATION-20', 'Pocket Attaching', 'S1', 1.2, 1, 70, 500, 0.80),
('STATION-21', 'Wash Care Label Adding', 'S1', 0.3, 1, 110, 750, 0.50),
('STATION-22', 'Sleeve Small Placket', 'S1', 1.5, 1, 90, 630, 0.60),
('STATION-23', 'Sleeve Small Placket Locking', 'S1', 0.5, 1, 90, 630, 0.60),
('STATION-24', 'Sleeve Big Placket', 'S1', 2.5, 1, 30, 210, 1.80),
('STATION-25', 'Sleeve Big Placket Iron', 'S1', 0.8, 1, 80, 560, 0.70),
('STATION-26', 'Cuff Ironing', 'S1', 0.4, 1, 90, 630, 0.60),
('STATION-27', 'Front and Back Attaching', 'S2', 0.9, 1, 60, 420, 1.10),
('STATION-28', 'Shoulder Top', 'S2', 0.6, 1, 80, 560, 0.70),
('STATION-29', 'Collar Attaching', 'S2', 1.2, 1, 50, 350, 1.00),
('STATION-30', 'Collar Finishing', 'S2', 1.0, 1, 50, 350, 1.25),
('STATION-31', 'Sleeve Attach', 'S2', 1.5, 1, 40, 280, 2.00),
('STATION-32', 'Sleeve Top', 'S2', 1.2, 1, 35, 250, 1.50),
('STATION-33', 'Side Seam & Side Loading', 'S2', 2.2, 1, 50, 350, 1.90),
('STATION-34', 'Cuff Attaching', 'S2', 1.5, 1, 40, 280, 1.40),
('STATION-35', 'Bottom Hemming', 'S2', 1.2, 1, 60, 420, 1.50),
('STATION-36', 'Button Hole Marking and Button Hole', 'S2', 1.0, 1, 50, 350, 1.00),
('STATION-37', 'Button Attach', 'S2', 1.2, 1, 50, 350, 1.00);
