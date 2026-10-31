package com.antigravity.mes.ai;

/** The wire format a provider speaks. Groq, OpenRouter, OpenAI and any custom endpoint all
 * share the OpenAI chat-completions shape, so they are one type with different base URLs. */
public enum AiProviderType {
    OPENAI_COMPATIBLE,
    GEMINI,
    ANTHROPIC
}
