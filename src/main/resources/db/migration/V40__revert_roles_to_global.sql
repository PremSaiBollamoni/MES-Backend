-- Roles are shared globally across all factories, not duplicated per factory - if a
-- factory needs different permissions for a role, that role is edited directly (which
-- affects everyone using it). Only SUPER_ADMIN is special (a system-wide login, not a
-- factory role). This reverts the per-factory role duplication introduced by
-- V35/V36 after finding it created confusing duplicate role rows in practice.

-- Re-point any employee holding a factory-specific duplicate role (created for
-- factory_id != 1 with the same name as an existing global role) onto that global
-- role instead, then remove the now-unused duplicates. Ignores duplicate-key errors
-- from an employee already also holding the target global role.
INSERT IGNORE INTO employee_roles (employee_id, role_id)
SELECT er.employee_id, r1.role_id
FROM employee_roles er
JOIN roles r2 ON er.role_id = r2.role_id AND r2.factory_id IS NOT NULL AND r2.factory_id != 1
JOIN roles r1 ON r1.role_name = r2.role_name AND r1.factory_id = 1;

DELETE er FROM employee_roles er
JOIN roles r2 ON er.role_id = r2.role_id
WHERE r2.factory_id IS NOT NULL AND r2.factory_id != 1;

DELETE FROM roles WHERE factory_id IS NOT NULL AND factory_id != 1;

-- Drop the factory-scoped unique constraint and the factory_id column's role in
-- identity - roles are global again, so role_name alone must be unique.
ALTER TABLE roles DROP INDEX `uq_roles_factory_name`;
ALTER TABLE roles ADD CONSTRAINT uq_roles_name UNIQUE (role_name);

-- Clear factory_id on every remaining role (including the former factory_id=1 rows) -
-- NULL now uniformly means "not tied to one factory", matching SUPER_ADMIN's existing
-- NULL. Role identity going forward is by name alone.
UPDATE roles SET factory_id = NULL WHERE role_name != 'SUPER_ADMIN';
