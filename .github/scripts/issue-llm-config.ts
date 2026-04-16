import { GitHubIssue } from "./github.ts";
import { IssueLlmConfig } from "./issue-llm-types.ts";
import { TriageResult } from "./issue-triage-types.ts";

const DEFAULT_TIMEOUT_MS = 30000;
const DEFAULT_MAX_ATTEMPTS = 2;
const DEFAULT_RETRY_BACKOFF_MS = 1500;
const DEFAULT_TEMPERATURE = 0.1;
const DEFAULT_MAX_COMMENTS = 4;
const DEFAULT_MAX_COMMENT_CHARS = 900;
const DEFAULT_MAX_BODY_CHARS = 6000;

export function readIssueLlmConfig(): IssueLlmConfig | null {
  const mode = normalizeMode(Deno.env.get("ISSUE_TRIAGE_LLM_MODE"));

  if (mode === "off") {
    return null;
  }

  const baseUrl = normalizeUrl(Deno.env.get("ISSUE_TRIAGE_LLM_BASE_URL"));
  const apiKey = Deno.env.get("ISSUE_TRIAGE_LLM_API_KEY")?.trim() ?? "";
  const model = Deno.env.get("ISSUE_TRIAGE_LLM_MODEL")?.trim() ?? "";

  if (!baseUrl || !apiKey || !model) {
    console.warn(
      "LLM triage is configured in a non-off mode but base URL, model, or API key is missing. Falling back to rules-only.",
    );
    return null;
  }

  return {
    mode,
    provider: "openai-compatible",
    baseUrl,
    apiKey,
    model,
    timeoutMs: parseInteger(
      Deno.env.get("ISSUE_TRIAGE_LLM_TIMEOUT_MS"),
      DEFAULT_TIMEOUT_MS,
    ),
    maxAttempts: Math.max(
      1,
      parseInteger(
        Deno.env.get("ISSUE_TRIAGE_LLM_MAX_ATTEMPTS"),
        DEFAULT_MAX_ATTEMPTS,
      ),
    ),
    retryBackoffMs: Math.max(
      0,
      parseInteger(
        Deno.env.get("ISSUE_TRIAGE_LLM_RETRY_BACKOFF_MS"),
        DEFAULT_RETRY_BACKOFF_MS,
      ),
    ),
    temperature: parseFloatSetting(
      Deno.env.get("ISSUE_TRIAGE_LLM_TEMPERATURE"),
      DEFAULT_TEMPERATURE,
    ),
    maxComments: parseInteger(
      Deno.env.get("ISSUE_TRIAGE_LLM_MAX_COMMENTS"),
      DEFAULT_MAX_COMMENTS,
    ),
    maxCommentChars: parseInteger(
      Deno.env.get("ISSUE_TRIAGE_LLM_MAX_COMMENT_CHARS"),
      DEFAULT_MAX_COMMENT_CHARS,
    ),
    maxBodyChars: parseInteger(
      Deno.env.get("ISSUE_TRIAGE_LLM_MAX_BODY_CHARS"),
      DEFAULT_MAX_BODY_CHARS,
    ),
  };
}

export function shouldUseLlm(issue: GitHubIssue, result: TriageResult) {
  const reasons: string[] = [];

  if (result.route === "needs-info") {
    reasons.push("route-needs-info");
  }

  if (result.route === "core") {
    reasons.push("route-core");
  }

  if (result.priority >= 3 && result.priority <= 4.2) {
    reasons.push("priority-near-threshold");
  }

  if (result.confidence <= 3) {
    reasons.push("confidence-low");
  }

  if (issue.comments >= 4) {
    reasons.push("discussion-heavy");
  }

  if ((issue.body ?? "").length >= 1200) {
    reasons.push("body-long");
  }

  if (result.issueKind === "feature" || result.issueKind === "reward") {
    reasons.push("non-bug-judgment");
  }

  return {
    use: reasons.length > 0,
    reasons,
  };
}

function normalizeMode(raw: string | undefined | null) {
  const value = raw?.trim().toLowerCase();

  if (value === "shadow" || value === "assist") {
    return value;
  }

  return "off";
}

function normalizeUrl(value: string | undefined | null) {
  const trimmed = value?.trim();

  if (!trimmed) {
    return "";
  }

  return trimmed.endsWith("/") ? trimmed.slice(0, -1) : trimmed;
}

function parseInteger(raw: string | undefined, fallback: number) {
  const parsed = Number.parseInt(raw ?? "", 10);
  return Number.isNaN(parsed) ? fallback : parsed;
}

function parseFloatSetting(raw: string | undefined, fallback: number) {
  const parsed = Number.parseFloat(raw ?? "");
  return Number.isNaN(parsed) ? fallback : parsed;
}
