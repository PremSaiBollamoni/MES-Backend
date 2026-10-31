-- Create SUPER_ADMIN role as a system-wide role (factory_id = NULL)
-- This role can manage all factories and users
INSERT INTO roles (role_name, activities, status, factory_id)
VALUES ('SUPER_ADMIN', '["FACTORY_MANAGEMENT", "ROLE_CONFIGURATION", "HRMS_CONFIGURATION", "EMPLOYEE_MANAGEMENT", "EMPLOYEE_LIST", "EMPLOYEE_DETAIL", "EMPLOYEE_FORM", "ROLE_LIST", "ROLE_DETAIL"]', 'ACTIVE', NULL);
