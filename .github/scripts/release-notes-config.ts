import { IssueLlmConfig } from "./issue-llm-types.ts";

const DEFAULT_TIMEOUT_MS = 30000;
const DEFAULT_MAX_ATTEMPTS = 2;
const DEFAULT_RETRY_BACKOFF_MS = 1500;
const DEFAULT_TEMPERATURE = 0.2;

export function readReleaseNotesLlmConfig(): IssueLlmConfig | null {
  const baseUrl = normalizeUrl(
    Deno.env.get("RELEASE_NOTES_LLM_BASE_URL") ||
      Deno.env.get("ISSUE_TRIAGE_LLM_BASE_URL"),
  );
  const apiKey = (
    Deno.env.get("RELEASE_NOTES_LLM_API_KEY") ||
    Deno.env.get("ISSUE_TRIAGE_LLM_API_KEY")
  )?.trim() ?? "";
  const model = (
    Deno.env.get("RELEASE_NOTES_LLM_MODEL") ||
    Deno.env.get("ISSUE_TRIAGE_LLM_MODEL")
  )?.trim() ?? "";

  if (!baseUrl || !apiKey || !model) {
    console.warn(
      "LLM config missing, will use fallback mode (conventional commit grouping)",
    );
    return null;
  }

  return {
    mode: "assist",
    provider: "openai-compatible",
    baseUrl,
    apiKey,
    model,
    timeoutMs: DEFAULT_TIMEOUT_MS,
    maxAttempts: DEFAULT_MAX_ATTEMPTS,
    retryBackoffMs: DEFAULT_RETRY_BACKOFF_MS,
    temperature: DEFAULT_TEMPERATURE,
    maxComments: 0,
    maxCommentChars: 0,
    maxBodyChars: 0,
  };
}

function normalizeUrl(value: string | undefined | null) {
  const trimmed = value?.trim();

  if (!trimmed) {
    return "";
  }

  return trimmed.endsWith("/") ? trimmed.slice(0, -1) : trimmed;
}
