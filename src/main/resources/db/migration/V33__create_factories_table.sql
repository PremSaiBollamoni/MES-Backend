CREATE TABLE factories (
    factory_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    factory_name VARCHAR(150) NOT NULL UNIQUE,
    factory_code VARCHAR(30) NOT NULL UNIQUE,
    location VARCHAR(255) NULL,
    hrms_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    hrms_config_json JSON NULL COMMENT 'HRMS endpoint and credentials config per factory',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_factories_active (is_active)
) ENGINE=InnoDB;

-- Insert default factory for backward compatibility with existing data
INSERT INTO factories (factory_name, factory_code, location, is_active)
VALUES ('Default Factory', 'DEFAULT', 'Default Location', TRUE);
