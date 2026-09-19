package com.iflytek.skillhub.service.authoring.adapter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Default backend: the script runs as a plain subprocess of the server process
 * with a scrubbed environment. Suitable for local development and CI where the
 * drafts are the developer's own; production deployments should switch to the
 * docker backend.
 */
@Component
public class InlineScriptCommandBuilder implements ScriptCommandBuilder {

    @Override
    public String backend() {
        return "inline";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public ScriptExecutionPlan plan(Path workspace, String interpreter, Path scriptPath,
                                    List<String> args, Map<String, String> scrubbedEnvironment,
                                    String runTag) {
        List<String> command = new ArrayList<>();
        command.add(interpreter);
        command.add(scriptPath.toString());
        command.addAll(args);
        return new ScriptExecutionPlan(List.copyOf(command), Map.copyOf(scrubbedEnvironment));
    }
}
