-- Add factory_id to employees table with backward compatibility
ALTER TABLE employees ADD COLUMN factory_id BIGINT NULL AFTER id;

-- Backfill all existing employees with the default factory (factory_id = 1)
UPDATE employees SET factory_id = 1 WHERE factory_id IS NULL;

-- Make factory_id NOT NULL after backfill
ALTER TABLE employees MODIFY COLUMN factory_id BIGINT NOT NULL;

-- Add foreign key constraint
ALTER TABLE employees ADD CONSTRAINT fk_employees_factory
    FOREIGN KEY (factory_id) REFERENCES factories(factory_id) ON DELETE RESTRICT;

-- Drop the global username unique constraint
ALTER TABLE employees DROP INDEX `uq_employees_username`;

-- Add factory-scoped unique constraint (same username allowed in different factories)
ALTER TABLE employees ADD CONSTRAINT uq_employees_factory_username
    UNIQUE (factory_id, username);

-- Create index for faster lookups by factory
CREATE INDEX idx_employees_factory_id ON employees(factory_id);
