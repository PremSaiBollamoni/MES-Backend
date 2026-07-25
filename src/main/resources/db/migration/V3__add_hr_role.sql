-- Adds a dedicated HR role. HR is the only role permitted to manage employee
-- records (see RoleAuthorizationInterceptor), separate from Factory Manager /
-- Production Supervisor who only view dashboards/reports per PALMS.md §5.
INSERT INTO roles (role_code, description) VALUES
    ('HR_MANAGER', 'Manages employee records, onboarding, and HR master data');
