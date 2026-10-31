-- Single-row AI provider configuration, editable from the SUPER_ADMIN settings screen. The
-- OpenRouter key is encrypted by the application before it reaches this column. Until a key
-- is saved in the app, the server's app.openrouter.* settings are used, so no secret is seeded.
CREATE TABLE ai_settings (
    id BIGINT PRIMARY KEY,
    openrouter_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    openrouter_api_key VARCHAR(500),
    support_chat_model VARCHAR(150) NOT NULL,
    report_model VARCHAR(150) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
INSERT INTO ai_settings (id, openrouter_enabled, support_chat_model, report_model)
VALUES (1, FALSE, 'google/gemini-3.8-flash', 'nvidia/nemotron-3-ultra-550b-a55b:free');
