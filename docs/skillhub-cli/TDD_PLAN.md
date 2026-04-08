# @motovis/skillhub TDD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以 skillhub-cli 为核心，扩展到 @motovis/skillhub，支持命名空间、41+ agents，并发布到 npm。

**Architecture:** 基于 commander.js 的 Node.js CLI，使用 undici 作为 HTTP 客户端

**Tech Stack:** TypeScript, Node.js, commander.js, undici, vitest

---

## 项目文件结构

```
skillhub-cli/
├── package.json              # npm 发布配置 (@motovis/skillhub)
├── src/
│   ├── cli.ts              # Commander 入口
│   ├── commands/
│   │   ├── namespaces.ts    # Phase 1.1: 验证
│   │   ├── search.ts       # Phase 1.2, 1.3: --namespace, 跨命名空间
│   │   ├── install.ts      # Phase 1.2: --namespace
│   │   ├── inspect.ts       # Phase 1.2: --namespace
│   │   ├── publish.ts       # Phase 1.2: --namespace
│   │   ├── add.ts           # Phase 2.2: 验证
│   │   ├── update.ts        # Phase 3.1: 新增
│   │   ├── uninstall.ts     # Phase 3.2: 新增
│   │   └── sync.ts         # Phase 3.3: 新增
│   └── core/
│       ├── agent-detector.ts # Phase 2.1: 扩展到 41+ agents
│       ├── api-client.ts    # HTTP 客户端
│       └── skill-name.ts    # 命名空间解析
└── tests/                   # TDD 测试
```

---

## Task 0: npm 发布配置

**Files:**
- Modify: `skillhub-cli/package.json`

- [ ] **Step 1: 更新 package.json 发布配置**

```json
{
  "name": "@motovis/skillhub",
  "version": "1.0.0",
  "description": "SkillHub CLI - 企业级 Agent Skill 管理工具，支持命名空间",
  "bin": {
    "skillhub": "dist/cli.mjs"
  },
  "publishConfig": {
    "access": "public",
    "registry": "https://registry.npmjs.org/"
  }
}
```

- [ ] **Step 2: 构建并验证**

Run: `cd skillhub-cli && npm run build`
Run: `node dist/cli.mjs --version` (确认 bin 工作)

- [ ] **Step 3: Commit**

```bash
git add skillhub-cli/package.json
git commit -m "feat(cli): rename to @motovis/skillhub for npm publish"
```

---

## Task 1.1: 验证 namespaces 命令

**Files:**
- Modify: `skillhub-cli/src/commands/namespaces.ts`
- Test: `skillhub-cli/tests/commands/namespaces.test.ts`

- [ ] **Step 1: 编写失败的测试**

```typescript
// tests/commands/namespaces.test.ts
import { describe, it, expect, vi } from 'vitest'
import { ApiClient } from '../../src/core/api-client.js'

describe('namespaces command', () => {
  it('should call /api/v1/me/namespaces endpoint', async () => {
    const mockGet = vi.fn().mockResolvedValue({
      code: 0,
      data: [{ name: 'global' }, { name: 'team-a' }]
    })
    vi.spyOn(ApiClient.prototype, 'get').mockImplementation(mockGet)
    
    // 验证输出包含 namespaces 列表
    // ...
  })
})
```

Run: `cd skillhub-cli && npm test -- namespaces.test.ts`
Expected: FAIL (namespaces command not implemented yet)

- [ ] **Step 2: 验证现有 namespaces.ts 实现**

检查 `src/commands/namespaces.ts` 是否正确调用 `ApiRoutes.meNamespaces`

- [ ] **Step 3: 如有问题，修复实现**

确保调用: `client.get(ApiRoutes.meNamespaces)`

- [ ] **Step 4: 运行测试**

Run: `npm test -- namespaces.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add skillhub-cli/src/commands/namespaces.ts skillhub-cli/tests/commands/namespaces.test.ts
git commit -m "test(cli): add namespaces command tests"
```

---

## Task 1.2: 验证 --namespace 全局选项

**Files:**
- Modify: `skillhub-cli/src/cli.ts` (如果需要)
- Modify: `skillhub-cli/src/commands/search.ts`
- Modify: `skillhub-cli/src/commands/install.ts`
- Modify: `skillhub-cli/src/commands/inspect.ts`
- Modify: `skillhub-cli/src/commands/publish.ts`
- Test: `skillhub-cli/tests/commands/namespace-option.test.ts`

- [ ] **Step 1: 编写失败的测试**

```typescript
// tests/commands/namespace-option.test.ts
describe('--namespace option', () => {
  it('search should accept --namespace flag', async () => {
    const result = await executeCli(['search', 'test', '--namespace', 'global'])
    expect(result.stdout).toContain('global')
  })
  
  it('install should accept --namespace flag', async () => {
    const result = await executeCli(['install', 'openspec', '--namespace', 'team-a'])
    expect(result.code).toBe(0)
  })
})
```

Run: `npm test -- namespace-option.test.ts`
Expected: FAIL (--namespace not supported)

- [ ] **Step 2: 检查现有实现**

检查各命令是否已支持 `--namespace` 选项

- [ ] **Step 3: 如有缺失，添加到 cli.ts 全局选项**

```typescript
// src/cli.ts
program
  .option('-n, --namespace <ns>', 'Target namespace')
```

- [ ] **Step 4: 运行测试**

Run: `npm test -- namespace-option.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add skillhub-cli/src/cli.ts skillhub-cli/tests/commands/namespace-option.test.ts
git commit -m "feat(cli): add --namespace global option"
```

---

## Task 1.3: 验证跨命名空间搜索

**Files:**
- Modify: `skillhub-cli/src/commands/search.ts`
- Test: `skillhub-cli/tests/commands/cross-namespace-search.test.ts`

- [ ] **Step 1: 编写失败的测试**

```typescript
// tests/commands/cross-namespace-search.test.ts
describe('cross-namespace search', () => {
  it('should search across all namespaces when --namespace not provided', async () => {
    const mockGet = vi.fn().mockResolvedValue({
      code: 0,
      data: [
        { name: 'skill1', namespace: 'global' },
        { name: 'skill2', namespace: 'team-a' }
      ]
    })
    
    const result = await executeCli(['search', 'test'])
    expect(result.stdout).toContain('global')
    expect(result.stdout).toContain('team-a')
  })
})
```

Run: `npm test -- cross-namespace-search.test.ts`
Expected: FAIL (may not show namespace in results)

- [ ] **Step 2: 检查 search.ts 实现**

确保搜索结果中包含 namespace 信息

- [ ] **Step 3: 如有问题，修复输出格式**

修改 search.ts 在结果中显示 namespace

- [ ] **Step 4: 运行测试**

Run: `npm test -- cross-namespace-search.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add skillhub-cli/src/commands/search.ts skillhub-cli/tests/commands/cross-namespace-search.test.ts
git commit -m "feat(cli): show namespace in search results"
```

---

## Task 2.1: 扩展 agent-detector 到 41+ agents

**Files:**
- Modify: `skillhub-cli/src/core/agent-detector.ts`
- Test: `skillhub-cli/tests/core/agent-detector.test.ts`

- [ ] **Step 1: 编写失败的测试**

```typescript
// tests/core/agent-detector.test.ts
describe('agent-detector', () => {
  it('should support 41+ agents from vercel-labs/skills', async () => {
    const detector = new AgentDetector()
    const agents = await detector.getSupportedAgents()
    
    // vercel-labs/skills agents
    expect(agents).toContain('claude')
    expect(agents).toContain('openai')
    expect(agents).toContain('gemini')
    expect(agents).toContain('mistral')
    expect(agents).toContain('anthropic')
    expect(agents.length).toBeGreaterThanOrEqual(41)
  })
})
```

Run: `npm test -- agent-detector.test.ts`
Expected: FAIL (only 16 agents currently)

- [ ] **Step 2: 查看当前 agent-detector.ts 实现**

Read: `skillhub-cli/src/core/agent-detector.ts`

- [ ] **Step 3: 参考 clawhub 的 agent 列表**

clawhub 源码: `/home/chenbaowang/.npm/_npx/a92a6dbcf543fba6/node_modules/clawhub/dist/`

```typescript
// 参考添加以下 agents:
const VERCELELABS_AGENTS = [
  'claude', 'openai', 'gemini', 'mistral', 'anthropic',
  'cohere', 'ai21', 'stability', 'deepseek', 'local',
  'ollama', 'llama', 'codellama', 'wizardcoder', 'phi',
  // ... 共 41+
]
```

- [ ] **Step 4: 运行测试**

Run: `npm test -- agent-detector.test.ts`
Expected: PASS (41+ agents)

- [ ] **Step 5: Commit**

```bash
git add skillhub-cli/src/core/agent-detector.ts skillhub-cli/tests/core/agent-detector.test.ts
git commit -m "feat(cli): expand agent support to 41+ agents from vercel-labs/skills"
```

---

## Task 2.2: 验证 add 命令

**Files:**
- Modify: `skillhub-cli/src/commands/add.ts`
- Test: `skillhub-cli/tests/commands/add.test.ts`

- [ ] **Step 1: 编写失败的测试**

```typescript
// tests/commands/add.test.ts
describe('add command', () => {
  it('should accept GitHub shorthand source', async () => {
    const result = await executeCli(['add', 'vercel/skills'])
    expect(result.code).toBe(0)
  })
  
  it('should accept --agent flag', async () => {
    const result = await executeCli(['add', 'vercel/skills', '--agent', 'claude'])
    expect(result.code).toBe(0)
  })
})
```

Run: `npm test -- add.test.ts`
Expected: FAIL or PASS (取决于实现)

- [ ] **Step 2: 检查 add.ts 实现**

确保支持:
- GitHub shorthand (e.g., `vercel/skills`)
- URL sources
- Local paths
- `--agent` 选项

- [ ] **Step 3: 如有问题，修复**

- [ ] **Step 4: 运行测试**

Run: `npm test -- add.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add skillhub-cli/src/commands/add.ts skillhub-cli/tests/commands/add.test.ts
git commit -m "feat(cli): enhance add command with agent selection"
```

---

## Task 3.1: 添加 update 命令

**Files:**
- Create: `skillhub-cli/src/commands/update.ts`
- Test: `skillhub-cli/tests/commands/update.test.ts`

- [ ] **Step 1: 编写失败的测试**

```typescript
// tests/commands/update.test.ts
describe('update command', () => {
  it('should update a specific skill', async () => {
    const result = await executeCli(['update', 'openspec'])
    expect(result.code).toBe(0)
  })
  
  it('should update all skills with --all flag', async () => {
    const result = await executeCli(['update', '--all'])
    expect(result.code).toBe(0)
  })
})
```

Run: `npm test -- update.test.ts`
Expected: FAIL (command not created yet)

- [ ] **Step 2: 创建 update.ts 实现**

```typescript
// src/commands/update.ts
export function registerUpdate(program: Command) {
  program
    .command('update [slug]')
    .option('-a, --all', 'Update all installed skills')
    .description('Update installed skills')
    .action(async (slug, opts) => {
      // 实现更新逻辑
    })
}
```

- [ ] **Step 3: 在 cli.ts 注册**

```typescript
import { registerUpdate } from './commands/update.js'
registerUpdate(program)
```

- [ ] **Step 4: 运行测试**

Run: `npm test -- update.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add skillhub-cli/src/commands/update.ts skillhub-cli/tests/commands/update.test.ts
git commit -m "feat(cli): add update command"
```

---

## Task 3.2: 添加 uninstall 命令

**Files:**
- Create: `skillhub-cli/src/commands/uninstall.ts`
- Test: `skillhub-cli/tests/commands/uninstall.test.ts`

- [ ] **Step 1: 编写失败的测试**

```typescript
// tests/commands/uninstall.test.ts
describe('uninstall command', () => {
  it('should uninstall a skill', async () => {
    const result = await executeCli(['uninstall', 'openspec'])
    expect(result.code).toBe(0)
  })
})
```

Run: `npm test -- uninstall.test.ts`
Expected: FAIL

- [ ] **Step 2: 创建 uninstall.ts 实现**

- [ ] **Step 3: 在 cli.ts 注册**

- [ ] **Step 4: 运行测试**

Run: `npm test -- uninstall.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add skillhub-cli/src/commands/uninstall.ts skillhub-cli/tests/commands/uninstall.test.ts
git commit -m "feat(cli): add uninstall command"
```

---

## Task 3.3: 添加 sync 命令

**Files:**
- Create: `skillhub-cli/src/commands/sync.ts`
- Test: `skillhub-cli/tests/commands/sync.test.ts`

- [ ] **Step 1: 编写失败的测试**

```typescript
// tests/commands/sync.test.ts
describe('sync command', () => {
  it('should scan and publish local skills', async () => {
    const result = await executeCli(['sync'])
    expect(result.code).toBe(0)
  })
  
  it('should support --dry-run flag', async () => {
    const result = await executeCli(['sync', '--dry-run'])
    expect(result.stdout).toContain('would publish')
  })
})
```

Run: `npm test -- sync.test.ts`
Expected: FAIL

- [ ] **Step 2: 创建 sync.ts 实现**

- [ ] **Step 3: 在 cli.ts 注册**

- [ ] **Step 4: 运行测试**

Run: `npm test -- sync.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add skillhub-cli/src/commands/sync.ts skillhub-cli/tests/commands/sync.test.ts
git commit -m "feat(cli): add sync command"
```

---

## Task 4.1: 添加 --json 全局选项

**Files:**
- Modify: `skillhub-cli/src/cli.ts`
- Test: `skillhub-cli/tests/commands/json-option.test.ts`

- [ ] **Step 1: 编写失败的测试**

```typescript
// tests/commands/json-option.test.ts
describe('--json option', () => {
  it('search should output JSON with --json flag', async () => {
    const result = await executeCli(['search', 'test', '--json'])
    const output = JSON.parse(result.stdout)
    expect(output).toHaveProperty('data')
  })
})
```

Run: `npm test -- json-option.test.ts`
Expected: FAIL

- [ ] **Step 2: 添加全局 --json 选项**

```typescript
// src/cli.ts
program.option('--json', 'Output as JSON')
```

- [ ] **Step 3: 修改命令支持 --json**

```typescript
// src/commands/search.ts
if (program.opts().json) {
  console.log(JSON.stringify(results, null, 2))
} else {
  // 现有格式化输出
}
```

- [ ] **Step 4: 运行测试**

Run: `npm test -- json-option.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add skillhub-cli/src/cli.ts skillhub-cli/tests/commands/json-option.test.ts
git commit -m "feat(cli): add --json global option"
```

---

## Task 4.2: 添加 --copy 安装选项

**Files:**
- Modify: `skillhub-cli/src/commands/install.ts`
- Test: `skillhub-cli/tests/commands/copy-option.test.ts`

- [ ] **Step 1: 编写失败的测试**

```typescript
// tests/commands/copy-option.test.ts
describe('--copy option', () => {
  it('install should copy instead of symlink with --copy flag', async () => {
    const result = await executeCli(['install', 'openspec', '--copy'])
    expect(result.code).toBe(0)
    // 验证使用复制而非 symlink
  })
})
```

Run: `npm test -- copy-option.test.ts`
Expected: FAIL

- [ ] **Step 2: 检查 install.ts 实现**

Read: `skillhub-cli/src/commands/install.ts`

- [ ] **Step 3: 添加 --copy 选项支持**

```typescript
program.option('--copy', 'Copy instead of symlink')
```

- [ ] **Step 4: 运行测试**

Run: `npm test -- copy-option.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add skillhub-cli/src/commands/install.ts skillhub-cli/tests/commands/copy-option.test.ts
git commit -m "feat(cli): add --copy install option"
```

---

## Task 5: 最终 npm 发布

**Files:**
- Modify: `skillhub-cli/package.json`
- Test: `skillhub-cli/tests/e2e/publish.test.ts`

- [ ] **Step 1: 最终构建和测试**

Run: `cd skillhub-cli && npm run build && npm test`

- [ ] **Step 2: 发布到 npm**

```bash
cd skillhub-cli
npm publish --access public --scope=@motovis
```

- [ ] **Step 3: 验证安装**

```bash
npm i -g @motovis/skillhub
skillhub --version
skillhub --help
```

- [ ] **Step 4: Commit 发布配置**

```bash
git add skillhub-cli/package.json
git commit -m "release: publish @motovis/skillhub v1.0.0"
```

---

## 预估时间

| Task | 预估时间 |
|------|----------|
| Task 0: npm 配置 | 15 min |
| Task 1.1: namespaces | 30 min |
| Task 1.2: --namespace | 1 hour |
| Task 1.3: 跨命名空间搜索 | 1 hour |
| Task 2.1: 41+ agents | 4 hours |
| Task 2.2: add 命令 | 1 hour |
| Task 3.1: update | 2 hours |
| Task 3.2: uninstall | 1 hour |
| Task 3.3: sync | 3 hours |
| Task 4.1: --json | 1 hour |
| Task 4.2: --copy | 1 hour |
| Task 5: 发布 | 30 min |

**总计: ~17 小时**

---

## 验证命令

```bash
cd skillhub-cli

# 构建
npm run build

# 测试
npm test

# 本地验证
node dist/cli.mjs --help
node dist/cli.mjs namespaces
node dist/cli.mjs search test --namespace global --json

# 发布
npm publish --access public --scope=@motovis
```
