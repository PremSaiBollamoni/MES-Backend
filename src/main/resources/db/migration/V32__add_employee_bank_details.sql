ALTER TABLE employee_info
    ADD COLUMN bank_name VARCHAR(100) NULL AFTER emergency_contact_relationship,
    ADD COLUMN bank_account_number_encrypted VARCHAR(255) NULL AFTER bank_name,
    ADD COLUMN ifsc_code VARCHAR(20) NULL AFTER bank_account_number_encrypted;
