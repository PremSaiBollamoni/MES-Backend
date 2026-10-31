package com.antigravity.mes.ai;

import jakarta.persistence.*;

/** Which provider and model a feature uses. One row per {@link AiFeature}. */
@Entity
@Table(name = "ai_feature_config")
public class AiFeatureConfig {

    @Id
    @Column(length = 20)
    private String feature;

    @ManyToOne
    @JoinColumn(name = "model_id")
    private AiModel model;

    public AiFeatureConfig() {}

    public AiFeatureConfig(String feature) {
        this.feature = feature;
    }

    public String getFeature() { return feature; }

    @ManyToOne
    @JoinColumn(name = "fallback_model_id")
    private AiModel fallbackModel;

    public AiModel getModel() { return model; }
    public void setModel(AiModel model) { this.model = model; }

    public AiModel getFallbackModel() { return fallbackModel; }
    public void setFallbackModel(AiModel fallbackModel) { this.fallbackModel = fallbackModel; }
}
