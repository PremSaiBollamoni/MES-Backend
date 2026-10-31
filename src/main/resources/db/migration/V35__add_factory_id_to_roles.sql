-- Add factory_id to roles table (NULL = system-wide roles like SUPER_ADMIN)
ALTER TABLE roles ADD COLUMN factory_id BIGINT NULL AFTER role_id;

-- Backfill existing roles to default factory
-- Exclude SUPER_ADMIN if it exists
UPDATE roles SET factory_id = 1 WHERE factory_id IS NULL AND role_name NOT IN ('SUPER_ADMIN');

-- Add foreign key constraint for non-NULL factory_id
ALTER TABLE roles ADD CONSTRAINT fk_roles_factory
    FOREIGN KEY (factory_id) REFERENCES factories(factory_id) ON DELETE CASCADE;

-- Drop the global role_name unique constraint
ALTER TABLE roles DROP INDEX `uq_roles_name`;

-- Add factory-scoped unique constraint
-- system roles (factory_id=NULL) can coexist with factory-specific roles of same name
ALTER TABLE roles ADD CONSTRAINT uq_roles_factory_name
    UNIQUE (factory_id, role_name);

-- Create index for faster lookups by factory
CREATE INDEX idx_roles_factory_id ON roles(factory_id);
