package com.iflytek.skillhub.domain.authoring.validation;

/**
 * Event types emitted during a validation run and persisted in order. The sequence
 * number of each event enables SSE replay with Last-Event-ID resumption.
 */
public enum ValidationEventType {
    RUN_STARTED,
    PHASE_STARTED,
    PHASE_FINISHED,
    AGENT_MESSAGE,
    TOOL_CALL,
    TOOL_RESULT,
    LOG,
    FINDING,
    RUN_FINISHED
}
