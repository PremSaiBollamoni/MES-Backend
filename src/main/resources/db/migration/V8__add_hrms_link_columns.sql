-- Links a local employee to its mirrored record in the HRMS biometric portal, so the
-- app can show the HRMS code / physical device enrollment ID and sync status without
-- an extra round trip to HRMS on every page load.
ALTER TABLE employee_info ADD COLUMN hrms_employee_id INT NULL AFTER employee_code;
ALTER TABLE employee_info ADD COLUMN hrms_employee_code VARCHAR(30) NULL AFTER hrms_employee_id;
ALTER TABLE employee_info ADD COLUMN hrms_device_user_id VARCHAR(20) NULL AFTER hrms_employee_code;
ALTER TABLE employee_info ADD COLUMN hrms_sync_status ENUM('NOT_SYNCED', 'SYNCED', 'FAILED') NOT NULL DEFAULT 'NOT_SYNCED' AFTER hrms_device_user_id;
ALTER TABLE employee_info ADD COLUMN hrms_synced_at TIMESTAMP NULL AFTER hrms_sync_status;
