CREATE TABLE attendance_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    employee_name VARCHAR(150) NOT NULL,
    employee_code VARCHAR(30) NOT NULL,
    department_name VARCHAR(100),
    work_date DATE NOT NULL,
    first_in VARCHAR(20),
    last_out VARCHAR(20),
    total_hours DOUBLE,
    status VARCHAR(30),
    is_late BOOLEAN NOT NULL DEFAULT FALSE,
    late_minutes INT,
    synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT uq_attendance_employee_date UNIQUE (employee_id, work_date)
);

CREATE INDEX idx_attendance_work_date ON attendance_records (work_date);
