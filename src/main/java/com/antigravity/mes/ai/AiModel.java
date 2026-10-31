package com.antigravity.mes.ai;

import jakarta.persistence.*;

/** A model name under one provider, e.g. "llama-3.3-70b-versatile" under Groq. */
@Entity
@Table(name = "ai_models")
public class AiModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private AiProvider provider;

    @Column(name = "model_name", nullable = false, length = 150)
    private String modelName;

    @Column(length = 150)
    private String label;

    @Column(nullable = false)
    private boolean enabled = true;

    public Long getId() { return id; }

    public AiProvider getProvider() { return provider; }
    public void setProvider(AiProvider provider) { this.provider = provider; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
