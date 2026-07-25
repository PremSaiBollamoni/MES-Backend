-- Drops the speculative full-PALMS schema seeded by V1__init_schema.sql before the
-- project pivoted to the current employees/roles architecture. None of these tables,
-- or the 4 reporting views built on them, are referenced by any JPA entity, repository,
-- or controller anywhere in the codebase - they've been empty, inert scaffolding since
-- day one. (This is also why the real Phase 1 stations table had to be named
-- sam_stations instead of stations - V1's `stations` already occupied that name.)
--
-- Explicitly NOT touched - these are the real, live tables the app actually reads and
-- writes: roles, employees, employee_info, login_audit, attendance_records, sam_stations.

DROP VIEW IF EXISTS v_daily_stock_position_board;
DROP VIEW IF EXISTS v_pacemaker_candidates;
DROP VIEW IF EXISTS v_bottleneck_analysis;
DROP VIEW IF EXISTS v_efficiency_classification;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS shift_risk_contributors;
DROP TABLE IF EXISTS shift_risk_log;
DROP TABLE IF EXISTS pacemaker_registry;
DROP TABLE IF EXISTS method_study_work_orders;
DROP TABLE IF EXISTS rtm_daily_report;
DROP TABLE IF EXISTS rtm_stock_ledger;
DROP TABLE IF EXISTS qc_defects;
DROP TABLE IF EXISTS packing_summary;
DROP TABLE IF EXISTS packing_operation_log;
DROP TABLE IF EXISTS washing_batches;
DROP TABLE IF EXISTS daily_stock_position;
DROP TABLE IF EXISTS stock_availability;
DROP TABLE IF EXISTS attendance;
DROP TABLE IF EXISTS production_entries;
DROP TABLE IF EXISTS bundle_movements;
DROP TABLE IF EXISTS bundles;
DROP TABLE IF EXISTS time_study_observations;
DROP TABLE IF EXISTS sam_master;
DROP TABLE IF EXISTS operations;
DROP TABLE IF EXISTS stations;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS zones;
DROP TABLE IF EXISTS styles;
DROP TABLE IF EXISTS buyers;
DROP TABLE IF EXISTS audit_log;

SET FOREIGN_KEY_CHECKS = 1;
