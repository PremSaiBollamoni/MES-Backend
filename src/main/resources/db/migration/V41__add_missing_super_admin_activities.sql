-- V36 seeded SUPER_ADMIN without SPLASH/LOGIN/DASHBOARD/PROFILE - every other role has
-- these, and without DASHBOARD specifically, the app's tab-gating logic (same rule
-- every role follows) hid the Dashboard tab entirely for SUPER_ADMIN.
UPDATE roles
SET activities = JSON_ARRAY(
    'SPLASH', 'LOGIN', 'DASHBOARD', 'PROFILE',
    'FACTORY_MANAGEMENT', 'ROLE_CONFIGURATION', 'HRMS_CONFIGURATION',
    'EMPLOYEE_MANAGEMENT', 'EMPLOYEE_LIST', 'EMPLOYEE_DETAIL', 'EMPLOYEE_FORM',
    'ROLE_LIST', 'ROLE_DETAIL')
WHERE role_name = 'SUPER_ADMIN';
