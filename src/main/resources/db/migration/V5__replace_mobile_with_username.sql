-- =====================================================================================
-- Replace mobile-number login with a system-generated username (MES001, MES002, ...).
-- Unlike a phone number, a username of this shape isn't personally identifying, so it
-- is stored and looked up in plain VARCHAR - no AES-GCM / HMAC blind-index needed here.
-- =====================================================================================

-- 1. Add username, backfill existing rows in id order, then lock it down.
ALTER TABLE employees ADD COLUMN username VARCHAR(20) NULL AFTER id;

SET @seq = 0;
UPDATE employees SET username = CONCAT('MES', LPAD(@seq := @seq + 1, 3, '0')) ORDER BY id;

ALTER TABLE employees MODIFY COLUMN username VARCHAR(20) NOT NULL;
ALTER TABLE employees ADD CONSTRAINT uq_employees_username UNIQUE (username);

-- 2. Drop the mobile-number columns (and their supporting unique index) entirely.
ALTER TABLE employees DROP COLUMN mobile_number_encrypted;
ALTER TABLE employees DROP COLUMN mobile_number_hash;

-- 3. login_audit tracked attempts by a blind-index hash of the mobile number; with a
--    non-sensitive username there's nothing to hash, so it just stores the username.
ALTER TABLE login_audit CHANGE COLUMN mobile_number_hash username VARCHAR(20) NOT NULL;
ALTER TABLE login_audit RENAME INDEX idx_login_audit_hash_time TO idx_login_audit_username_time;
