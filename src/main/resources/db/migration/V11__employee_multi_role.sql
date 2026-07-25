-- An employee can now hold more than one role (e.g. someone who is both a
-- Production Supervisor and a QC Inspector). Replaces the single employees.role_id
-- FK with a many-to-many join table.

CREATE TABLE employee_roles (
    employee_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (employee_id, role_id),
    CONSTRAINT fk_employee_roles_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_employee_roles_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE
);

-- Carry every existing single-role assignment over before the old column is dropped.
INSERT INTO employee_roles (employee_id, role_id)
SELECT id, role_id FROM employees WHERE role_id IS NOT NULL;

ALTER TABLE employees DROP FOREIGN KEY fk_employees_role;
ALTER TABLE employees DROP COLUMN role_id;
