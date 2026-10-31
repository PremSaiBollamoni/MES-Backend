-- Saved recipient list for every automated and manual email. Each recipient opts in per
-- report type, so payroll can go to one group while daily attendance goes to another.
CREATE TABLE mail_recipients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(150) NOT NULL,
    name VARCHAR(100),
    receives_payroll BOOLEAN NOT NULL DEFAULT TRUE,
    receives_attendance BOOLEAN NOT NULL DEFAULT TRUE,
    receives_production BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_mail_recipients_email (email)
);
