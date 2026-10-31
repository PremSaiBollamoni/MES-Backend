-- Replaces the single-row AI settings with a list of providers, the models under each provider,
-- and one chosen model for each feature (chat assistant, daily reports). API keys are encrypted
-- by the application before they are stored. OpenRouter is seeded with no key, so the server's
-- existing app.openrouter.api-key keeps working until a key is saved in the app.
DROP TABLE IF EXISTS ai_settings;

CREATE TABLE ai_providers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    provider_type VARCHAR(30) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai_models (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    model_name VARCHAR(150) NOT NULL,
    label VARCHAR(150),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_models_provider FOREIGN KEY (provider_id) REFERENCES ai_providers(id) ON DELETE CASCADE,
    CONSTRAINT uq_ai_models_provider_name UNIQUE (provider_id, model_name)
);

CREATE TABLE ai_feature_config (
    feature VARCHAR(20) PRIMARY KEY,
    model_id BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_feature_model FOREIGN KEY (model_id) REFERENCES ai_models(id) ON DELETE SET NULL
);

INSERT INTO ai_providers (name, provider_type, base_url, enabled)
VALUES ('OpenRouter', 'OPENAI_COMPATIBLE', 'https://openrouter.ai/api/v1', TRUE);

INSERT INTO ai_models (provider_id, model_name, label, enabled)
SELECT id, 'google/gemini-3.8-flash', 'Gemini 3.8 Flash', TRUE FROM ai_providers WHERE name = 'OpenRouter';

INSERT INTO ai_models (provider_id, model_name, label, enabled)
SELECT id, 'nvidia/nemotron-3-ultra-550b-a55b:free', 'Nemotron 3 Ultra (free)', TRUE FROM ai_providers WHERE name = 'OpenRouter';

INSERT INTO ai_feature_config (feature, model_id)
SELECT 'CHAT', m.id FROM ai_models m WHERE m.model_name = 'google/gemini-3.8-flash';

INSERT INTO ai_feature_config (feature, model_id)
SELECT 'REPORT', m.id FROM ai_models m WHERE m.model_name = 'nvidia/nemotron-3-ultra-550b-a55b:free';
