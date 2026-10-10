package com.iflytek.skillhub.domain.authoring.runtime;

import com.iflytek.skillhub.domain.authoring.spec.TaskType;
import com.iflytek.skillhub.domain.authoring.spec.ValidationTaskSpec;

/**
 * SPI implemented by every agent runtime the authoring platform can bind to a draft.
 *
 * <p>Implementations live in the application layer (process execution, HTTP clients)
 * and are selected by the runtime binding's agent type. The contract is deliberately
 * minimal: execute one task, stream progress through the sink, return or throw.
 * Assertion evaluation is handled by the orchestrator via {@link TaskResult}, not by
 * adapters.
 */
public interface SkillRuntimeAdapter {

    /** The agent type identifier this adapter serves (see {@code AgentRuntimeType}). */
    String agentType();

    /** Whether the adapter can execute the given task kind. */
    boolean supports(TaskType taskType);

    /**
     * Whether the adapter is currently usable. Disabled adapters (missing server-side
     * configuration, feature flags) are reported as RUNTIME_DISABLED findings instead of
     * failed tasks when the orchestrator selects them.
     */
    default boolean enabled() {
        return true;
    }

    /**
     * Executes one validation task. Implementations must:
     * <ul>
     *   <li>honor {@code context.cancellation()} while running;</li>
     *   <li>enforce the task timeout themselves;</li>
     *   <li>report progress through {@code sink};</li>
     *   <li>never modify draft state — the working directory is a disposable copy.</li>
     * </ul>
     *
     * @return the raw task outcome for assertion evaluation
     * @throws InterruptedException when cancelled mid-execution
     */
    TaskResult execute(RuntimeExecutionContext context, ValidationTaskSpec task,
                       RuntimeEventSink sink) throws Exception;
}
