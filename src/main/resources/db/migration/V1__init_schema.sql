-- =====================================================================================
-- PALMS v2.0 - Complete initial schema
-- Source of truth: "App Requirements/PALMS.md"
--
-- Security notes:
--   * users.mobile_number is never stored in plaintext. It is stored twice:
--       - mobile_number_encrypted : AES-256-GCM ciphertext (application-layer, see
--         com.antigravity.mes.security.EncryptedStringConverter), non-deterministic.
--       - mobile_number_hash      : HMAC-SHA-256 blind index (deterministic, keyed),
--         used ONLY for equality lookup (login). It cannot be reversed to the phone
--         number and does not leak ordering the way a plain hash/index would.
--   * users.password_hash stores a BCrypt hash only. Plaintext passwords are never
--     persisted or logged.
--   * login_audit / audit_log provide tamper-evident traceability of authentication
--     attempts and sensitive data changes.
--   * All FKs default to RESTRICT (InnoDB default) to prevent silent orphaning of
--     production/traceability data.
-- =====================================================================================

-- ---------------------------------------------------------------------------
-- 1. Roles & Users  (PALMS.md §5 User Roles, §12 Attendance auth context)
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    role_code   VARCHAR(30)  NOT NULL PRIMARY KEY,
    description VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

INSERT INTO roles (role_code, description) VALUES
    ('FACTORY_MANAGER',       'Views all dashboards, approves stock positions, reviews RTM reports'),
    ('PRODUCTION_SUPERVISOR', 'Attendance, operator allocation, WIP monitoring, stock updates, shift management'),
    ('IE_ANALYST',            'Operation Bulletin, SAM updates, time & method studies, efficiency analysis'),
    ('OPERATOR',              'Scans bundles, views own efficiency and piece count'),
    ('QC_INSPECTOR',          'Defect logging, QC verification, rework management'),
    ('STORE_KEEPER',          'Packing stock, RTM stock, dispatch entries, manual stock updates');

CREATE TABLE users (
    id                      BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    mobile_number_encrypted VARCHAR(255) NOT NULL COMMENT 'AES-256-GCM ciphertext (base64)',
    mobile_number_hash      CHAR(64)     NOT NULL COMMENT 'HMAC-SHA-256 blind index for lookup',
    password_hash           VARCHAR(255) NOT NULL COMMENT 'BCrypt hash, never plaintext',
    name                    VARCHAR(150) NOT NULL,
    role_code               VARCHAR(30)  NOT NULL,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts   INT          NOT NULL DEFAULT 0,
    locked_until            DATETIME     NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_mobile_hash UNIQUE (mobile_number_hash),
    CONSTRAINT fk_users_role FOREIGN KEY (role_code) REFERENCES roles(role_code)
) ENGINE=InnoDB;

CREATE TABLE login_audit (
    id                  BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT      NULL,
    mobile_number_hash  CHAR(64)    NOT NULL,
    result              ENUM('SUCCESS','FAILURE') NOT NULL,
    ip_address          VARCHAR(45) NULL,
    attempted_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_login_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_login_audit_hash_time (mobile_number_hash, attempted_at)
) ENGINE=InnoDB;

CREATE TABLE audit_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    table_name  VARCHAR(100) NOT NULL,
    record_id   BIGINT      NOT NULL,
    action      ENUM('INSERT','UPDATE','DELETE') NOT NULL,
    changed_by  BIGINT      NULL,
    changed_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    old_data    JSON        NULL,
    new_data    JSON        NULL,
    CONSTRAINT fk_audit_log_user FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_audit_log_table_record (table_name, record_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 2. Master data (§4 Factory Flow, §19 RTM Rules)
-- ---------------------------------------------------------------------------
CREATE TABLE buyers (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    buyer_name VARCHAR(150) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_buyers_name UNIQUE (buyer_name)
) ENGINE=InnoDB;

CREATE TABLE styles (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    style_code VARCHAR(50)  NOT NULL,
    style_name VARCHAR(150) NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_styles_code UNIQUE (style_code)
) ENGINE=InnoDB;

CREATE TABLE orders (
    id             BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_number   VARCHAR(50) NOT NULL,
    buyer_id       BIGINT      NOT NULL,
    style_id       BIGINT      NOT NULL,
    order_quantity INT         NOT NULL,
    moq            INT         NULL COMMENT 'Minimum order quantity, used for RTM below-MOQ alert',
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_orders_number UNIQUE (order_number),
    CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES buyers(id),
    CONSTRAINT fk_orders_style FOREIGN KEY (style_id) REFERENCES styles(id)
) ENGINE=InnoDB;

CREATE TABLE zones (
    zone_code   VARCHAR(10)  NOT NULL PRIMARY KEY COMMENT 'S1, S2, FGS, WSH, IRN-R, QCV, IRN-F, PKG, RTM',
    zone_name   VARCHAR(100) NOT NULL,
    sequence_no INT          NOT NULL
) ENGINE=InnoDB;

INSERT INTO zones (zone_code, zone_name, sequence_no) VALUES
    ('S1',    'Sub Assembly',            1),
    ('S2',    'Full Garment Assembly',   2),
    ('FGS',   'Finished Garments Station', 3),
    ('WSH',   'Washing Station',         4),
    ('IRN-R', 'Ironing Rough Press',     5),
    ('QCV',   'QC Verification',         6),
    ('IRN-F', 'Ironing Final Press',     7),
    ('PKG',   'Packing Station',         8),
    ('RTM',   'Ready To Market',         9);

CREATE TABLE stations (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    station_code   VARCHAR(20)  NOT NULL,
    station_name   VARCHAR(150) NOT NULL,
    zone_code      VARCHAR(10)  NOT NULL,
    line_group     VARCHAR(50)  NULL COMMENT 'Preparation / Collar Line / Cuff Line / Back Body / Front Body / Sleeve Line',
    sequence_no    INT          NOT NULL,
    max_wip_alert  INT          NULL COMMENT 'Station-specific alert threshold, e.g. FGS>50, WSH>200',
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_stations_code UNIQUE (station_code),
    CONSTRAINT fk_stations_zone FOREIGN KEY (zone_code) REFERENCES zones(zone_code)
) ENGINE=InnoDB;

CREATE TABLE operations (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    station_id     BIGINT       NOT NULL,
    operation_name VARCHAR(150) NOT NULL,
    sequence_no    INT          NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_operations_station FOREIGN KEY (station_id) REFERENCES stations(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 3. SAM Management (§10 Efficiency Formula, §14 Industry SAM, §17 Derived Actual Time)
-- ---------------------------------------------------------------------------
CREATE TABLE sam_master (
    id                          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operation_id                BIGINT        NOT NULL,
    sam_minutes                 DECIMAL(6,2)  NOT NULL,
    sam_source                  ENUM('FACTORY','INDUSTRY') NOT NULL DEFAULT 'FACTORY',
    status                      ENUM('APPROVED','PENDING_STUDY') NOT NULL DEFAULT 'PENDING_STUDY',
    observation_count           INT           NOT NULL DEFAULT 0,
    min_observations_required   INT           NOT NULL DEFAULT 30,
    excluded_from_incentive     BOOLEAN       NOT NULL DEFAULT FALSE COMMENT 'TRUE while status=PENDING_STUDY',
    effective_from              DATE          NOT NULL,
    created_at                  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sam_master_op_date UNIQUE (operation_id, effective_from),
    CONSTRAINT fk_sam_master_operation FOREIGN KEY (operation_id) REFERENCES operations(id)
) ENGINE=InnoDB;

CREATE TABLE time_study_observations (
    id                      BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sam_master_id           BIGINT        NOT NULL,
    observed_cycle_seconds  DECIMAL(8,2)  NOT NULL,
    observer_id             BIGINT        NOT NULL,
    observed_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_derived              BOOLEAN       NOT NULL DEFAULT FALSE COMMENT 'Actual Time = 60 / Actual Pieces Per Hour, marked (D)',
    replace_by              DATE          NULL COMMENT 'Derived values must be replaced within 5 working days',
    CONSTRAINT fk_tso_sam_master FOREIGN KEY (sam_master_id) REFERENCES sam_master(id),
    CONSTRAINT fk_tso_observer FOREIGN KEY (observer_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 4. WIP tracking (§8 WIP Tracking)
-- ---------------------------------------------------------------------------
CREATE TABLE bundles (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bundle_barcode      VARCHAR(64)  NOT NULL,
    order_id            BIGINT       NOT NULL,
    size                VARCHAR(10)  NOT NULL,
    colour              VARCHAR(50)  NOT NULL,
    quantity            INT          NOT NULL,
    current_station_id  BIGINT       NULL,
    status              ENUM('IN_PROGRESS','COMPLETED','SCRAPPED') NOT NULL DEFAULT 'IN_PROGRESS',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bundles_barcode UNIQUE (bundle_barcode),
    CONSTRAINT fk_bundles_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_bundles_station FOREIGN KEY (current_station_id) REFERENCES stations(id)
) ENGINE=InnoDB;

CREATE TABLE bundle_movements (
    id          BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bundle_id   BIGINT    NOT NULL,
    station_id  BIGINT    NOT NULL,
    operator_id BIGINT    NULL,
    scan_type   ENUM('IN','OUT') NOT NULL,
    scanned_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bm_bundle FOREIGN KEY (bundle_id) REFERENCES bundles(id),
    CONSTRAINT fk_bm_station FOREIGN KEY (station_id) REFERENCES stations(id),
    CONSTRAINT fk_bm_operator FOREIGN KEY (operator_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_bm_bundle_time (bundle_id, scanned_at),
    INDEX idx_bm_station_time (station_id, scanned_at)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 5. Production / Efficiency (§10 Efficiency Formula, §11 Classification, §9 Bottleneck)
-- ---------------------------------------------------------------------------
CREATE TABLE production_entries (
    id                         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operator_id                BIGINT        NOT NULL,
    station_id                 BIGINT        NOT NULL,
    bundle_id                  BIGINT        NULL,
    shift_date                 DATE          NOT NULL,
    shift                      VARCHAR(20)   NOT NULL DEFAULT 'GENERAL',
    pieces_produced             INT           NOT NULL,
    sam_earned                 DECIMAL(8,2)  NOT NULL COMMENT 'pieces_produced * SAM',
    time_on_machine_minutes    DECIMAL(8,2)  NOT NULL,
    efficiency_pct             DECIMAL(6,2)  GENERATED ALWAYS AS
                                    (ROUND(sam_earned / NULLIF(time_on_machine_minutes, 0) * 100, 2)) STORED,
    created_at                 TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pe_operator FOREIGN KEY (operator_id) REFERENCES users(id),
    CONSTRAINT fk_pe_station FOREIGN KEY (station_id) REFERENCES stations(id),
    CONSTRAINT fk_pe_bundle FOREIGN KEY (bundle_id) REFERENCES bundles(id),
    INDEX idx_pe_operator_date (operator_id, shift_date),
    INDEX idx_pe_station_date (station_id, shift_date)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 6. Attendance & Stock Availability (§12, §13)
-- ---------------------------------------------------------------------------
CREATE TABLE attendance (
    id                        BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operator_id               BIGINT    NOT NULL,
    station_id                BIGINT    NULL,
    shift_date                DATE      NOT NULL,
    status                    ENUM('PRESENT','ABSENT','REASSIGNED') NOT NULL,
    reassigned_to_station_id  BIGINT    NULL,
    recorded_by               BIGINT    NOT NULL,
    recorded_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attendance_operator_date UNIQUE (operator_id, shift_date),
    CONSTRAINT fk_att_operator FOREIGN KEY (operator_id) REFERENCES users(id),
    CONSTRAINT fk_att_station FOREIGN KEY (station_id) REFERENCES stations(id),
    CONSTRAINT fk_att_reassigned_station FOREIGN KEY (reassigned_to_station_id) REFERENCES stations(id),
    CONSTRAINT fk_att_recorded_by FOREIGN KEY (recorded_by) REFERENCES users(id),
    INDEX idx_attendance_date (shift_date)
    -- Retention: 12 months (PALMS.md §12). Enforce via a scheduled archival/purge job,
    -- not a DDL constraint, so historical audit trails are never silently truncated.
) ENGINE=InnoDB;

CREATE TABLE stock_availability (
    id          BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    station_id  BIGINT    NOT NULL,
    shift_date  DATE      NOT NULL,
    status      ENUM('AVAILABLE','NO_STOCK') NOT NULL,
    reason      VARCHAR(255) NULL,
    logged_by   BIGINT    NOT NULL,
    logged_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_stock_avail_station_date UNIQUE (station_id, shift_date),
    CONSTRAINT fk_sa_station FOREIGN KEY (station_id) REFERENCES stations(id),
    CONSTRAINT fk_sa_logged_by FOREIGN KEY (logged_by) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 7. Daily Stock Position Board (§6, §7, §21)
-- ---------------------------------------------------------------------------
CREATE TABLE daily_stock_position (
    id               BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    station_id       BIGINT    NOT NULL,
    position_date    DATE      NOT NULL,
    opening_balance  INT       NOT NULL DEFAULT 0,
    additions        INT       NOT NULL DEFAULT 0,
    dispatched       INT       NOT NULL DEFAULT 0,
    closing_balance  INT       GENERATED ALWAYS AS (opening_balance + additions - dispatched) STORED,
    max_wip          INT       NULL,
    alert_triggered  BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_dsp_station_date UNIQUE (station_id, position_date),
    CONSTRAINT fk_dsp_station FOREIGN KEY (station_id) REFERENCES stations(id)
) ENGINE=InnoDB;

CREATE TABLE washing_batches (
    id                  BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    station_id          BIGINT    NOT NULL,
    batch_date          DATE      NOT NULL,
    wash_cycle_no       INT       NOT NULL,
    batch_size          INT       NOT NULL,
    cycle_time_minutes  INT       NOT NULL,
    pieces_washed       INT       NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wb_station FOREIGN KEY (station_id) REFERENCES stations(id)
) ENGINE=InnoDB;

CREATE TABLE packing_operation_log (
    id                 BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bundle_id          BIGINT    NULL,
    packing_operation  ENUM('COLLAR_STAY_TISSUE_INSERT','PRICE_TAG_ATTACH','FOLDING_ON_BOARD',
                             'POLYBAG_INSERTION','BOX_PACKING','CARTON_SEALING') NOT NULL,
    quantity           INT       NOT NULL,
    operator_id        BIGINT    NULL,
    logged_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pol_bundle FOREIGN KEY (bundle_id) REFERENCES bundles(id),
    CONSTRAINT fk_pol_operator FOREIGN KEY (operator_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE packing_summary (
    id                 BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    position_date      DATE      NOT NULL,
    cartons_packed     INT       NOT NULL DEFAULT 0,
    cartons_sealed     INT       NOT NULL DEFAULT 0,
    pieces_per_carton  INT       NULL,
    CONSTRAINT uq_packing_summary_date UNIQUE (position_date)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 8. QC (§4 Post Sewing Flow - QCV)
-- ---------------------------------------------------------------------------
CREATE TABLE qc_defects (
    id               BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bundle_id        BIGINT    NOT NULL,
    station_id       BIGINT    NOT NULL,
    defect_type      VARCHAR(100) NOT NULL,
    quantity         INT       NOT NULL,
    rework_required  BOOLEAN   NOT NULL DEFAULT FALSE,
    inspector_id     BIGINT    NOT NULL,
    logged_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_qc_bundle FOREIGN KEY (bundle_id) REFERENCES bundles(id),
    CONSTRAINT fk_qc_station FOREIGN KEY (station_id) REFERENCES stations(id),
    CONSTRAINT fk_qc_inspector FOREIGN KEY (inspector_id) REFERENCES users(id),
    INDEX idx_qc_station_time (station_id, logged_at)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 9. RTM (§19 RTM Rules - cumulative, does NOT reset daily)
-- ---------------------------------------------------------------------------
CREATE TABLE rtm_stock_ledger (
    id                BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id          BIGINT    NOT NULL,
    style_id          BIGINT    NOT NULL,
    buyer_id          BIGINT    NOT NULL,
    size              VARCHAR(10) NOT NULL,
    colour            VARCHAR(50) NOT NULL,
    transaction_type  ENUM('PRODUCTION_ADDED','DISPATCHED') NOT NULL,
    quantity          INT       NOT NULL,
    transaction_date  DATE      NOT NULL,
    recorded_by       BIGINT    NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rtm_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_rtm_style FOREIGN KEY (style_id) REFERENCES styles(id),
    CONSTRAINT fk_rtm_buyer FOREIGN KEY (buyer_id) REFERENCES buyers(id),
    CONSTRAINT fk_rtm_recorded_by FOREIGN KEY (recorded_by) REFERENCES users(id),
    INDEX idx_rtm_order_style_date (order_id, style_id, transaction_date)
) ENGINE=InnoDB;

CREATE TABLE rtm_daily_report (
    id             BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    report_date    DATE      NOT NULL COMMENT 'Generated daily at 17:00',
    order_id       BIGINT    NOT NULL,
    style_id       BIGINT    NOT NULL,
    buyer_id       BIGINT    NOT NULL,
    opening_stock  INT       NOT NULL,
    added          INT       NOT NULL,
    dispatched     INT       NOT NULL,
    closing_stock  INT       GENERATED ALWAYS AS (opening_stock + added - dispatched) STORED,
    generated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rtm_report UNIQUE (report_date, order_id, style_id, buyer_id),
    CONSTRAINT fk_rtm_report_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_rtm_report_style FOREIGN KEY (style_id) REFERENCES styles(id),
    CONSTRAINT fk_rtm_report_buyer FOREIGN KEY (buyer_id) REFERENCES buyers(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 10. Method Study (§16)
-- ---------------------------------------------------------------------------
CREATE TABLE method_study_work_orders (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    station_id     BIGINT       NOT NULL,
    operator_id    BIGINT       NOT NULL,
    efficiency_pct DECIMAL(6,2) NOT NULL COMMENT 'Trigger: < 50%',
    shift_date     DATE         NOT NULL,
    status         ENUM('OPEN','ASSIGNED','IN_PROGRESS','CLOSED') NOT NULL DEFAULT 'OPEN',
    assigned_to    BIGINT       NULL,
    opened_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at      TIMESTAMP    NULL,
    CONSTRAINT fk_msw_station FOREIGN KEY (station_id) REFERENCES stations(id),
    CONSTRAINT fk_msw_operator FOREIGN KEY (operator_id) REFERENCES users(id),
    CONSTRAINT fk_msw_assigned_to FOREIGN KEY (assigned_to) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 11. Pacemaker Registry (§15)
-- ---------------------------------------------------------------------------
CREATE TABLE pacemaker_registry (
    id                     BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operator_id            BIGINT       NOT NULL,
    station_id             BIGINT       NOT NULL,
    efficiency_pct         DECIMAL(6,2) NOT NULL COMMENT '>= 150%',
    achieved_date          DATE         NOT NULL,
    trainer_candidate      BOOLEAN      NOT NULL DEFAULT TRUE,
    recognition_eligible   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pacemaker_operator_date UNIQUE (operator_id, achieved_date),
    CONSTRAINT fk_pr_operator FOREIGN KEY (operator_id) REFERENCES users(id),
    CONSTRAINT fk_pr_station FOREIGN KEY (station_id) REFERENCES stations(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 12. Production Loss / Shift Risk (§18)
-- ---------------------------------------------------------------------------
CREATE TABLE shift_risk_log (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shift_date       DATE         NOT NULL,
    shift            VARCHAR(20)  NOT NULL DEFAULT 'GENERAL',
    total_risk_score DECIMAL(10,2) NOT NULL,
    computed_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_shift_risk_date UNIQUE (shift_date, shift)
) ENGINE=InnoDB;

CREATE TABLE shift_risk_contributors (
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shift_risk_log_id    BIGINT       NOT NULL,
    station_id           BIGINT       NOT NULL,
    reason               ENUM('ABSENT','NO_STOCK') NOT NULL,
    sam_minutes          DECIMAL(6,2) NOT NULL,
    contribution_score   DECIMAL(10,2) GENERATED ALWAYS AS (480 / NULLIF(sam_minutes, 0)) STORED,
    CONSTRAINT fk_src_log FOREIGN KEY (shift_risk_log_id) REFERENCES shift_risk_log(id) ON DELETE CASCADE,
    CONSTRAINT fk_src_station FOREIGN KEY (station_id) REFERENCES stations(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 13. Reporting views (§20 Reports, §9 Bottleneck, §11 Classification, §15 Pacemaker)
--     Views only ever read from the tables above - no data duplication.
-- ---------------------------------------------------------------------------
CREATE VIEW v_efficiency_classification AS
SELECT
    pe.*,
    CASE
        WHEN pe.efficiency_pct < 50  THEN 'CRITICAL'
        WHEN pe.efficiency_pct < 85  THEN 'LOW'
        WHEN pe.efficiency_pct < 100 THEN 'MARGINAL'
        WHEN pe.efficiency_pct < 150 THEN 'GOOD'
        ELSE 'EXCELLENT'
    END AS classification
FROM production_entries pe;

CREATE VIEW v_bottleneck_analysis AS
SELECT
    s.id AS station_id,
    s.station_code,
    s.station_name,
    sm.sam_minutes,
    RANK() OVER (ORDER BY sm.sam_minutes DESC) AS bottleneck_rank
FROM stations s
JOIN operations o ON o.station_id = s.id
JOIN sam_master sm ON sm.operation_id = o.id AND sm.status = 'APPROVED';

CREATE VIEW v_pacemaker_candidates AS
SELECT * FROM v_efficiency_classification WHERE efficiency_pct >= 150;

CREATE VIEW v_daily_stock_position_board AS
SELECT
    dsp.*,
    s.station_name,
    z.zone_code,
    z.zone_name
FROM daily_stock_position dsp
JOIN stations s ON s.id = dsp.station_id
JOIN zones z ON z.zone_code = s.zone_code;
