package com.antigravity.mes.support;

import com.antigravity.mes.ai.AiFeature;
import com.antigravity.mes.ai.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** In-app help chatbot: answers questions about how to use the app (attendance, production
 * entry, stock, payroll, etc.) and tells the employee to raise a ticket via the separate
 * button when it can't resolve something itself. Uses whichever provider and model the
 * CHAT feature is set to in the AI settings. */
@Service
public class SupportChatService {

    private static final String SYSTEM_PROMPT = """
            You are the in-app help assistant for SMO, a garment factory Manufacturing
            Execution System (MES) used by factory employees on their phones. Help with
            questions about using the app: attendance, production entry, stock/WIP tracking,
            payroll, employee records, stations, and reports - whatever the app's screens cover.
            Keep answers short and practical, like a helpful coworker, not a formal support
            agent. If the question is about something broken, a bug, or something you can't
            resolve through conversation, tell them to tap "Raise Ticket" below so it reaches
            the admin team. Never invent app features or data that may not exist - if unsure,
            say so and suggest raising a ticket instead.

            Reply in plain text only - the chat screen does not render markdown. Never use
            **bold**, *italic*, backticks, bullet points with "-" or "*", or any other markdown
            syntax. Write plain sentences, and use numbered steps like "1)" "2)" if you need
            a sequence.
            """;

    private static final String DRAFT_SYSTEM_PROMPT = """
            You turn a support chat conversation into a support ticket. Read the
            conversation and write a short, clear ticket describing the employee's problem
            in their own voice (first person, like "My attendance isn't syncing...").

            Respond with ONLY a JSON object, no other text, no markdown fences:
            {"subject": "...", "description": "..."}

            "subject" is under 10 words, specific to the actual problem (never generic like
            "App issue"). "description" is 2-4 plain sentences explaining what's wrong, what
            they already tried (if mentioned), and any detail that would help someone fix it.
            If the conversation doesn't actually describe a problem yet, still produce your
            best-guess subject/description from whatever was said - never refuse or ask a
            question back, since this always runs right before a human reads the result.
            """;

    @Autowired
    private AiService aiService;

    @Autowired
    private ObjectMapper objectMapper;

    public boolean isEnabled() {
        return aiService.isConfigured(AiFeature.CHAT);
    }

    public record TicketDraft(String subject, String description) {}

    /** Summarizes the chat so far into a ready-to-submit ticket, so the employee doesn't
     * have to write up their own problem from scratch - they just review/edit before
     * sending. Same model as {@link #reply}, different system prompt. */
    public TicketDraft draftTicket(List<Map<String, String>> history) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("Support chat is not set up - choose a chat model in AI settings");
        }
        if (history.isEmpty()) {
            return new TicketDraft("", "");
        }

        List<Map<String, String>> messages = new ArrayList<>(history);
        messages.add(Map.of("role", "user", "content", "Draft the ticket now, as the JSON object only."));

        // Reasoning models spend tokens on internal thinking before the visible answer, and
        // that counts against this budget too - a 3+ turn conversation can burn past 400
        // before any JSON comes out, leaving an empty/truncated response that fails to parse.
        String raw = aiService.complete(AiFeature.CHAT, DRAFT_SYSTEM_PROMPT, messages, 1200);
        JsonNode parsed = objectMapper.readTree(stripJsonFence(raw));
        return new TicketDraft(parsed.path("subject").asText(""), parsed.path("description").asText(""));
    }

    /** Models sometimes wrap JSON in a ```json fence despite being told not to - strip it
     * defensively rather than fail the parse. */
    private String stripJsonFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) trimmed = trimmed.substring(firstNewline + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    /** {@code history} is the prior turns as {role, content} maps (role: "user" or
     * "assistant"), oldest first - the caller resends the whole conversation each time,
     * same as any stateless chat completion API. */
    public String reply(List<Map<String, String>> history) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("Support chat is not set up - choose a chat model in AI settings");
        }
        // A short help answer never needs a large budget - without a cap, some providers
        // reserve their full context window and reject the call on a small credit balance.
        return stripMarkdown(aiService.complete(AiFeature.CHAT, SYSTEM_PROMPT, history, 500));
    }

    /** The chat bubble renders plain text, not markdown - the system prompt asks the model
     * to skip markdown entirely, but models don't always follow that reliably, so strip the
     * common emphasis/bullet syntax defensively rather than show raw ** and *. */
    private String stripMarkdown(String text) {
        return text
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("(?<!\\w)\\*(.+?)\\*(?!\\w)", "$1")
                .replaceAll("(?m)^[ \\t]*[-*][ \\t]+", "- ")
                .replaceAll("`([^`]+)`", "$1");
    }
}
