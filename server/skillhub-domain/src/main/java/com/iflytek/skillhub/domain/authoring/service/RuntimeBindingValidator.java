package com.iflytek.skillhub.domain.authoring.service;

import com.iflytek.skillhub.domain.authoring.validation.FindingDraft;
import com.iflytek.skillhub.domain.authoring.validation.ValidationLayer;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * The CONFIG validation layer: checks the runtime binding (agent type, adapter
 * configuration, tool allowlist, MCP server declarations) plus the syntactic validity
 * of validation task assertions. Rejects embedded plaintext credentials — bindings may
 * only reference server-side credential names.
 */
@Service
public class RuntimeBindingValidator {

    private static final Pattern TOOL_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$");
    private static final Pattern MCP_NAME = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
    private static final Pattern CREDENTIAL_KEY = Pattern.compile(
            "(?i).*(secret|password|passwd|api[_-]?key|token|credential).*");
    private static final Pattern ENV_REF_NAME = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final List<String> TRANSPORTS = List.of("http", "sse", "stdio");
    private static final List<String> INTERPRETERS = List.of("sh", "bash", "python3", "node");

    /**
     * @param agentType       agent type identifier (may be invalid — reported as finding)
     * @param adapterConfig   adapter-specific configuration map
     * @param toolAllowlist   tool identifiers (may be null = unrestricted)
     * @param mcpServers      MCP server declarations (may be null)
     */
    public List<FindingDraft> validate(String agentType, Map<String, Object> adapterConfig,
                                       List<String> toolAllowlist, List<Map<String, Object>> mcpServers) {
        List<FindingDraft> findings = new ArrayList<>();
        String resolvedAgentType = agentType == null ? "" : agentType;

        switch (resolvedAgentType) {
            case "local-script" -> validateLocalScriptConfig(adapterConfig, findings);
            case "openai-compatible" -> validateOpenAiCompatibleConfig(adapterConfig, findings);
            default -> findings.add(FindingDraft.error(ValidationLayer.CONFIG, "AGENT_TYPE_UNKNOWN",
                    "Unknown agent runtime type: " + resolvedAgentType
                            + " (supported: local-script, openai-compatible)"));
        }

        if (toolAllowlist != null) {
            for (String tool : toolAllowlist) {
                if (tool == null || !TOOL_NAME.matcher(tool).matches()) {
                    findings.add(FindingDraft.error(ValidationLayer.CONFIG, "TOOL_ALLOWLIST_INVALID",
                            "Invalid tool allowlist entry: " + tool));
                }
            }
        }

        if (mcpServers != null) {
            for (Map<String, Object> server : mcpServers) {
                validateMcpServer(server, findings);
            }
        }

        scanForEmbeddedSecrets(agentType, adapterConfig, toolAllowlist, mcpServers, findings);
        return findings;
    }

    private void validateLocalScriptConfig(Map<String, Object> config, List<FindingDraft> findings) {
        if (config == null) {
            return;
        }
        Object interpreter = config.get("interpreter");
        if (interpreter != null && !INTERPRETERS.contains(interpreter.toString())) {
            findings.add(FindingDraft.error(ValidationLayer.CONFIG, "INTERPRETER_UNSUPPORTED",
                    "Unsupported interpreter: " + interpreter + " (supported: "
                            + String.join(", ", INTERPRETERS) + ")"));
        }
        Object envAllowlist = config.get("envAllowlist");
        if (envAllowlist instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry == null || !ENV_REF_NAME.matcher(entry.toString()).matches()) {
                    findings.add(FindingDraft.error(ValidationLayer.CONFIG, "ENV_ALLOWLIST_INVALID",
                            "Invalid environment variable name: " + entry));
                }
            }
        } else if (envAllowlist != null) {
            findings.add(FindingDraft.error(ValidationLayer.CONFIG, "ENV_ALLOWLIST_INVALID",
                    "envAllowlist must be a list of variable names"));
        }
    }

    private void validateOpenAiCompatibleConfig(Map<String, Object> config, List<FindingDraft> findings) {
        if (config == null) {
            findings.add(FindingDraft.error(ValidationLayer.CONFIG, "LLM_CONFIG_MISSING",
                    "openai-compatible runtime requires a config object with endpoint and model"));
            return;
        }
        Object endpoint = config.get("endpoint");
        if (endpoint == null || endpoint.toString().isBlank()) {
            findings.add(FindingDraft.error(ValidationLayer.CONFIG, "LLM_ENDPOINT_MISSING",
                    "openai-compatible runtime requires an 'endpoint' URL"));
        } else if (!isHttpUrl(endpoint.toString())) {
            findings.add(FindingDraft.error(ValidationLayer.CONFIG, "LLM_ENDPOINT_INVALID",
                    "endpoint must be an http(s) URL: " + endpoint));
        }
        Object model = config.get("model");
        if (model == null || model.toString().isBlank()) {
            findings.add(FindingDraft.error(ValidationLayer.CONFIG, "LLM_MODEL_MISSING",
                    "openai-compatible runtime requires a 'model' name"));
        }
        Object temperature = config.get("temperature");
        if (temperature instanceof Number number
                && (number.doubleValue() < 0 || number.doubleValue() > 2)) {
            findings.add(FindingDraft.error(ValidationLayer.CONFIG, "LLM_TEMPERATURE_INVALID",
                    "temperature must be between 0 and 2"));
        }
        // the API key is resolved from server configuration, never from the binding
        if (config.containsKey("apiKey") || config.containsKey("api_key")) {
            findings.add(FindingDraft.error(ValidationLayer.CONFIG, "CREDENTIAL_EMBEDDED",
                    "apiKey must not be stored in the binding; configure it server-side"));
        }
    }

    private void validateMcpServer(Map<String, Object> server, List<FindingDraft> findings) {
        Object name = server.get("name");
        if (name == null || !MCP_NAME.matcher(name.toString()).matches()) {
            findings.add(FindingDraft.error(ValidationLayer.CONFIG, "MCP_NAME_INVALID",
                    "MCP server name must be lowercase alphanumerics/hyphens: " + name));
        }
        Object transport = server.get("transport");
        if (transport == null || !TRANSPORTS.contains(transport.toString())) {
            findings.add(FindingDraft.error(ValidationLayer.CONFIG, "MCP_TRANSPORT_INVALID",
                    "MCP server " + name + " has unknown transport: " + transport
                            + " (supported: " + String.join(", ", TRANSPORTS) + ")"));
        } else if ("http".equals(transport.toString()) || "sse".equals(transport.toString())) {
            Object endpoint = server.get("endpoint");
            if (endpoint == null || !isHttpUrl(endpoint.toString())) {
                findings.add(FindingDraft.error(ValidationLayer.CONFIG, "MCP_ENDPOINT_INVALID",
                        "MCP server " + name + " requires an http(s) endpoint"));
            }
        } else {
            Object command = server.get("command");
            if (command == null || command.toString().isBlank()) {
                findings.add(FindingDraft.error(ValidationLayer.CONFIG, "MCP_COMMAND_MISSING",
                        "MCP server " + name + " (stdio) requires a command"));
            }
        }
        Object toolFilters = server.get("toolFilters");
        if (toolFilters instanceof List<?> filters) {
            for (Object filter : filters) {
                if (filter == null || !TOOL_NAME.matcher(filter.toString()).matches()) {
                    findings.add(FindingDraft.error(ValidationLayer.CONFIG, "MCP_TOOL_FILTER_INVALID",
                            "MCP server " + name + " has an invalid tool filter: " + filter));
                }
            }
        }
        Object envRefs = server.get("envRefs");
        if (envRefs instanceof List<?> refs) {
            for (Object ref : refs) {
                if (ref == null || !ENV_REF_NAME.matcher(ref.toString()).matches()) {
                    findings.add(FindingDraft.error(ValidationLayer.CONFIG, "MCP_ENV_REF_INVALID",
                            "MCP server " + name + " has an invalid env reference: " + ref));
                }
            }
        }
    }

    /** Rejects secret-looking keys anywhere in the binding payload. */
    private void scanForEmbeddedSecrets(String agentType, Map<String, Object> adapterConfig,
                                        List<String> toolAllowlist,
                                        List<Map<String, Object>> mcpServers,
                                        List<FindingDraft> findings) {
        if (adapterConfig != null) {
            scanMap(adapterConfig, findings);
        }
        if (mcpServers != null) {
            for (Map<String, Object> server : mcpServers) {
                scanMap(server, findings);
            }
        }
    }

    private void scanMap(Map<String, Object> map, List<FindingDraft> findings) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (CREDENTIAL_KEY.matcher(entry.getKey()).matches()
                    && entry.getValue() != null && !entry.getValue().toString().isBlank()) {
                findings.add(FindingDraft.error(ValidationLayer.CONFIG, "CREDENTIAL_EMBEDDED",
                        "Binding must not embed credentials in field '" + entry.getKey()
                                + "'; reference server-side credentials instead"));
            }
        }
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
