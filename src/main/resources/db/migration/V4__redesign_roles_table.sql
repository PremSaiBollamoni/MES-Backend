-- =====================================================================================
-- Redesign `roles` into role_id / role_name / activities / status.
--
-- `activities` is a JSON array of activity (screen) codes - one code per Flutter
-- screen under frontend/lib/activities/ (SPLASH, LOGIN, HOME, EMPLOYEE_LIST,
-- EMPLOYEE_DETAIL, EMPLOYEE_FORM, ...). A role may only reach the activities listed
-- here; RoleAuthorizationInterceptor enforces this server-side on every annotated
-- endpoint, and the Flutter app uses the same list (returned at login) to decide
-- which module tiles to show. `status` lets a role be disabled without deleting it -
-- employees holding a disabled role are denied login (see EmployeeService).
-- =====================================================================================

-- 1. Give roles a surrogate numeric identity (role_code stays the PK for now).
ALTER TABLE roles ADD COLUMN role_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE FIRST;

-- 2. Point employees at role_id instead of role_code: add + backfill + swap the FK.
ALTER TABLE employees ADD COLUMN role_id BIGINT NULL AFTER role_code;
UPDATE employees e JOIN roles r ON r.role_code = e.role_code SET e.role_id = r.role_id;
ALTER TABLE employees MODIFY COLUMN role_id BIGINT NOT NULL;
ALTER TABLE employees DROP FOREIGN KEY fk_employees_role;
ALTER TABLE employees DROP COLUMN role_code;

-- 3. Reshape roles: role_id becomes PK, role_code becomes role_name, description is
--    replaced by activities + status.
ALTER TABLE roles DROP PRIMARY KEY;
ALTER TABLE roles ADD PRIMARY KEY (role_id);
ALTER TABLE roles CHANGE COLUMN role_code role_name VARCHAR(50) NOT NULL;
ALTER TABLE roles ADD CONSTRAINT uq_roles_name UNIQUE (role_name);
ALTER TABLE roles DROP COLUMN description;
ALTER TABLE roles ADD COLUMN activities JSON NULL AFTER role_name;
ALTER TABLE roles ADD COLUMN status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE' AFTER activities;

-- 4. Re-attach the employees FK, now against role_id.
ALTER TABLE employees ADD CONSTRAINT fk_employees_role FOREIGN KEY (role_id) REFERENCES roles(role_id);

-- 5. Populate each role's allowed activities.
UPDATE roles SET activities = JSON_ARRAY('SPLASH', 'LOGIN', 'HOME')
    WHERE role_name IN ('FACTORY_MANAGER', 'PRODUCTION_SUPERVISOR', 'IE_ANALYST', 'OPERATOR', 'QC_INSPECTOR', 'STORE_KEEPER');
UPDATE roles SET activities = JSON_ARRAY('SPLASH', 'LOGIN', 'HOME', 'EMPLOYEE_LIST', 'EMPLOYEE_DETAIL', 'EMPLOYEE_FORM')
    WHERE role_name = 'HR_MANAGER';

ALTER TABLE roles MODIFY COLUMN activities JSON NOT NULL;
