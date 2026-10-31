-- Single-row SMTP configuration, editable from the SUPER_ADMIN settings screen. The password
-- is encrypted by the application (EncryptedStringConverter) before it reaches this column.
-- Until someone saves real settings the mail sender falls back to the MAIL_* environment
-- variables, so no secret is ever seeded here.
CREATE TABLE mail_settings (
    id BIGINT PRIMARY KEY,
    smtp_host VARCHAR(150) NOT NULL,
    smtp_port INT NOT NULL,
    smtp_username VARCHAR(150),
    smtp_password VARCHAR(500),
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
INSERT INTO mail_settings (id, smtp_host, smtp_port) VALUES (1, 'smtp.gmail.com', 587);
