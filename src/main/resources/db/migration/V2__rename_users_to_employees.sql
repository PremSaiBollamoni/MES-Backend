-- =====================================================================================
-- Rename `users` -> `employees` and add `employee_info` (HR/personal/emergency-contact
-- master data, 1:1 with employees). The auth-critical columns (mobile number, password,
-- role, lockout state) stay on `employees` since they gate login; `employee_info` holds
-- everything else so auth logic never has to touch HR data and vice versa.
--
-- Sensitive personal fields here follow the same encryption pattern as
-- employees.mobile_number_encrypted: AES-256-GCM ciphertext at rest via
-- EncryptedStringConverter. Operational fields (department, designation, shift, DOB,
-- gender, blood group) stay plaintext since they're needed for filtering/reporting and
-- aren't uniquely identifying on their own.
-- =====================================================================================

RENAME TABLE users TO employees;

ALTER TABLE employees RENAME INDEX uq_users_mobile_hash TO uq_employees_mobile_hash;
ALTER TABLE employees DROP FOREIGN KEY fk_users_role;
ALTER TABLE employees ADD CONSTRAINT fk_employees_role FOREIGN KEY (role_code) REFERENCES roles(role_code);

-- login_audit.user_id -> employee_id
ALTER TABLE login_audit DROP FOREIGN KEY fk_login_audit_user;
ALTER TABLE login_audit CHANGE COLUMN user_id employee_id BIGINT NULL;
ALTER TABLE login_audit ADD CONSTRAINT fk_login_audit_employee
    FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE SET NULL;

-- audit_log.changed_by keeps its name (role-neutral already) but now points at employees
ALTER TABLE audit_log DROP FOREIGN KEY fk_audit_log_user;
ALTER TABLE audit_log ADD CONSTRAINT fk_audit_log_employee
    FOREIGN KEY (changed_by) REFERENCES employees(id) ON DELETE SET NULL;

CREATE TABLE employee_info (
    id                                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id                         BIGINT       NOT NULL,
    employee_code                       VARCHAR(30)  NOT NULL COMMENT 'Badge / employee ID number',
    department                          VARCHAR(100) NULL,
    designation                         VARCHAR(100) NULL,
    date_of_joining                     DATE         NULL,
    shift                               VARCHAR(20)  NOT NULL DEFAULT 'GENERAL',
    date_of_birth                       DATE         NULL,
    gender                              ENUM('MALE','FEMALE','OTHER') NULL,
    blood_group                         VARCHAR(5)   NULL,
    address_encrypted                   VARCHAR(1000) NULL COMMENT 'AES-256-GCM ciphertext (base64)',
    emergency_contact_name              VARCHAR(150) NULL,
    emergency_contact_number_encrypted  VARCHAR(255) NULL COMMENT 'AES-256-GCM ciphertext (base64)',
    emergency_contact_relationship      VARCHAR(50)  NULL,
    created_at                          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_employee_info_employee UNIQUE (employee_id),
    CONSTRAINT uq_employee_info_code UNIQUE (employee_code),
    CONSTRAINT fk_employee_info_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
) ENGINE=InnoDB;
