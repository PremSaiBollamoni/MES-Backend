package com.antigravity.mes.ai;

import com.antigravity.mes.security.EncryptedStringConverter;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/** An AI service the app can call. The API key is encrypted at rest and is never sent back to the app. */
@Entity
@Table(name = "ai_providers")
public class AiProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 30)
    private AiProviderType providerType;

    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "api_key", length = 500)
    private String apiKey;

    @Column(nullable = false)
    private boolean enabled = true;

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<AiModel> models = new ArrayList<>();

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public AiProviderType getProviderType() { return providerType; }
    public void setProviderType(AiProviderType providerType) { this.providerType = providerType; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<AiModel> getModels() { return models; }
}
