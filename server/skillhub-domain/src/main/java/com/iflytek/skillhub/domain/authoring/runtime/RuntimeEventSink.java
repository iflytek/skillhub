package com.iflytek.skillhub.domain.authoring.runtime;

/**
 * Callbacks a runtime adapter uses to stream execution progress into the persisted
 * run event log. The orchestrator assigns sequence numbers and broadcasts to SSE
 * subscribers; adapters only report what happened.
 */
public interface RuntimeEventSink {

    /** An agent-level message (model response, task narration). */
    void agentMessage(String taskName, String content);

    /** A tool invocation initiated by the runtime (script execution, HTTP tool call). */
    void toolCall(String taskName, String tool, String argumentsJson);

    /** The outcome of a tool invocation. */
    void toolResult(String taskName, String tool, String summaryJson);

    /** A raw log line from the runtime (stdout/stderr of a script, transport log). */
    void log(String taskName, String stream, String line);
}
