# Resolve 命令命名空间交互选择实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 当 resolve 命令未指定命名空间且存在多个匹配时，显示交互式选择器

**Architecture:** 抽取 explore.ts 中的 runInteractiveSearch 为共享模块，resolve.ts 在检测到多命名空间匹配时复用该选择器

**Tech Stack:** TypeScript, Commander.js, Node.js readline

---

## 文件结构

```
skillhub-cli/src/
├── core/
│   └── interactive-search.ts    # 新建: 共享交互选择器
└── commands/
    ├── explore.ts               # 修改: 使用共享模块
    └── resolve.ts              # 修改: 添加交互选择逻辑
```

---

## Task 1: 创建共享交互选择模块

**Files:**
- Create: `skillhub-cli/src/core/interactive-search.ts`

- [ ] **Step 1: 创建文件并实现 runInteractiveSearch 函数**

```typescript
// skillhub-cli/src/core/interactive-search.ts
import { ApiClient } from "./api-client.js";
import { ApiRoutes, SearchResponse } from "../schema/routes.js";
import * as readline from "readline";
import { dim, info } from "../utils/logger.js";

const HIDE_CURSOR = "\x1b[?25l";
const SHOW_CURSOR = "\x1b[?25h";
const CLEAR_DOWN = "\x1b[J";
const MOVE_UP = (n: number) => `\x1b[${n}A`;
const MOVE_TO_COL = (n: number) => `\x1b[${n}G`;

const RESET = "\x1b[0m";
const BOLD = "\x1b[1m";
const TEXT = "\x1b[38;5;145m";
const YELLOW = "\x1b[33m";
const DIM = "\x1b[38;5;102m";

export interface SearchSkill {
  name: string;
  slug: string;
  namespace: string;
  version?: string;
  summary?: string;
  installs?: number;
}

interface SkillDetail {
  starCount: number;
  downloadCount: number;
  version: string;
}

function parseNamespace(slug: string): { namespace: string; name: string } {
  const parts = slug.split("--");
  if (parts.length >= 2) {
    return { namespace: parts[0], name: parts.slice(1).join("--") };
  }
  return { namespace: "global", name: slug };
}

function formatInstalls(count: number): string {
  if (!count || count <= 0) return "";
  if (count >= 1_000_000) return `${(count / 1_000_000).toFixed(1).replace(/\.0$/, "")}M installs`;
  if (count >= 1_000) return `${(count / 1_000).toFixed(1).replace(/\.0$/, "")}K installs`;
  return `${count} install${count === 1 ? "" : "s"}`;
}

async function fetchSkillDetail(client: ApiClient, namespace: string, name: string): Promise<SkillDetail | null> {
  try {
    const detail = await client.get<SkillDetail>(
      `${ApiRoutes.skillDetail.replace("{namespace}", namespace).replace("{slug}", name)}`
    );
    return detail;
  } catch {
    return null;
  }
}

export async function searchSkills(
  client: ApiClient,
  query: string,
  limit: number = 10
): Promise<SearchSkill[]> {
  const result = await client.get<SearchResponse>(
    `${ApiRoutes.search}?q=${encodeURIComponent(query)}&limit=${limit}`
  );

  if (!result.results || result.results.length === 0) {
    return [];
  }

  return result.results.map((s) => {
    const { namespace, name } = parseNamespace(s.slug);
    return {
      name,
      slug: s.slug,
      namespace,
      version: s.version,
      summary: s.summary,
      installs: (s as any).installCount || 0,
    };
  }).sort((a, b) => (b.installs || 0) - (a.installs || 0));
}

export async function runInteractiveSearch(
  client: ApiClient,
  initialQuery: string = ""
): Promise<string | null> {
  const MAX_VISIBLE = 8;
  let query = initialQuery;
  let results: SearchSkill[] = [];
  let selectedIndex = 0;
  let loading = false;
  let lastRenderedLines = 0;
  let debounceTimer: ReturnType<typeof setTimeout> | null = null;

  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });

  const width = process.stdout.columns || 80;

  if (process.stdin.isTTY) {
    process.stdin.setRawMode(true);
  }
  process.stdin.resume();
  readline.emitKeyEvents(process.stdin);
  process.stdout.write(HIDE_CURSOR);

  function render(): void {
    if (lastRenderedLines > 0) {
      process.stdout.write(MOVE_UP(lastRenderedLines) + MOVE_TO_COL(1));
    }
    process.stdout.write(CLEAR_DOWN);

    const lines: string[] = [];

    const cursor = `${BOLD}_${RESET}`;
    const searchLine = `${TEXT}Select namespace:${RESET} ${query}${cursor}`;
    lines.push(searchLine);
    lines.push("");

    if (!query || query.length < 2) {
      lines.push(`${DIM}Start typing to search (min 2 chars)${RESET}`);
    } else if (results.length === 0 && loading) {
      lines.push(`${DIM}Searching...${RESET}`);
    } else if (results.length === 0) {
      lines.push(`${DIM}No skills found${RESET}`);
    } else {
      const visible = results.slice(0, MAX_VISIBLE);
      for (let i = 0; i < visible.length; i++) {
        const skill = visible[i]!;
        const isSelected = i === selectedIndex;
        const arrow = isSelected ? `${BOLD}>${RESET}` : " ";
        const name = isSelected ? `${BOLD}${skill.name}${RESET}` : `${TEXT}${skill.name}${RESET}`;
        const nsBadge = skill.namespace !== "global" ? ` ${YELLOW}[${skill.namespace}]${RESET}` : "";
        const versionBadge = skill.version ? ` ${DIM}v${skill.version}${RESET}` : "";

        lines.push(`  ${arrow} ${name}${nsBadge}${versionBadge}`);
      }
    }

    lines.push("");
    lines.push(`${DIM}up/down navigate | enter select | esc cancel${RESET}`);

    for (const line of lines) {
      process.stdout.write(line + "\n");
    }

    lastRenderedLines = lines.length;
  }

  function triggerSearch(q: string): void {
    if (debounceTimer) {
      clearTimeout(debounceTimer);
      debounceTimer = null;
    }

    loading = false;

    if (!q || q.length < 2) {
      results = [];
      selectedIndex = 0;
      render();
      return;
    }

    loading = true;
    render();

    const debounceMs = Math.max(150, 350 - q.length * 50);

    debounceTimer = setTimeout(async () => {
      try {
        results = await searchSkills(client, q);
        selectedIndex = 0;
      } catch {
        results = [];
      } finally {
        loading = false;
        debounceTimer = null;
        render();
      }
    }, debounceMs);
  }

  if (initialQuery) {
    triggerSearch(initialQuery);
  }
  render();

  return new Promise<string | null>((resolve) => {
    function cleanup(): void {
      process.stdin.removeListener("keypress", handleKeypress);
      if (process.stdin.isTTY) {
        process.stdin.setRawMode(false);
      }
      process.stdout.write(SHOW_CURSOR);
      process.stdin.pause();
      rl.close();
    }

    function handleKeypress(_ch: string | undefined, key: readline.Key): void {
      if (!key) return;

      if (key.name === "escape" || (key.ctrl && key.name === "c")) {
        cleanup();
        resolve(null);
        return;
      }

      if (key.name === "return") {
        cleanup();
        resolve(results[selectedIndex] ? `${results[selectedIndex].namespace}/${results[selectedIndex].name}` : null);
        return;
      }

      if (key.name === "up" || key.name === "k") {
        selectedIndex = Math.max(0, selectedIndex - 1);
        render();
        return;
      }

      if (key.name === "down" || key.name === "j") {
        selectedIndex = Math.min(Math.max(0, results.length - 1), selectedIndex + 1);
        render();
        return;
      }

      if (key.name === "backspace") {
        if (query.length > 0) {
          query = query.slice(0, -1);
          triggerSearch(query);
        }
        return;
      }

      if (key.sequence && !key.ctrl && !key.meta && key.sequence.length === 1) {
        const char = key.sequence;
        if (char >= " " && char <= "~") {
          query += char;
          triggerSearch(query);
        }
      }
    }

    process.stdin.on("keypress", handleKeypress);
  });
}
```

- [ ] **Step 2: 验证文件创建成功**

Run: `ls -la skillhub-cli/src/core/interactive-search.ts`
Expected: 文件存在

- [ ] **Step 3: 构建项目验证无语法错误**

Run: `cd skillhub-cli && npm run build 2>&1`
Expected: Build succeeded

- [ ] **Step 4: 提交**

```bash
git add skillhub-cli/src/core/interactive-search.ts
git commit -m "feat(cli): extract interactive search to shared module"
```

---

## Task 2: 修改 explore.ts 使用共享模块

**Files:**
- Modify: `skillhub-cli/src/commands/explore.ts:1-331`

- [ ] **Step 1: 读取当前 explore.ts 确认需要修改的部分**

查看文件结构，identify哪些函数需要替换或保留

- [ ] **Step 2: 修改 explore.ts 导入共享模块**

在文件开头添加 import，并移除本地的 runInteractiveSearch 和相关辅助函数，改为使用共享模块

```typescript
// skillhub-cli/src/commands/explore.ts
import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { ApiRoutes } from "../schema/routes.js";
import { info, dim } from "../utils/logger.js";
import { execSync } from "node:child_process";
import { runInteractiveSearch, searchSkills } from "../core/interactive-search.js";
```

- [ ] **Step 3: 验证构建**

Run: `cd skillhub-cli && npm run build 2>&1`
Expected: Build succeeded

- [ ] **Step 4: 提交**

```bash
git add skillhub-cli/src/commands/explore.ts
git commit -m "refactor(cli): use shared interactive-search module"
```

---

## Task 3: 修改 resolve.ts 添加交互选择逻辑

**Files:**
- Modify: `skillhub-cli/src/commands/resolve.ts:1-56`

- [ ] **Step 1: 添加 import**

```typescript
import { runInteractiveSearch, searchSkills } from "../core/interactive-search.js";
```

- [ ] **Step 2: 修改 action 逻辑**

在 parseSkillName 之后，API 调用之前，添加交互选择逻辑：

```typescript
.action(async (slug: string, opts: Record<string, string>) => {
  try {
    const { namespace, slug: skillSlug } = parseSkillName(slug);
    const config = loadConfig();
    const token = await readToken();
    const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

    let targetNamespace = namespace;
    let targetSlug = skillSlug;

    // 如果未指定命名空间，搜索所有命名空间的匹配项
    if (namespace === "global") {
      const results = await searchSkills(client, skillSlug, 50);
      
      // 按 namespace + name 去重
      const seen = new Set<string>();
      const uniqueResults = results.filter(r => {
        const key = `${r.namespace}/${r.name}`;
        if (!seen.has(key)) {
          seen.add(key);
          return true;
        }
        return false;
      });

      if (uniqueResults.length === 0) {
        error(`Skill not found: ${skillSlug}`);
        process.exit(1);
      }

      if (uniqueResults.length === 1) {
        // 只有一个匹配，直接使用
        targetNamespace = uniqueResults[0].namespace;
        targetSlug = uniqueResults[0].name;
      } else {
        // 多个匹配，显示交互选择器
        const selected = await runInteractiveSearch(client, skillSlug);
        if (!selected) {
          info("Cancelled.");
          return;
        }
        const [ns, name] = selected.split("/", 2);
        targetNamespace = ns;
        targetSlug = name;
      }
    }

    const params = new URLSearchParams();
    if (opts["skill-version"]) {
      params.set("version", opts["skill-version"]);
    } else if (opts.tag) {
      params.set("tag", opts.tag);
    }
    if (opts.hash) params.set("hash", opts.hash);

    const qs = params.toString();
    const path = `/api/v1/skills/${targetNamespace}/${targetSlug}/resolve${qs ? "?" + qs : ""}`;
    const result = await client.get<ResolveResponse>(path);

    info(`${result.slug}@${result.version}`);
    dim(`Namespace:    ${result.namespace}`);
    dim(`Version ID:   ${result.versionId}`);
    dim(`Fingerprint:  ${result.fingerprint}`);
    dim(`Matched:      ${result.matched}`);
    dim(`Download URL: ${result.downloadUrl}`);
  } catch (e: any) {
    error(`Failed: ${e.message}`);
    process.exit(1);
  }
});
```

- [ ] **Step 3: 验证构建**

Run: `cd skillhub-cli && npm run build 2>&1`
Expected: Build succeeded

- [ ] **Step 4: 测试交互选择功能**

需要手动测试或添加集成测试

- [ ] **Step 5: 提交**

```bash
git add skillhub-cli/src/commands/resolve.ts
git commit -m "feat(cli): add interactive namespace selection to resolve command"
```

---

## Task 4: 更新测试文档

**Files:**
- Modify: `skillhub-cli/docs/MANUAL_TEST_GUIDE.md`

- [ ] **Step 1: 添加 resolve 交互选择的测试用例**

在 3.2.4 resolve 部分添加新的测试用例：

```
| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| RESOLVE-05 | `resolve <slug>` (单命名空间) | 只有一个匹配 | 直接解析 |
| RESOLVE-06 | `resolve <slug>` (多命名空间) | 多个命名空间有同名 skill | 显示交互选择器 |
| RESOLVE-07 | `resolve <slug>` 取消选择 | 用户按 ESC | 显示 Cancelled |
```

- [ ] **Step 2: 提交**

```bash
git add skillhub-cli/docs/MANUAL_TEST_GUIDE.md
git commit -m "docs: add resolve interactive selection test cases"
```

---

## 验证清单

- [ ] `npm run build` 成功
- [ ] `node dist/cli.mjs resolve openspec` (只有一个匹配时) 直接解析
- [ ] `node dist/cli.mjs resolve openspec` (多个匹配时) 显示选择器
- [ ] 选择后正确显示解析结果
- [ ] 按 ESC 取消时显示 Cancelled
- [ ] 显式指定命名空间 `resolve vision2group/openspec` 行为不变
