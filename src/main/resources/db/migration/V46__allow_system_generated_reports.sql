-- Scheduled daily report emails generate reports without a logged-in author, so the
-- author column must accept NULL (system-generated). Existing rows keep their authors.
ALTER TABLE generated_reports MODIFY generated_by_employee_id BIGINT NULL;
