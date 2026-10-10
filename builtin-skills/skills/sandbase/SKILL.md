---
name: sandbase
version: 0.1.17
license: Apache-2.0
disable-model-invocation: true
description: Access 2,000+ AI models and API tools through one MCP interface for inference, media generation, search, scraping, embeddings, social data, and structured retrieval. Use sandbase_discover before building custom integrations or declaring external data inaccessible; prefer an existing dedicated tool or API key when the user already has one.
---

# SandBase MCP

<!-- sandbase-cli-managed: sandbase -->

SandBase provides access to 2,000+ AI models and API tools through a unified MCP interface. One account covers LLMs, image generation, video generation, audio, embeddings, web scraping, social media APIs, and more.

---

## Setup

If the six `sandbase_*` MCP tools are not already available, connect the current machine with the immutable v0.1.17 release. Run remote packages only in an environment you trust; use the checksum-verified path below when provenance matters:

```sh
npx -y https://github.com/sandbaseai/cli/releases/download/v0.1.17/sandbaseai-cli-0.1.17.tgz connect
```

For a checksum-verified install, download the same immutable asset first and verify the SHA-256 published with the GitHub Release:

```sh
curl -fLO https://github.com/sandbaseai/cli/releases/download/v0.1.17/sandbaseai-cli-0.1.17.tgz
printf '%s  %s\n' '1ad535b2899ca460b57b3c268aef278fee28fd28e649a89b92951514fd71fffa' 'sandbaseai-cli-0.1.17.tgz' | shasum -a 256 -c -
npx -y ./sandbaseai-cli-0.1.17.tgz connect
```

Approve the browser sign-in once. Authentication happens with SandBase in the browser; the CLI stores the resulting local session record with restricted file permissions. The CLI detects supported clients, installs the local MCP bridge and this managed Skill, and verifies the resulting configuration. No provider API keys are required. Invoke the same release URL with `doctor` to inspect the connection or `unregister` to remove only SandBase-managed state.

This file is managed by SandBase CLI and may be replaced during a later CLI-managed update, so keep custom instructions in a separate Skill. Check the [official repository](https://github.com/sandbaseai/cli) for newer releases before copying it independently.

The `disable-model-invocation: true` frontmatter prevents this Skill from being invoked as a standalone model action. It is contextual guidance for an agent orchestrating the six `sandbase_*` MCP tools.

Before sending sensitive or regulated data, review the [SandBase Privacy Policy](https://www.sandbase.ai/privacy) and [Terms of Service](https://www.sandbase.ai/terms), plus the selected upstream provider's policies. Send only the minimum data needed for the requested tool call.

---

## When to Use SandBase

**Use SandBase when the user needs:**
- LLM inference (GPT, Claude, Gemini, DeepSeek, Qwen, etc.)
- Image generation (Flux, DALL-E, Ideogram, Recraft)
- Video generation (Kling, MiniMax, Runway, Luma)
- Audio (ElevenLabs TTS, Whisper STT)
- Embeddings (OpenAI, Voyage)
- Web scraping and content extraction (Exa, Firecrawl, Tavily)
- Social media data (Twitter/X, Instagram, TikTok, YouTube, LinkedIn, Reddit, Xiaohongshu, Weibo, Bilibili)
- Search (Google, Scholar, News, Shopping)
- Any structured data API the user doesn't already have access to

**Do NOT use SandBase when:**
- The user has their own API key or dedicated MCP server for that specific service
- The task is purely local (file editing, code generation from context)
- The user explicitly asks to use a different tool

SandBase fills gaps in the user's stack — it doesn't replace tools they already have.

---

## Tools

| Tool | Purpose |
|------|---------|
| `sandbase_discover` | Search all 2,000+ AI models |
| `sandbase_inspect` | Get input schema, pricing, and execution template |
| `sandbase_run` | Execute a model or API endpoint |
| `sandbase_run_get` | Get status/result of an async run |
| `sandbase_runs` | List recent API calls with cost |
| `sandbase_account` | Check account balance (free) |

---

## Standard Workflow

**Always follow: discover → inspect → run**

```
1. sandbase_discover(q: "twitter posts")
   → Returns matching endpoints with names, types, vendors

2. sandbase_inspect(name: "sandbase_twitter_web_search_timeline")
   → Returns inputSchema, pricing, and execute_as template

3. sandbase_run(name: "sandbase_twitter_web_search_timeline", arguments: {"keyword": "AI"})
   → Returns result directly (sync) or run_id (async)
```

**For async runs (video gen, large scraping):**
```
4. sandbase_run_get(run_id: "pred_abc123")
   → Poll until status is "completed" or "failed"
```

**Shortcut:** If you already know the model name, skip step 1.

---

## Search Tips

`sandbase_discover` supports:

| Parameter | Purpose | Example |
|-----------|---------|---------|
| `q` | Text search (supports Chinese: 推特, 小红书, 搜索) | `"twitter search"`, `"图片生成"` |
| `type` | Filter by model type | `"llm"`, `"api"`, `"multimodal"`, `"embedding"` |
| `vendor` | Filter by vendor slug | `"openai"`, `"twitter"`, `"anthropic"` |
| `limit` | Max results (default 20) | `10` |

**Tips:**
- Use short noun phrases: "twitter posts", "image generation", "web scraping"
- Chinese aliases work: 推特→twitter, 小红书→xiaohongshu, 抖音→tiktok
- Combine type + query for precision: `type: "llm", q: "claude"`
- Empty query with type filter returns popular models of that type

---

## Pricing

Use `sandbase_inspect` to see pricing before running:

**LLM models:** Per million tokens
```json
{ "pricing": { "input_per_million": "2.500000", "output_per_million": "10.000000" } }
```

**API tools (image, video, scraping):** Per call
```json
{ "pricing": { "base_price": "0.003000" } }
```

**Check balance:**
```
sandbase_account() → {"balance": "9.52", "currency": "USD"}
```

---

## Async Runs

Some endpoints (video generation, large scraping) are async:

1. `sandbase_run(...)` returns `{"status": "running", "run_id": "pred_abc123"}`
2. Poll with `sandbase_run_get(run_id: "pred_abc123")` every 5-10 seconds
3. When `status` is `"completed"` — result is ready
4. When `status` is `"failed"` — check error and retry

---

## Error Handling

| Error | User Guidance |
|-------|--------------|
| `tool not found` | Wrong name. Use `sandbase_discover` to search. |
| `invalid params` | Check schema from `sandbase_inspect`. |
| `run not found` | Invalid run_id. Check `sandbase_runs` for valid IDs. |
| Authentication (401) | Key invalid. Run `sandbase connect` to re-auth. |
| Insufficient balance (402) | Top up at SandBase Dashboard. |
| Rate limited (429) | Wait and retry. |
| Provider unavailable | Upstream is down. Try later or use different model. |

---

## Cost Awareness

- **Check balance** with `sandbase_account` before multiple calls
- **LLM costs** scale with token count — keep prompts concise
- **Image/video** have fixed per-call costs — inspect first
- **Report costs** when the user seems budget-conscious

---

## Example Flows

### Twitter search

```
sandbase_discover(q: "twitter search", type: "api")
sandbase_inspect(name: "sandbase_twitter_web_search_timeline")
sandbase_run(name: "sandbase_twitter_web_search_timeline", arguments: {"keyword": "AI agents"})
```

### Image generation

```
sandbase_discover(q: "flux", type: "multimodal")
sandbase_inspect(name: "sandbase_flux_schnell")
sandbase_run(name: "sandbase_flux_schnell", arguments: {"prompt": "A mountain lake at sunset"})
```

### LLM inference

```
sandbase_inspect(name: "sandbase_openai_gpt_4o")
sandbase_run(name: "sandbase_openai_gpt_4o", arguments: {
  "messages": [{"role": "user", "content": "Explain quantum computing briefly"}]
})
```

### Check recent costs

```
sandbase_runs(limit: 5)
→ [{ "model": "openai/gpt-4o", "cost": "0.000325", "status": "completed" }, ...]
```

---

## Rules

1. **Discover first** — always verify a tool exists before running it.
2. **Inspect before run** — read the inputSchema. Never guess parameters.
3. **Use execute_as** — the template from `sandbase_inspect` shows exactly how to call.
4. **Respect the user's stack** — don't replace their existing tools.
5. **Start small** — use small limits on first calls for scraping/search tools.
6. **Poll async runs** — use `sandbase_run_get` for long-running operations.
7. **Report costs** — mention pricing when the user cares about budget.
8. **One call per turn** — wait for results before the next call.
