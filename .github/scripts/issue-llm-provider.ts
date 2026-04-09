import { IssueLlmConfig } from "./issue-llm-types.ts";

interface OpenAiCompatibleResponse {
  choices?: Array<{
    message?: {
      content?: string | Array<{ type?: string; text?: string }>;
    };
  }>;
}

export async function requestOpenAiCompatibleJson(
  config: IssueLlmConfig,
  systemPrompt: string,
  userPrompt: string,
) {
  let lastError: Error | null = null;

  for (let attempt = 1; attempt <= config.maxAttempts; attempt += 1) {
    try {
      return await requestOnce(config, systemPrompt, userPrompt);
    } catch (error) {
      const normalized = normalizeRequestError(
        error,
        attempt,
        config.maxAttempts,
      );
      lastError = normalized;

      if (!shouldRetry(error) || attempt >= config.maxAttempts) {
        throw normalized;
      }

      await sleep(resolveRetryDelay(error, config.retryBackoffMs, attempt));
    }
  }

  throw lastError ?? new Error("LLM request failed for an unknown reason.");
}

async function requestOnce(
  config: IssueLlmConfig,
  systemPrompt: string,
  userPrompt: string,
) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.timeoutMs);

  try {
    const response = await fetch(`${config.baseUrl}/chat/completions`, {
      method: "POST",
      signal: controller.signal,
      headers: {
        Authorization: `Bearer ${config.apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: config.model,
        temperature: config.temperature,
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: userPrompt },
        ],
      }),
    });

    if (!response.ok) {
      const message =
        `LLM request failed with status ${response.status}: ${await response
          .text()}`;
      throw new RetryableHttpError(
        message,
        response.status,
        response.headers.get("retry-after"),
      );
    }

    const payload = (await response.json()) as OpenAiCompatibleResponse;
    const content = payload.choices?.[0]?.message?.content;
    const text = normalizeMessageContent(content);

    if (!text) {
      throw new Error("LLM response did not include message content.");
    }

    return extractJsonObject(text);
  } finally {
    clearTimeout(timeout);
  }
}

class RetryableHttpError extends Error {
  status: number;
  retryAfterSeconds: number | null;

  constructor(
    message: string,
    status: number,
    retryAfterHeader: string | null,
  ) {
    super(message);
    this.name = "RetryableHttpError";
    this.status = status;
    this.retryAfterSeconds = parseRetryAfterSeconds(retryAfterHeader);
  }
}

function shouldRetry(error: unknown) {
  if (error instanceof RetryableHttpError) {
    return error.status === 408 || error.status === 429 || error.status >= 500;
  }

  if (error instanceof DOMException && error.name === "AbortError") {
    return true;
  }

  if (error instanceof Error) {
    const message = error.message.toLowerCase();
    return message.includes("network") || message.includes("connection");
  }

  return false;
}

function normalizeRequestError(
  error: unknown,
  attempt: number,
  maxAttempts: number,
) {
  if (error instanceof DOMException && error.name === "AbortError") {
    return new Error(
      `LLM request timed out on attempt ${attempt}/${maxAttempts}.`,
    );
  }

  if (error instanceof RetryableHttpError) {
    return new Error(
      `LLM request failed on attempt ${attempt}/${maxAttempts}: ${error.message}`,
    );
  }

  if (error instanceof Error) {
    return new Error(
      `LLM request failed on attempt ${attempt}/${maxAttempts}: ${error.message}`,
    );
  }

  return new Error(
    `LLM request failed on attempt ${attempt}/${maxAttempts}: ${String(error)}`,
  );
}

function resolveRetryDelay(
  error: unknown,
  retryBackoffMs: number,
  attempt: number,
) {
  if (error instanceof RetryableHttpError && error.retryAfterSeconds !== null) {
    return error.retryAfterSeconds * 1000;
  }

  return retryBackoffMs * attempt;
}

function parseRetryAfterSeconds(value: string | null) {
  if (!value) {
    return null;
  }

  const seconds = Number.parseInt(value, 10);
  return Number.isNaN(seconds) ? null : Math.max(0, seconds);
}

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function normalizeMessageContent(
  content: string | Array<{ type?: string; text?: string }> | undefined,
) {
  if (!content) {
    return "";
  }

  if (typeof content === "string") {
    return content;
  }

  return content
    .map((item) => item.text ?? "")
    .join("\n")
    .trim();
}

function extractJsonObject(text: string) {
  const trimmed = text.trim();
  const fenced = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/i);
  const candidate = fenced?.[1]?.trim() ?? trimmed;
  const start = candidate.indexOf("{");
  const end = candidate.lastIndexOf("}");

  if (start < 0 || end < 0 || end <= start) {
    throw new Error(`LLM response did not contain a JSON object: ${trimmed}`);
  }

  return candidate.slice(start, end + 1);
}
