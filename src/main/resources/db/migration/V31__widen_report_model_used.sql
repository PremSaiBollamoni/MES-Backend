-- model_used now sometimes holds a freely-typed OpenRouter model id (report generation
-- screen), not just the fixed Claude model name - some model slugs run past 50 chars.
ALTER TABLE generated_reports
    MODIFY COLUMN model_used VARCHAR(100) NOT NULL;
