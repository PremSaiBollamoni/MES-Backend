package com.antigravity.mes.ai;

/** Each app feature that calls an AI model picks its own provider and model. */
public enum AiFeature {
    /** In-app support chat assistant and ticket drafting. */
    CHAT,
    /** Daily and monthly PDF reports, including the ones sent by email. */
    REPORT
}
