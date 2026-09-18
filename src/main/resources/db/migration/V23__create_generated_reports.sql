-- AI-generated production reports (Claude). HTML is stored once and rendered to PDF
-- on-demand at download time, so re-downloading a report never re-calls the model.
CREATE TABLE generated_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_date DATE NOT NULL UNIQUE,
    generated_by_employee_id BIGINT NOT NULL,
    html_content LONGTEXT NOT NULL,
    model_used VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_generated_report_by FOREIGN KEY (generated_by_employee_id) REFERENCES employees(id)
);
