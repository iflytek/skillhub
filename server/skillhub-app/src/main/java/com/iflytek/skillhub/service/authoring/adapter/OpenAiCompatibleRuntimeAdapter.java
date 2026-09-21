package com.iflytek.skillhub.service.authoring.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflytek.skillhub.config.AuthoringProperties;
import com.iflytek.skillhub.domain.authoring.runtime.RuntimeEventSink;
import com.iflytek.skillhub.domain.authoring.runtime.RuntimeExecutionContext;
import com.iflytek.skillhub.domain.authoring.runtime.SkillRuntimeAdapter;
import com.iflytek.skillhub.domain.authoring.runtime.TaskResult;
import com.iflytek.skillhub.domain.authoring.service.AuthoringSecurityPolicy;
import com.iflytek.skillhub.domain.authoring.spec.TaskType;
import com.iflytek.skillhub.domain.authoring.spec.ValidationTaskSpec;
import com.iflytek.skillhub.service.authoring.mcp.HttpMcpClient;
import com.iflytek.skillhub.service.authoring.mcp.McpClient;
import com.iflytek.skillhub.service.authoring.mcp.McpClientFactory;
import com.iflytek.skillhub.service.authoring.mcp.McpTool;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Behavior-layer adapter for prompt tasks: an agentic loop against an
 * OpenAI-compatible chat-completion endpoint. Tools declared on the binding's MCP
 * servers are discovered live (initialize + tools/list), filtered by the server's
 * toolFilters and the binding's toolAllowlist, exposed to the model as function
 * tools, and executed for real when the model calls them — every chat round and
 * every tool invocation/reply lands in the run's event trace. The API key is
 * resolved from server-side configuration only; runtime bindings reference
 * endpoints and models, never credentials.
 */
@Component
public class OpenAiCompatibleRuntimeAdapter implements SkillRuntimeAdapter {

    private static final String SKILL_MD = "SKILL.md";
    /** Cap on a single tool output fed back into the model context. */
    private static final int MAX_TOOL_OUTPUT_CHARS = 8_000;
    /** Cap on tool discovery (initialize + tools/list) regardless of task timeout. */
    private static final Duration DISCOVERY_TIMEOUT = Duration.ofSeconds(30);

    private final AuthoringProperties properties;
    private final ObjectMapper objectMapper;
    private final McpClientFactory mcpClientFactory;
    private final AuthoringSecurityPolicy securityPolicy;
    private final HttpClient httpClient;

    public OpenAiCompatibleRuntimeAdapter(AuthoringProperties properties, ObjectMapper objectMapper,
                                          McpClientFactory mcpClientFactory,
                                          AuthoringSecurityPolicy securityPolicy) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mcpClientFactory = mcpClientFactory;
        this.securityPolicy = securityPolicy;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                // never follow redirects: a public endpoint answering 302 towards
                // a link-local target must not become an SSRF bypass
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String agentType() {
        return "openai-compatible";
    }

    @Override
    public boolean supports(TaskType taskType) {
        return taskType == TaskType.PROMPT;
    }

    @Override
    public boolean enabled() {
        return properties.getOpenAiCompatible().isEnabled();
    }

    /** One tool the model may call, bound to the client that can execute it. */
    private record ExposedTool(McpClient client, String server, McpTool tool) {
    }

    @Override
    public TaskResult execute(RuntimeExecutionContext context, ValidationTaskSpec task,
                              RuntimeEventSink sink) throws Exception {
        AuthoringProperties.OpenAiCompatible config = properties.getOpenAiCompatible();
        String endpoint = firstNonBlank(context.configString("endpoint"), config.getDefaultEndpoint());
        String model = firstNonBlank(context.configString("model"), config.getDefaultModel());
        if (endpoint == null || model == null) {
            throw new IllegalStateException(
                    "openai-compatible runtime requires endpoint and model (binding config or server defaults)");
        }
        // requests carry the server-side API key, so the destination must pass
        // the SSRF guard before any credential leaves the host
        String endpointRejection = securityPolicy.endpointRejection(
                endpoint, AuthoringSecurityPolicy.EndpointUse.LLM_API);
        if (endpointRejection != null) {
            throw new IllegalStateException(
                    "openai-compatible endpoint is not allowed: " + endpointRejection);
        }
        String url = endpoint.endsWith("/") ? endpoint + "chat/completions" : endpoint + "/chat/completions";
        Duration requestTimeout = Duration.ofMillis(Math.max(task.timeoutMs(), 1_000));

        List<McpClient> clients = new ArrayList<>();
        Map<String, ExposedTool> exposedTools = discoverTools(context, task, sink, clients);
        try {
            ArrayNode messages = objectMapper.createArrayNode();
            messages.addObject().put("role", "system").put("content", readSkillMd(context.workingDirectory()));
            messages.addObject().put("role", "user").put("content", task.prompt());

            int executedToolCalls = 0;
            String content = "";
            int maxRounds = Math.max(config.getMaxToolRounds(), 1);
            for (int round = 1; round <= maxRounds; round++) {
                if (context.cancellation().isCancelRequested()) {
                    throw new IllegalStateException("task '" + task.name() + "' cancelled");
                }

                ObjectNode requestBody = chatRequest(model, context, messages, exposedTools, round);
                sink.toolCall(task.name(), "openai-chat", requestBody.get("meta").toString());
                JsonNode message = postChatCompletion(url, config, requestBody, requestTimeout);

                content = message.path("content").asText("");
                JsonNode toolCalls = message.path("tool_calls");
                if (toolCalls.isEmpty() || exposedTools.isEmpty()) {
                    break; // final answer (or nothing we can execute)
                }

                // record the assistant turn verbatim, then append one tool reply per call
                ObjectNode assistantMessage = objectMapper.createObjectNode();
                assistantMessage.put("role", "assistant");
                if (!content.isEmpty()) {
                    assistantMessage.put("content", content);
                }
                HttpMcpClient.attachToolCalls(objectMapper, assistantMessage, toolCalls);
                messages.add(assistantMessage);

                for (JsonNode call : toolCalls) {
                    String callId = call.path("id").asText("call-" + executedToolCalls);
                    String functionName = call.path("function").path("name").asText("");
                    String argumentsJson = call.path("function").path("arguments").asText("{}");
                    ExposedTool exposed = exposedTools.get(functionName);
                    if (exposed == null) {
                        appendToolReply(messages, callId, "error: unknown tool '" + functionName + "'");
                        sink.toolResult(task.name(), "mcp:" + functionName,
                                "{\"error\":\"unknown tool\"}");
                        continue;
                    }
                    sink.toolCall(task.name(), "mcp:" + exposed.server() + "/" + exposed.tool().name(),
                            argumentsJson);
                    String reply;
                    try {
                        Map<String, Object> arguments = parseArguments(argumentsJson);
                        String output = exposed.client().callTool(
                                exposed.tool().name(), arguments, requestTimeout);
                        reply = truncate(output, MAX_TOOL_OUTPUT_CHARS);
                        executedToolCalls++;
                        sink.toolResult(task.name(), "mcp:" + exposed.server() + "/" + exposed.tool().name(),
                                "{\"ok\":true,\"length\":" + output.length() + "}");
                    } catch (Exception exception) {
                        reply = "error: tool call failed: " + exception.getMessage();
                        sink.toolResult(task.name(), "mcp:" + exposed.server() + "/" + exposed.tool().name(),
                                "{\"error\":" + jsonString(String.valueOf(exception.getMessage())) + "}");
                    }
                    appendToolReply(messages, callId, reply);
                }
                if (round == maxRounds) {
                    sink.log(task.name(), "summary",
                            "round limit (" + maxRounds + ") reached while the model still requests tools");
                }
            }

            sink.agentMessage(task.name(), truncate(content, 4_000));
            return TaskResult.ofPrompt(content, executedToolCalls,
                    new WorkingDirectoryArtifacts(context.workingDirectory()));
        } finally {
            clients.forEach(McpClient::close);
        }
    }

    // ---------------------------------------------------------------- tool discovery

    /**
     * Connects to every declared MCP server, lists its tools, and keeps the ones
     * allowed by the server's toolFilters and the binding's global toolAllowlist
     * (empty allowlist = unrestricted; entries match the bare tool name or
     * "server.tool"). Keys of the returned map are the function names exposed to
     * the model. Every client created along the way is appended to {@code clients}
     * so the caller can close them all — even servers whose tools were entirely
     * filtered out.
     */
    private Map<String, ExposedTool> discoverTools(RuntimeExecutionContext context,
                                                   ValidationTaskSpec task, RuntimeEventSink sink,
                                                   List<McpClient> clients) {
        Map<String, ExposedTool> exposed = new LinkedHashMap<>();
        List<String> allowlist = context.safeToolAllowlist();
        for (Map<String, Object> server : context.safeMcpServers()) {
            String serverName = String.valueOf(server.get("name"));
            Set<String> toolFilters = server.get("toolFilters") instanceof List<?> filters
                    ? filters.stream().map(String::valueOf).collect(Collectors.toSet())
                    : Set.of();
            List<McpTool> tools;
            McpClient client;
            try {
                client = mcpClientFactory.create(server);
                clients.add(client);
                tools = client.listTools(DISCOVERY_TIMEOUT);
            } catch (Exception exception) {
                // the config-layer probe already reported unreachable servers; skip quietly
                sink.log(task.name(), "mcp", "server '" + serverName + "' unavailable during task: "
                        + exception.getMessage());
                continue;
            }
            for (McpTool tool : tools) {
                if (!toolFilters.isEmpty() && !toolFilters.contains(tool.name())) {
                    continue;
                }
                if (!allowlist.isEmpty() && !allowlist.contains(tool.name())
                        && !allowlist.contains(serverName + "." + tool.name())) {
                    continue;
                }
                exposed.put(exposedName(exposed.keySet(), serverName, tool.name()),
                        new ExposedTool(client, serverName, tool));
            }
        }
        return exposed;
    }

    /** Function names must match ^[a-zA-Z0-9_-]{1,64}$; MCP names may contain dots. */
    private String exposedName(Set<String> taken, String serverName, String toolName) {
        String candidate = (serverName + "__" + toolName).replaceAll("[^a-zA-Z0-9_-]", "_");
        if (candidate.length() > 64) {
            candidate = candidate.substring(0, 64);
        }
        String unique = candidate;
        int suffix = 2;
        while (taken.contains(unique)) {
            String tail = "_" + suffix++;
            unique = candidate.substring(0, 64 - tail.length()) + tail;
        }
        return unique;
    }

    // ---------------------------------------------------------------- chat protocol

    private ObjectNode chatRequest(String model, RuntimeExecutionContext context, ArrayNode messages,
                                   Map<String, ExposedTool> exposedTools, int round) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        Object temperature = context.safeAdapterConfig().get("temperature");
        if (temperature instanceof Number number) {
            body.put("temperature", number.doubleValue());
        }
        body.set("messages", messages);
        if (!exposedTools.isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            exposedTools.forEach((exposedName, tool) -> tools.add(
                    HttpMcpClient.toFunctionSchema(objectMapper, tool.tool(), exposedName)));
        }
        // trace metadata; removed before the body reaches the endpoint
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("model", model);
        meta.put("round", round);
        meta.put("messages", messages.size());
        meta.put("tools", exposedTools.size());
        body.set("meta", meta);
        return body;
    }

    /** Posts one chat-completion request and returns the first choice's message. */
    private JsonNode postChatCompletion(String url, AuthoringProperties.OpenAiCompatible config,
                                        ObjectNode requestBody, Duration timeout) throws Exception {
        requestBody.remove("meta"); // trace-only field must not reach the endpoint
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8));
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + config.getApiKey());
        }
        HttpResponse<InputStream> response =
                httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
        String body = HttpMcpClient.readBodyCapped(response.body(), HttpMcpClient.MAX_RESPONSE_BYTES);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("chat completion failed with HTTP " + response.statusCode()
                    + ": " + truncate(body, 500));
        }
        return objectMapper.readTree(body).path("choices").path(0).path("message");
    }

    private void appendToolReply(ArrayNode messages, String toolCallId, String content) {
        messages.add(HttpMcpClient.toolResponseMessage(objectMapper, toolCallId, content));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String argumentsJson) {
        try {
            return objectMapper.readValue(argumentsJson, Map.class);
        } catch (Exception exception) {
            return new HashMap<>();
        }
    }

    private String jsonString(String value) {
        return objectMapper.valueToTree(value == null ? "" : value).toString();
    }

    private String readSkillMd(Path workingDirectory) throws IOException {
        Path skillMd = workingDirectory.resolve(SKILL_MD);
        if (Files.isRegularFile(skillMd)) {
            return Files.readString(skillMd, StandardCharsets.UTF_8);
        }
        return "No SKILL.md is present in this draft.";
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null || fallback.isBlank() ? null : fallback;
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }
}
