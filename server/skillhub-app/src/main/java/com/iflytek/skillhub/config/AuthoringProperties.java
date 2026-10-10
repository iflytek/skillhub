package com.iflytek.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feature configuration for the skill authoring and validation platform
 * ({@code skillhub.authoring.*}). This layer covers the draft authoring and
 * structure-validation core; runtime-binding configuration (script execution
 * backends, LLM endpoints, MCP servers) arrives with the runtime layer.
 */
@ConfigurationProperties(prefix = "skillhub.authoring")
public class AuthoringProperties {

    /** Root directory for per-run isolated working directories. */
    private String workspaceRoot = System.getProperty("java.io.tmpdir") + "/skillhub-authoring";

    /** Concurrent validation runs executed in-process. */
    private int executorThreads = 4;

    /** Hard wall-clock cap for one whole validation run. */
    private long runTimeoutMs = 900_000;

    /** Active runs older than this are swept to TIMED_OUT by the maintenance task. */
    private int staleRunMinutes = 30;

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public int getExecutorThreads() {
        return executorThreads;
    }

    public void setExecutorThreads(int executorThreads) {
        this.executorThreads = executorThreads;
    }

    public long getRunTimeoutMs() {
        return runTimeoutMs;
    }

    public void setRunTimeoutMs(long runTimeoutMs) {
        this.runTimeoutMs = runTimeoutMs;
    }

    public int getStaleRunMinutes() {
        return staleRunMinutes;
    }

    public void setStaleRunMinutes(int staleRunMinutes) {
        this.staleRunMinutes = staleRunMinutes;
    }
}
