package com.antigravity.mes.ai;

import com.antigravity.mes.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Manages the AI providers, the models under each provider, and which model each feature uses.
 * Features call {@link #complete} and never pick a provider themselves. A transient failure is
 * retried on the same model, then the feature's fallback model is tried. */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    public static final String OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1";

    private static final String TEST_PROMPT = "Reply with the word OK.";

    /** Waits before each retry of the same model. Two retries, so three tries in total. */
    private static final long[] RETRY_DELAYS_MS = {2_000, 5_000};

    /** Fallback key for the seeded OpenRouter provider, so an existing server-side key keeps working until one is saved in the app. */
    @Value("${app.openrouter.api-key:}")
    private String fallbackOpenRouterKey;

    @Autowired
    private AiProviderRepository providerRepository;

    @Autowired
    private AiModelRepository modelRepository;

    @Autowired
    private AiFeatureConfigRepository featureRepository;

    @Autowired
    private AiCompletionClient completionClient;

    public record ModelView(Long id, String modelName, String label, boolean enabled) {}

    public record ProviderView(Long id, String name, AiProviderType providerType, String baseUrl,
                               boolean enabled, boolean keySet, List<ModelView> models) {}

    /** Null fields mean nothing is chosen. The fallback fields describe the model used when the main one fails. */
    public record FeatureView(AiFeature feature,
                              Long modelId, Long providerId, String providerName, String modelName,
                              Long fallbackModelId, String fallbackProviderName, String fallbackModelName) {}

    /** The reply text and the model that actually produced it - which can be the fallback. */
    public record AiReply(String text, String model) {}

    /** Everything a call needs, resolved from a model. */
    private record Target(AiProviderType type, String baseUrl, String apiKey, String model) {}

    @Transactional(readOnly = true)
    public List<ProviderView> listProviders() {
        return providerRepository.findAll(Sort.by("id")).stream().map(this::toView).toList();
    }

    @Transactional
    public ProviderView createProvider(String name, AiProviderType type, String baseUrl, String apiKey, boolean enabled) {
        String cleanName = requireText(name, "Provider name is required");
        if (type == null) throw new IllegalArgumentException("Provider type is required");
        if (providerRepository.existsByName(cleanName)) {
            throw new IllegalArgumentException("A provider named " + cleanName + " already exists");
        }
        AiProvider provider = new AiProvider();
        provider.setName(cleanName);
        provider.setProviderType(type);
        provider.setBaseUrl(requireBaseUrl(baseUrl));
        provider.setApiKey(blankToNull(apiKey));
        provider.setEnabled(enabled);
        return toView(providerRepository.save(provider));
    }

    /** A blank {@code apiKey} keeps the key already saved - the key is never shown back to the app. */
    @Transactional
    public ProviderView updateProvider(Long id, String name, AiProviderType type, String baseUrl, String apiKey, boolean enabled) {
        AiProvider provider = findProvider(id);
        String cleanName = requireText(name, "Provider name is required");
        if (type == null) throw new IllegalArgumentException("Provider type is required");
        if (providerRepository.existsByNameAndIdNot(cleanName, id)) {
            throw new IllegalArgumentException("A provider named " + cleanName + " already exists");
        }
        provider.setName(cleanName);
        provider.setProviderType(type);
        provider.setBaseUrl(requireBaseUrl(baseUrl));
        if (blankToNull(apiKey) != null) provider.setApiKey(apiKey.trim());
        provider.setEnabled(enabled);
        return toView(provider);
    }

    @Transactional
    public void deleteProvider(Long id) {
        providerRepository.delete(findProvider(id));
    }

    @Transactional
    public ModelView addModel(Long providerId, String modelName, String label) {
        AiProvider provider = findProvider(providerId);
        String cleanModel = requireText(modelName, "Model name is required");
        if (modelRepository.existsByProviderIdAndModelName(providerId, cleanModel)) {
            throw new IllegalArgumentException("That model is already added to " + provider.getName());
        }
        AiModel model = new AiModel();
        model.setProvider(provider);
        model.setModelName(cleanModel);
        model.setLabel(blankToNull(label));
        model.setEnabled(true);
        return toModelView(modelRepository.save(model));
    }

    @Transactional
    public ModelView updateModel(Long id, String label, boolean enabled) {
        AiModel model = findModel(id);
        model.setLabel(blankToNull(label));
        model.setEnabled(enabled);
        return toModelView(model);
    }

    @Transactional
    public void deleteModel(Long id) {
        modelRepository.delete(findModel(id));
    }

    @Transactional(readOnly = true)
    public List<FeatureView> listFeatures() {
        return Arrays.stream(AiFeature.values()).map(this::featureView).toList();
    }

    /** Null {@code modelId} clears the feature. Null {@code fallbackModelId} means no fallback. */
    @Transactional
    public FeatureView assignFeature(AiFeature feature, Long modelId, Long fallbackModelId) {
        AiFeatureConfig config = featureRepository.findById(feature.name())
                .orElseGet(() -> new AiFeatureConfig(feature.name()));
        AiModel model = modelId == null ? null : enabledModel(modelId);
        AiModel fallback = fallbackModelId == null ? null : enabledModel(fallbackModelId);
        if (model != null && fallback != null && model.getId().equals(fallback.getId())) {
            throw new IllegalArgumentException("The fallback must be a different model");
        }
        config.setModel(model);
        config.setFallbackModel(fallback);
        featureRepository.save(config);
        return featureView(feature);
    }

    /** Sends a short request with the saved key, so a model can be checked right after it is added. */
    @Transactional(readOnly = true)
    public String testModel(Long modelId) throws Exception {
        AiModel model = findModel(modelId);
        AiProvider provider = model.getProvider();
        String key = effectiveKey(provider);
        if (key == null) {
            throw new IllegalArgumentException("No API key saved for " + provider.getName() + " - save one first");
        }
        String reply = completionClient.complete(provider.getProviderType(), provider.getBaseUrl(), key, model.getModelName(),
                null, List.of(Map.of("role", "user", "content", TEST_PROMPT)), 300);
        String trimmed = reply.trim();
        return "Model replied: " + (trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed);
    }

    /** True when the feature has at least one usable model (its own, or its fallback). */
    public boolean isConfigured(AiFeature feature) {
        return !resolveChain(feature).isEmpty();
    }

    public String complete(AiFeature feature, String system, List<Map<String, String>> messages, int maxTokens) throws Exception {
        return completeWithModel(feature, system, messages, maxTokens).text();
    }

    /** Tries the feature's model, retrying transient failures, then its fallback model. Stops at once if the key is rejected. */
    public AiReply completeWithModel(AiFeature feature, String system, List<Map<String, String>> messages, int maxTokens) throws Exception {
        List<Target> chain = resolveChain(feature);
        if (chain.isEmpty()) {
            throw new IllegalStateException("AI " + feature.name().toLowerCase() + " is not set up - choose an enabled model with an API key in AI settings");
        }
        AiProviderException last = null;
        for (Target target : chain) {
            try {
                String text = callWithRetry(target, system, messages, maxTokens);
                return new AiReply(text, target.model());
            } catch (AiProviderException e) {
                if (e.isKeyRejected()) throw e;
                log.warn("AI {} model {} failed, trying the next model: {}", feature, target.model(), e.getMessage());
                last = e;
            }
        }
        throw last;
    }

    private String callWithRetry(Target target, String system, List<Map<String, String>> messages, int maxTokens) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try {
                return completionClient.complete(target.type(), target.baseUrl(), target.apiKey(), target.model(), system, messages, maxTokens);
            } catch (AiProviderException e) {
                if (!e.isTransient() || attempt >= RETRY_DELAYS_MS.length) throw e;
                Thread.sleep(RETRY_DELAYS_MS[attempt]);
            }
        }
    }

    /** The feature's model first, then its fallback. Models that are disabled or have no key are skipped. */
    private List<Target> resolveChain(AiFeature feature) {
        AiFeatureConfig config = featureRepository.findById(feature.name()).orElse(null);
        List<Target> chain = new ArrayList<>();
        if (config == null) return chain;
        targetFor(config.getModel()).ifPresent(chain::add);
        AiModel fallback = config.getFallbackModel();
        boolean sameAsMain = config.getModel() != null && fallback != null && config.getModel().getId().equals(fallback.getId());
        if (!sameAsMain) targetFor(fallback).ifPresent(chain::add);
        return chain;
    }

    private Optional<Target> targetFor(AiModel model) {
        if (model == null || !model.isEnabled() || !model.getProvider().isEnabled()) return Optional.empty();
        AiProvider provider = model.getProvider();
        String key = effectiveKey(provider);
        if (key == null) return Optional.empty();
        return Optional.of(new Target(provider.getProviderType(), provider.getBaseUrl(), key, model.getModelName()));
    }

    private String effectiveKey(AiProvider provider) {
        if (blankToNull(provider.getApiKey()) != null) return provider.getApiKey();
        boolean openRouter = provider.getProviderType() == AiProviderType.OPENAI_COMPATIBLE
                && OPENROUTER_BASE_URL.equals(stripSlash(provider.getBaseUrl()));
        return openRouter && blankToNull(fallbackOpenRouterKey) != null ? fallbackOpenRouterKey : null;
    }

    private FeatureView featureView(AiFeature feature) {
        AiFeatureConfig config = featureRepository.findById(feature.name()).orElse(null);
        AiModel model = config == null ? null : config.getModel();
        AiModel fallback = config == null ? null : config.getFallbackModel();
        return new FeatureView(
                feature,
                model == null ? null : model.getId(),
                model == null ? null : model.getProvider().getId(),
                model == null ? null : model.getProvider().getName(),
                model == null ? null : model.getModelName(),
                fallback == null ? null : fallback.getId(),
                fallback == null ? null : fallback.getProvider().getName(),
                fallback == null ? null : fallback.getModelName());
    }

    private ProviderView toView(AiProvider provider) {
        return new ProviderView(
                provider.getId(),
                provider.getName(),
                provider.getProviderType(),
                provider.getBaseUrl(),
                provider.isEnabled(),
                effectiveKey(provider) != null,
                provider.getModels().stream().map(this::toModelView).toList());
    }

    private ModelView toModelView(AiModel model) {
        return new ModelView(model.getId(), model.getModelName(), model.getLabel(), model.isEnabled());
    }

    private AiProvider findProvider(Long id) {
        return providerRepository.findById(id).orElseThrow(() -> new NotFoundException("AI provider not found: " + id));
    }

    private AiModel findModel(Long id) {
        return modelRepository.findById(id).orElseThrow(() -> new NotFoundException("AI model not found: " + id));
    }

    private AiModel enabledModel(Long id) {
        AiModel model = findModel(id);
        if (!model.isEnabled()) throw new IllegalArgumentException("Enable " + model.getModelName() + " before using it");
        return model;
    }

    private static String requireText(String value, String message) {
        String cleaned = blankToNull(value);
        if (cleaned == null) throw new IllegalArgumentException(message);
        return cleaned;
    }

    private static String requireBaseUrl(String baseUrl) {
        String cleaned = stripSlash(blankToNull(baseUrl));
        if (cleaned == null || !(cleaned.startsWith("https://") || cleaned.startsWith("http://"))) {
            throw new IllegalArgumentException("Base URL must start with https:// or http://");
        }
        return cleaned;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String stripSlash(String url) {
        if (url == null) return null;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
