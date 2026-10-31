-- Usernames are globally unique by policy (never reused across factories) - login
-- resolves an employee's factory from their own record, not from a caller-supplied
-- factory selection. Enforce that policy at the database level too, not just in
-- application code, so a bug or a direct insert can never create a collision.
ALTER TABLE employees ADD CONSTRAINT uq_employees_username_global UNIQUE (username);
