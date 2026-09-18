package com.antigravity.mes.scan;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.antigravity.mes.model.Station;
import com.antigravity.mes.repository.StationRepository;
import com.antigravity.mes.scan.dto.ScannedRow;
import com.antigravity.mes.web.ErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads one photographed production book page into draft rows.
 *
 * Uses the same model as the reports. Handwriting on a photographed page is a harder
 * perception task than narrating a JSON blob, so if a set of books reads poorly, app.scan.model
 * points it at a stronger reader without a rebuild.
 */
@Service
public class PageScanService {

    @Value("${app.anthropic.enabled}")
    private boolean enabled;

    @Value("${app.anthropic.api-key}")
    private String apiKey;

    // Same model the reports use. Overridable with app.scan.model if handwriting accuracy on a
    // particular set of books turns out to need a stronger reader.
    @Value("${app.scan.model:claude-haiku-4-5-20251001}")
    private String model;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /** Raw reading of the page - carry-forward is applied afterwards by {@link ScanImportService}. */
    public ScanReading read(byte[] image, String contentType) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException(ErrorCodes.SCAN_DISABLED);
        }

        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();

        ImageBlockParam imageBlock = ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                        .mediaType(mediaType(contentType))
                        .data(Base64.getEncoder().encodeToString(image))
                        .build())
                .build();

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(8000L)
                .system(ScanPrompt.system(stationList()))
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofImage(imageBlock),
                        ContentBlockParam.ofText(TextBlockParam.builder()
                                .text(ScanPrompt.USER_INSTRUCTION)
                                .build())))
                .build();

        Message response = client.messages().create(params);
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());

        return parse(text);
    }

    /** What the model returned, before business rules are applied. */
    public record ScanReading(String rawDate, List<ScannedRow> rows) {}

    private ScanReading parse(String text) throws Exception {
        JsonNode root = objectMapper.readTree(stripFence(text));
        List<ScannedRow> rows = new ArrayList<>();

        for (JsonNode node : root.path("rows")) {
            List<Integer> pieces = new ArrayList<>();
            for (JsonNode piece : node.path("pieces")) {
                pieces.add(piece.asInt());
            }
            // A row the model returned with no readable count carries nothing to save; its note
            // is still worth surfacing so the supervisor knows to look at that hour.
            rows.add(new ScannedRow(
                    node.path("hour").asInt(),
                    node.hasNonNull("stationId") ? node.get("stationId").asLong() : null,
                    null,
                    node.path("rawStyle").asText(null),
                    node.path("carriedForward").asBoolean(false),
                    pieces,
                    node.hasNonNull("note") ? node.get("note").asText() : null));
        }
        return new ScanReading(root.path("rawDate").asText(null), rows);
    }

    /**
     * Models occasionally wrap JSON in a markdown fence despite being told not to. Stripping it
     * is cheaper than failing the whole page over formatting.
     */
    private String stripFence(String text) {
        String trimmed = text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        throw new IllegalArgumentException(ErrorCodes.SCAN_UNREADABLE);
    }

    private String stationList() {
        return stationRepository.findAllByOrderByStationNumberAsc().stream()
                .filter(Station::isActive)
                .map(s -> "  " + s.getId() + " = " + s.getOperationName() + " (" + s.getStationNumber() + ")")
                .collect(Collectors.joining("\n"));
    }

    private Base64ImageSource.MediaType mediaType(String contentType) {
        if (contentType == null) return Base64ImageSource.MediaType.IMAGE_JPEG;
        return switch (contentType.toLowerCase()) {
            case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
            case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
            case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
            default -> Base64ImageSource.MediaType.IMAGE_JPEG;
        };
    }
}
