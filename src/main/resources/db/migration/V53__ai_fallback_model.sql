-- The model a feature switches to when its chosen model is unavailable (overloaded, rate limited,
-- removed, or too large for the account's limits). Optional - a feature without one just fails.
ALTER TABLE ai_feature_config
    ADD COLUMN fallback_model_id BIGINT NULL,
    ADD CONSTRAINT fk_ai_feature_fallback FOREIGN KEY (fallback_model_id) REFERENCES ai_models(id) ON DELETE SET NULL;
