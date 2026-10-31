package com.antigravity.mes.ai;

import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** SUPER_ADMIN-only AI configuration: providers, the models under each, and the model each
 * feature (chat assistant, reports) uses. The AI_CONFIGURATION activity is granted to no other role. */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiController {

    @Autowired
    private AiService aiService;

    public record ProviderRequest(String name, AiProviderType providerType, String baseUrl, String apiKey, Boolean enabled) {}
    public record ModelRequest(String modelName, String label) {}
    public record ModelUpdateRequest(String label, Boolean enabled) {}
    public record FeatureRequest(Long modelId, Long fallbackModelId) {}

    @RequiresActivity("AI_CONFIGURATION")
    @GetMapping("/providers")
    public ResponseEntity<List<AiService.ProviderView>> listProviders() {
        return ResponseEntity.ok(aiService.listProviders());
    }

    @RequiresActivity("AI_CONFIGURATION")
    @PostMapping("/providers")
    public ResponseEntity<AiService.ProviderView> createProvider(@RequestBody ProviderRequest request) {
        return ResponseEntity.ok(aiService.createProvider(request.name(), request.providerType(), request.baseUrl(),
                request.apiKey(), request.enabled() == null || request.enabled()));
    }

    @RequiresActivity("AI_CONFIGURATION")
    @PutMapping("/providers/{id}")
    public ResponseEntity<AiService.ProviderView> updateProvider(@PathVariable Long id, @RequestBody ProviderRequest request) {
        return ResponseEntity.ok(aiService.updateProvider(id, request.name(), request.providerType(), request.baseUrl(),
                request.apiKey(), request.enabled() == null || request.enabled()));
    }

    @RequiresActivity("AI_CONFIGURATION")
    @DeleteMapping("/providers/{id}")
    public ResponseEntity<Void> deleteProvider(@PathVariable Long id) {
        aiService.deleteProvider(id);
        return ResponseEntity.noContent().build();
    }

    @RequiresActivity("AI_CONFIGURATION")
    @PostMapping("/providers/{id}/models")
    public ResponseEntity<AiService.ModelView> addModel(@PathVariable Long id, @RequestBody ModelRequest request) {
        return ResponseEntity.ok(aiService.addModel(id, request.modelName(), request.label()));
    }

    @RequiresActivity("AI_CONFIGURATION")
    @PutMapping("/models/{id}")
    public ResponseEntity<AiService.ModelView> updateModel(@PathVariable Long id, @RequestBody ModelUpdateRequest request) {
        return ResponseEntity.ok(aiService.updateModel(id, request.label(), request.enabled() == null || request.enabled()));
    }

    @RequiresActivity("AI_CONFIGURATION")
    @DeleteMapping("/models/{id}")
    public ResponseEntity<Void> deleteModel(@PathVariable Long id) {
        aiService.deleteModel(id);
        return ResponseEntity.noContent().build();
    }

    @RequiresActivity("AI_CONFIGURATION")
    @PostMapping("/models/{id}/test")
    public ResponseEntity<Map<String, String>> testModel(@PathVariable Long id) throws Exception {
        return ResponseEntity.ok(Map.of("message", aiService.testModel(id)));
    }

    @RequiresActivity("AI_CONFIGURATION")
    @GetMapping("/features")
    public ResponseEntity<List<AiService.FeatureView>> listFeatures() {
        return ResponseEntity.ok(aiService.listFeatures());
    }

    @RequiresActivity("AI_CONFIGURATION")
    @PutMapping("/features/{feature}")
    public ResponseEntity<AiService.FeatureView> assignFeature(@PathVariable String feature, @RequestBody FeatureRequest request) {
        return ResponseEntity.ok(aiService.assignFeature(AiFeature.valueOf(feature.toUpperCase()), request.modelId(), request.fallbackModelId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** A failed model test is a problem with the provider, not with this request. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> providerFailed(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
