-- Multi-operator stations (e.g. Fusing, 2 operators) share ONE tracking session and one
-- shared piece count/timer, with wages split per station.operatorCount at read time.
CREATE TABLE tracking_session_employees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tracking_session_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    CONSTRAINT uq_tse_session_employee UNIQUE (tracking_session_id, employee_id),
    CONSTRAINT fk_tse_session FOREIGN KEY (tracking_session_id) REFERENCES tracking_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_tse_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);

INSERT INTO tracking_session_employees (tracking_session_id, employee_id)
SELECT id, employee_id FROM tracking_sessions WHERE employee_id IS NOT NULL;

-- The FK must be dropped before the index it depends on, or MySQL refuses the DROP INDEX.
ALTER TABLE tracking_sessions DROP FOREIGN KEY fk_tracking_session_employee;
DROP INDEX idx_tracking_sessions_employee_status ON tracking_sessions;
ALTER TABLE tracking_sessions DROP COLUMN employee_id;
