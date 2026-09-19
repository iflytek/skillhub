package com.iflytek.skillhub.domain.authoring.validation;

import com.iflytek.skillhub.domain.authoring.FixSuggestion;

/**
 * A finding produced by one validation layer, before it is persisted. The orchestrator
 * converts these into {@link ValidationFinding} rows and emits FINDING events.
 */
public record FindingDraft(
        ValidationLayer layer,
        String ruleCode,
        FindingSeverity severity,
        String filePath,
        String location,
        String message,
        FixSuggestion suggestion
) {

    public static FindingDraft error(ValidationLayer layer, String ruleCode, String message) {
        return new FindingDraft(layer, ruleCode, FindingSeverity.ERROR, null, null, message, null);
    }

    public static FindingDraft error(ValidationLayer layer, String ruleCode, String filePath,
                                     String message, FixSuggestion suggestion) {
        return new FindingDraft(layer, ruleCode, FindingSeverity.ERROR, filePath, null, message, suggestion);
    }

    public static FindingDraft warning(ValidationLayer layer, String ruleCode, String filePath,
                                       String message) {
        return new FindingDraft(layer, ruleCode, FindingSeverity.WARNING, filePath, null, message, null);
    }

    public static FindingDraft info(ValidationLayer layer, String ruleCode, String message) {
        return new FindingDraft(layer, ruleCode, FindingSeverity.INFO, null, null, message, null);
    }
}
