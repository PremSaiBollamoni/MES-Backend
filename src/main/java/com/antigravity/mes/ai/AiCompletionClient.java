package com.antigravity.mes.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sends one chat turn to a provider and returns the reply text. Each provider type speaks its
 * own JSON shape here, so callers only ever deal with a system prompt, messages and text back.
 * Uses the JDK HttpClient, so no vendor SDK is needed. */
@Component
public class AiCompletionClient {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    /** Kept under the report poll deadline, so a slow provider fails over before the app gives up. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    @Autowired
    private ObjectMapper objectMapper;

    /** {@code messages} are {role, content} maps with role "user" or "assistant", oldest first. */
    public String complete(AiProviderType type, String baseUrl, String apiKey, String model,
                           String system, List<Map<String, String>> messages, int maxTokens) throws Exception {
        String base = trimSlash(baseUrl);
        return switch (type) {
            case OPENAI_COMPATIBLE -> openAiCompatible(base, apiKey, model, system, messages, maxTokens);
            case GEMINI -> gemini(base, apiKey, stripModelsPrefix(model), system, messages, maxTokens);
            case ANTHROPIC -> anthropic(base, apiKey, model, system, messages, maxTokens);
        };
    }

    private String openAiCompatible(String base, String apiKey, String model, String system,
                                    List<Map<String, String>> messages, int maxTokens) throws Exception {
        List<Map<String, String>> all = new ArrayList<>();
        if (notBlank(system)) all.add(Map.of("role", "system", "content", system));
        all.addAll(messages);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", all);
        // Without an explicit cap, some providers reserve their full context window up-front
        // and reject the request on a small credit balance.
        body.put("max_tokens", maxTokens);

        JsonNode root = post(base + "/chat/completions", Map.of("Authorization", "Bearer " + apiKey), body, "OpenAI-compatible");
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new AiProviderException("Provider returned no content: " + root, 200);
        }
        return content.asText();
    }

    private String gemini(String base, String apiKey, String model, String system,
                          List<Map<String, String>> messages, int maxTokens) throws Exception {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (Map<String, String> message : messages) {
            String role = "assistant".equals(message.get("role")) ? "model" : "user";
            contents.add(Map.<String, Object>of("role", role, "parts", List.of(Map.of("text", message.get("content")))));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (notBlank(system)) body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", system))));
        body.put("contents", contents);
        body.put("generationConfig", Map.of("maxOutputTokens", maxTokens));

        JsonNode root = post(base + "/models/" + model + ":generateContent", Map.of("x-goog-api-key", apiKey), body, "Gemini");
        StringBuilder text = new StringBuilder();
        for (JsonNode part : root.path("candidates").path(0).path("content").path("parts")) {
            text.append(part.path("text").asText(""));
        }
        if (text.isEmpty()) {
            throw new AiProviderException("Gemini returned no content: " + root, 200);
        }
        return text.toString();
    }

    private String anthropic(String base, String apiKey, String model, String system,
                             List<Map<String, String>> messages, int maxTokens) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        if (notBlank(system)) body.put("system", system);
        body.put("messages", messages);

        JsonNode root = post(base + "/messages",
                Map.of("x-api-key", apiKey, "anthropic-version", "2023-06-01"), body, "Anthropic");
        StringBuilder text = new StringBuilder();
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText(""));
            }
        }
        if (text.isEmpty()) {
            throw new AiProviderException("Anthropic returned no content: " + root, 200);
        }
        return text.toString();
    }

    private JsonNode post(String url, Map<String, String> headers, Map<String, Object> body, String label) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        headers.forEach(builder::header);

        HttpResponse<String> response;
        try {
            response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // Timeouts and dropped connections have no HTTP status - they are retried like a 503.
            throw new AiProviderException(label + " request failed: " + e.getMessage(), 0);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiProviderException(label + " request failed (" + response.statusCode() + "): " + response.body(), response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Gemini model ids are often copied as "models/gemini-2.5-flash"; the URL adds the prefix itself. */
    private static String stripModelsPrefix(String model) {
        return model.startsWith("models/") ? model.substring("models/".length()) : model;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
