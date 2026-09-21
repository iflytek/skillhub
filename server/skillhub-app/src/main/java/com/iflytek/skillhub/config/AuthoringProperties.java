package com.iflytek.skillhub.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feature configuration for the skill authoring and validation platform
 * ({@code skillhub.authoring.*}).
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

    private final LocalScript localScript = new LocalScript();

    private final OpenAiCompatible openAiCompatible = new OpenAiCompatible();

    private final Mcp mcp = new Mcp();

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

    public LocalScript getLocalScript() {
        return localScript;
    }

    public OpenAiCompatible getOpenAiCompatible() {
        return openAiCompatible;
    }

    public Mcp getMcp() {
        return mcp;
    }

    /** Local script execution runtime. */
    public static class LocalScript {
        private boolean enabled = true;

        /** Where scripts run: INLINE on the server host (dev), DOCKER in a locked-down container (production). */
        private ScriptExecutionMode executionMode = ScriptExecutionMode.INLINE;

        private final Docker docker = new Docker();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public ScriptExecutionMode getExecutionMode() {
            return executionMode;
        }

        public void setExecutionMode(ScriptExecutionMode executionMode) {
            this.executionMode = executionMode;
        }

        public Docker getDocker() {
            return docker;
        }

        /** Container isolation profile for the docker execution mode. */
        public static class Docker {
            private String image = "alpine:3.20";
            private String memory = "256m";
            private String cpus = "1.0";
            private int pidsLimit = 128;
            private String tmpfsSize = "64m";

            public String getImage() {
                return image;
            }

            public void setImage(String image) {
                this.image = image;
            }

            public String getMemory() {
                return memory;
            }

            public void setMemory(String memory) {
                this.memory = memory;
            }

            public String getCpus() {
                return cpus;
            }

            public void setCpus(String cpus) {
                this.cpus = cpus;
            }

            public int getPidsLimit() {
                return pidsLimit;
            }

            public void setPidsLimit(int pidsLimit) {
                this.pidsLimit = pidsLimit;
            }

            public String getTmpfsSize() {
                return tmpfsSize;
            }

            public void setTmpfsSize(String tmpfsSize) {
                this.tmpfsSize = tmpfsSize;
            }
        }
    }

    /** Script execution backend selection. */
    public enum ScriptExecutionMode {
        INLINE,
        DOCKER
    }

    /**
     * OpenAI-compatible chat completion runtime. The API key is resolved here
     * (environment/config), never from a per-draft runtime binding.
     */
    public static class OpenAiCompatible {
        private boolean enabled = false;
        private String apiKey;
        private String defaultEndpoint;
        private String defaultModel;
        private int timeoutMs = 120_000;
        /** Max chat-completion rounds per prompt task (each round may execute MCP tool calls). */
        private int maxToolRounds = 4;
        /**
         * SSRF guard escape hatch for enterprise deployments whose LLM endpoint
         * lives on a private network (e.g. an internal vLLM). Loopback and
         * RFC1918 destinations stay blocked by default; link-local (cloud
         * metadata) is always blocked.
         */
        private boolean allowPrivateEndpoints = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getDefaultEndpoint() {
            return defaultEndpoint;
        }

        public void setDefaultEndpoint(String defaultEndpoint) {
            this.defaultEndpoint = defaultEndpoint;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getMaxToolRounds() {
            return maxToolRounds;
        }

        public void setMaxToolRounds(int maxToolRounds) {
            this.maxToolRounds = maxToolRounds;
        }

        public boolean isAllowPrivateEndpoints() {
            return allowPrivateEndpoints;
        }

        public void setAllowPrivateEndpoints(boolean allowPrivateEndpoints) {
            this.allowPrivateEndpoints = allowPrivateEndpoints;
        }
    }

    /**
     * Security knobs for user-declared MCP servers. Defaults are the safe
     * production posture: private endpoints blocked, stdio transport off, no
     * environment references allowed. Local development relaxes them via
     * application-local.yml.
     */
    public static class Mcp {
        /**
         * When false (default), http/sse MCP endpoints pointing at loopback,
         * RFC1918, ULA, or CGNAT addresses are rejected. Link-local addresses
         * (cloud metadata) are always rejected.
         */
        private boolean allowPrivateEndpoints = false;

        /** When false (default), stdio MCP servers cannot be declared or spawned. */
        private boolean stdioEnabled = false;

        /** Server environment variables a binding may reference via envRefs. */
        private List<String> envAllowlist = new ArrayList<>();

        private final Docker docker = new Docker();

        public boolean isAllowPrivateEndpoints() {
            return allowPrivateEndpoints;
        }

        public void setAllowPrivateEndpoints(boolean allowPrivateEndpoints) {
            this.allowPrivateEndpoints = allowPrivateEndpoints;
        }

        public boolean isStdioEnabled() {
            return stdioEnabled;
        }

        public void setStdioEnabled(boolean stdioEnabled) {
            this.stdioEnabled = stdioEnabled;
        }

        public List<String> getEnvAllowlist() {
            return envAllowlist;
        }

        public void setEnvAllowlist(List<String> envAllowlist) {
            this.envAllowlist = envAllowlist;
        }

        public Docker getDocker() {
            return docker;
        }

        /** Isolation profile for stdio MCP servers in docker execution mode. */
        public static class Docker {
            private String image = "alpine:3.20";
            private String memory = "256m";
            private String cpus = "1.0";
            private int pidsLimit = 128;
            private String tmpfsSize = "64m";

            public String getImage() {
                return image;
            }

            public void setImage(String image) {
                this.image = image;
            }

            public String getMemory() {
                return memory;
            }

            public void setMemory(String memory) {
                this.memory = memory;
            }

            public String getCpus() {
                return cpus;
            }

            public void setCpus(String cpus) {
                this.cpus = cpus;
            }

            public int getPidsLimit() {
                return pidsLimit;
            }

            public void setPidsLimit(int pidsLimit) {
                this.pidsLimit = pidsLimit;
            }

            public String getTmpfsSize() {
                return tmpfsSize;
            }

            public void setTmpfsSize(String tmpfsSize) {
                this.tmpfsSize = tmpfsSize;
            }
        }
    }
}
