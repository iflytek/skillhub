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

    /** Deadline after execution starts; interrupts cooperatively and blocks late writes. */
    private long runTimeoutMs = 900_000;

    /** Active runs older than this are swept to TIMED_OUT by the maintenance task. */
    private int staleRunMinutes = 30;

    private final LocalScript localScript = new LocalScript();

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

    /** Local script execution runtime. */
    public static class LocalScript {
        private boolean enabled = true;

        /** Where scripts run: INLINE on the server host (dev), DOCKER in a locked-down container (production). */
        private ScriptExecutionMode executionMode = ScriptExecutionMode.INLINE;

        /** Deployment-owned variable names available to all local-script draft owners. Empty denies all. */
        private List<String> environmentAllowlist = List.of();

        public List<String> getEnvironmentAllowlist() {
            return environmentAllowlist;
        }

        public void setEnvironmentAllowlist(List<String> environmentAllowlist) {
            this.environmentAllowlist = environmentAllowlist == null ? List.of() : List.copyOf(environmentAllowlist);
        }

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

}
