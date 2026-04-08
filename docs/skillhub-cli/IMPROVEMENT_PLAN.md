# SkillHub CLI 改进计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 改进 SkillHub CLI，修复已知 bug，增加缺失功能，对标 vercel-labs/skills 和 clawhub

**Architecture:** 基于 commander.js 的 Node.js CLI，使用 undici 作为 HTTP 客户端

**Tech Stack:** TypeScript, Node.js, commander.js, undici, ora, chalk, semver

---

## 问题汇总

### 已验证的 Bug

| # | 问题 | 严重性 | 位置 |
|---|------|--------|------|
| 1 | symlink vs copy 模式错误 - installer.ts 总是使用 copy | 高 | `src/core/installer.ts:36-42` |
| 2 | namespace/slug 解析错误 - `info global/test` 会构造错误 URL | 高 | `src/commands/info.ts` 及相关命令 |
| 3 | API routes 未集中管理 - 部分使用硬编码路径 | 中 | `src/schema/routes.ts` |

### 缺失功能 (对标 clawhub)

| # | 功能 | 优先级 |
|---|------|--------|
| 1 | `--json` 输出选项 | 高 |
| 2 | `explore` 命令 (浏览最新 skills) | 中 |
| 3 | `inspect` 命令 (查看元数据不安装) | 中 |
| 4 | `--force` 选项 (强制覆盖安装) | 中 |

### 后端问题 (需后端修复)

| # | 问题 | CLI 状态 |
|---|------|----------|
| 1 | `/api/v1/notifications` API Token 无权限 | CLI 正常，后端需修复 |
| 2 | `/api/v1/reviews/submissions` API Token 无权限 | CLI 正常，后端需修复 |

---

## 任务清单

### Task 1: 修复 symlink vs copy Bug

**Files:**
- Modify: `skillhub-cli/src/core/installer.ts:36-42`

- [ ] **Step 1: 理解问题** - 查看当前代码逻辑

```typescript
// 当前代码 (错误):
if (mode === "symlink") {
  copyDir(skillDir, skillTargetDir);  // 错误！应该是 symlinkSync
} else {
  copyDir(skillDir, skillTargetDir);
}
return { skillName, agentKey, path: skillTargetDir, mode: "copy", success: true };  // 总是返回 "copy"
```

- [ ] **Step 2: 修复代码** - 正确实现 symlink 和 copy 模式

```typescript
// 修复后:
if (mode === "symlink") {
  symlinkSync(skillDir, skillTargetDir);
} else {
  copyDir(skillDir, skillTargetDir);
}
return { skillName, agentKey, path: skillTargetDir, mode, success: true };
```

- [ ] **Step 3: 验证修复** - 运行测试

```bash
cd skillhub-cli && npm test
```

---

### Task 2: 修复 namespace/slug 解析

**Files:**
- Modify: `skillhub-cli/src/commands/info.ts`
- Modify: `skillhub-cli/src/commands/versions.ts`
- Modify: `skillhub-cli/src/commands/resolve.ts`
- Modify: `skillhub-cli/src/commands/download.ts`
- Modify: `skillhub-cli/src/core/skill-name.ts` (可能需要新增)

- [ ] **Step 1: 创建 skill-name 解析工具函数**

```typescript
// src/core/skill-name.ts
export interface ParsedSkillName {
  namespace: string;
  slug: string;
}

export function parseSkillName(input: string, defaultNamespace = "global"): ParsedSkillName {
  const parts = input.split("/");
  if (parts.length === 2) {
    return { namespace: parts[0], slug: parts[1] };
  }
  return { namespace: defaultNamespace, slug: input };
}
```

- [ ] **Step 2: 更新 info.ts**

```typescript
// 在 info.ts 中:
import { parseSkillName } from "../core/skill-name.js";

// 在 action 中:
const { namespace, slug } = parseSkillName(slugArg);
const skill = await client.get<SkillDetailResponse>(
  `${ApiRoutes.skillDetail.replace("{namespace}", namespace).replace("{slug}", slug)}`
);
```

- [ ] **Step 3: 同样更新 versions.ts, resolve.ts, download.ts**

- [ ] **Step 4: 验证修复**

```bash
cd skillhub-cli && npm run build
node dist/cli.mjs info global/test  # 应该正确工作
node dist/cli.mjs versions global/test  # 应该正确工作
```

---

### Task 3: 集中管理 API Routes

**Files:**
- Modify: `skillhub-cli/src/schema/routes.ts`

- [ ] **Step 1: 扩展 routes.ts**

```typescript
// 新增 routes:
export const ApiRoutes = {
  // ... 现有 routes
  // 用户相关
  meSkills: "/api/v1/me/skills",
  meStars: "/api/v1/me/stars",
  // 通知
  notifications: "/api/v1/notifications",
  notificationRead: (id: string) => `/api/v1/notifications/${id}/read`,
  notificationsReadAll: "/api/v1/notifications/read-all",
  // 审核
  reviewsSubmissions: "/api/v1/reviews/submissions",
  // 评分
  skillRate: (ns: string, slug: string) => `/api/v1/skills/${ns}/${slug}/rating`,
  // 举报
  skillReport: (ns: string, slug: string) => `/api/v1/skills/${ns}/${slug}/reports`,
} as const;
```

- [ ] **Step 2: 更新使用硬编码路径的命令**

检查以下命令是否使用硬编码:
- `me.ts` - 使用 `/api/v1/me/skills`
- `notifications.ts` - 使用 `/api/v1/notifications`
- `reviews.ts` - 使用 `/api/v1/reviews/submissions`
- `rating.ts` - 使用 `/api/v1/skills/${ns}/${slug}/rating`

- [ ] **Step 3: 运行测试验证**

```bash
cd skillhub-cli && npm test
```

---

### Task 4: 添加 --json 输出选项

**Files:**
- Modify: `skillhub-cli/src/cli.ts` (全局选项)
- Modify: `skillhub-cli/src/commands/search.ts`
- Modify: `skillhub-cli/src/commands/info.ts`
- Modify: `skillhub-cli/src/commands/list.ts`

- [ ] **Step 1: 在 cli.ts 中添加全局 JSON 选项**

```typescript
// 在全局选项中添加:
program.option('--json', 'Output as JSON');
```

- [ ] **Step 2: 更新 search.ts 支持 JSON 输出**

```typescript
// 在 search action 中:
const results = await client.get<SearchResponse>(...);
if (opts.json) {
  console.log(JSON.stringify(results, null, 2));
} else {
  // 现有格式化输出
}
```

- [ ] **Step 3: 同样更新 info.ts, list.ts**

- [ ] **Step 4: 测试 JSON 输出**

```bash
cd skillhub-cli && npm run build
node dist/cli.mjs search test --json
node dist/cli.mjs info test --json
```

---

### Task 5: 添加 --force 选项到 install 和 add 命令

**Files:**
- Modify: `skillhub-cli/src/commands/install.ts`
- Modify: `skillhub-cli/src/commands/add.ts`

- [ ] **Step 1: 在 install.ts 中添加 --force 选项**

```typescript
.option("-f, --force", "Force reinstall even if already installed")
```

- [ ] **Step 2: 在 installSkill 调用前检查 force 选项**

- [ ] **Step 3: 同样更新 add.ts**

- [ ] **Step 4: 测试**

```bash
cd skillhub-cli && npm run build
node dist/cli.mjs install test --force
```

---

### Task 6: 实现 explore 命令 (浏览最新 skills)

**Files:**
- Create: `skillhub-cli/src/commands/explore.ts`
- Modify: `skillhub-cli/src/cli.ts`

- [ ] **Step 1: 创建 explore.ts**

```typescript
import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { loadConfig } from "../core/config.js";
import { info, dim } from "../utils/logger.js";

export function registerExplore(program: Command) {
  program
    .command("explore")
    .description("Browse latest updated skills from the registry")
    .option("-n, --limit <n>", "Max results", "20")
    .action(async (opts) => {
      const config = loadConfig();
      const client = new ApiClient({ baseUrl: config.registry });
      // 调用探索 API
    });
}
```

- [ ] **Step 2: 在 cli.ts 中注册命令**

- [ ] **Step 3: 测试**

```bash
cd skillhub-cli && npm run build
node dist/cli.mjs explore
```

---

### Task 7: 实现 inspect 命令 (查看元数据不安装)

**Files:**
- Create: `skillhub-cli/src/commands/inspect.ts`
- Modify: `skillhub-cli/src/cli.ts`

- [ ] **Step 1: 创建 inspect.ts**

- [ ] **Step 2: 测试**

```bash
cd skillhub-cli && npm run build
node dist/cli.mjs inspect test
```

---

## 验证步骤

所有任务完成后，运行以下验证:

```bash
cd skillhub-cli

# 1. 构建
npm run build

# 2. 单元测试
npm test

# 3. 类型检查
npm run typecheck

# 4. 功能验证
node dist/cli.mjs --help
node dist/cli.mjs info global/test
node dist/cli.mjs versions global/test
node dist/cli.mjs search test --json
```

---

## 预估工时

| Task | 预估时间 | 依赖 |
|------|----------|------|
| Task 1: symlink bug | 15 分钟 | 无 |
| Task 2: namespace/slug parsing | 30 分钟 | Task 1 |
| Task 3: API routes 集中化 | 30 分钟 | Task 2 |
| Task 4: --json 选项 | 30 分钟 | Task 3 |
| Task 5: --force 选项 | 20 分钟 | Task 1 |
| Task 6: explore 命令 | 45 分钟 | Task 3 |
| Task 7: inspect 命令 | 30 分钟 | Task 3 |

**总计: ~3 小时**
