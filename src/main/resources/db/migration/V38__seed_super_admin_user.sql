-- Seed the first SUPER_ADMIN user
-- This user can manage all factories, users, and roles
-- Default credentials: username=SUPERADMIN, password=MES2K26 (same as other demo employees)
-- NOTE: Change these credentials immediately in production!

-- Insert SUPER_ADMIN user (factory_id is still required in employees table, using 1 as placeholder)
INSERT INTO employees (factory_id, username, password_hash, name, is_active, failed_login_attempts)
VALUES (
    1,
    'SUPERADMIN',
    -- This is the bcrypt hash of 'MES2K26' with spring security defaults
    -- To update: use PasswordEncoder.encode("MES2K26") in your Spring app
    '$2a$10$5eJC13YkadmJuxo6U/JJeOzmqJQmJykAAjoM0z6sWov7BOZxVcK7y',
    'Super Admin',
    TRUE,
    0
);

-- Get the super admin user ID (should be last insert)
SET @super_admin_id = LAST_INSERT_ID();

-- Assign SUPER_ADMIN role to this user
INSERT INTO employee_roles (employee_id, role_id)
SELECT @super_admin_id, role_id FROM roles WHERE role_name = 'SUPER_ADMIN';

-- Create an empty employee_info record for the super admin (required for HR endpoints)
INSERT INTO employee_info (factory_id, employee_id, employee_code, department, designation, date_of_joining)
VALUES (
    1,
    @super_admin_id,
    'SUPERADMIN',
    'Administration',
    'Super Admin',
    CURDATE()
);
