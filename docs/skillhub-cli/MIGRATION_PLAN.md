# SkillHub CLI 统一方案

> **For agentic workers:** 使用 superpowers:subagent-driven-development 或 superpowers:executing-plans 执行此计划。步骤使用复选框 (`- [ ]`) 语法追踪。

**目标:** 以 skillhub-cli 为核心，统一 skillhub-cli 和 clawhub 的最佳功能，发布到 npm 成为独立产品。

**Architecture:** 基于 commander.js 的 Node.js CLI，使用 undici 作为 HTTP 客户端

**Tech Stack:** TypeScript, Node.js, commander.js, undici

---

## 战略决策

### 方案: 以 skillhub-cli 为核心进行扩展

**原因:**
1. skillhub-cli 是我们自己的代码，完全可控
2. 命名空间功能是差异化竞争力，clawhub 上游不需要
3. clawhub 是上游开源项目，可以参考但不应强依赖
4. MIT license 完全允许我们自由修改和分发

### 架构图

```
                    ┌─────────────────────────────────────────┐
                    │          @motovis/skillhub             │
                    │         (npm 发布，私有命名)            │
                    └─────────────────────────────────────────┘
                                       ▲
                    ┌─────────────────┼─────────────────┐
                    │                 │                 │
           ┌────────┴────────┐ ┌──────┴──────┐  ┌───────┴───────┐
           │  skillhub-cli   │ │   clawhub    │  │  skillhub-    │
           │  (已有代码)      │ │  (参考代码)   │  │  server       │
           │  - 29 命令      │ │  - 41+ agents│  │  (后端)       │
           │  - 命名空间     │ │  - 官方生态  │  │               │
           │  - 16 agents   │ └─────────────┘  └───────────────┘
           └─────────────────┘
                    │
                    ▼
           ┌─────────────────────────────────────┐
           │           用户安装方式                 │
           │  npm i -g @motovis/skillhub         │
           └─────────────────────────────────────┘
```

### Dream State Mapping

```
CURRENT STATE                    THIS PLAN                  12-MONTH IDEAL
skillhub-cli + clawhub  ────>  统一 CLI (@motovis/skillhub)  ────>  行业标准 CLI
- 分散维护                            - 单一代码库                        - 贡献回上游
- 功能不互补                          - 最佳功能合并                      - 生态主导
- 用户困惑                            - 明确差异化                         - 企业级支持
```

---

## npm 发布计划

### 发布配置

**package.json 更新:**
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

### 发布流程

```bash
# 1. 构建
cd skillhub-cli && npm run build

# 2. 发布到 npm
npm publish --access public --scope=@iflytek

# 3. 用户安装
npm i -g @motovis/skillhub

# 4. 验证安装
skillhub --version
skillhub --help
```

---

## 功能合并清单

### Phase 1: 核心命名空间支持 (优先级: 高)

#### Task 1.1: 验证现有 namespaces 命令

**Files:**
- `skillhub-cli/src/commands/namespaces.ts`

**功能:**
```bash
skillhub namespaces                          # 列出用户有权限的命名空间
```

#### Task 1.2: 验证 --namespace 全局选项

**Files:**
- `skillhub-cli/src/cli.ts`
- `skillhub-cli/src/core/skill-name.ts`

**功能:**
```bash
skillhub search <query> --namespace <ns>    # 在指定命名空间搜索
skillhub install <slug> --namespace <ns>    # 从指定命名空间安装
skillhub inspect <slug> --namespace <ns>   # 查看指定命名空间的 skill
skillhub publish <path> --namespace <ns>    # 发布到指定命名空间
```

#### Task 1.3: 验证跨命名空间搜索

**Files:**
- `skillhub-cli/src/commands/search.ts`

**功能:**
- 不指定 `--namespace` 时，搜索所有可访问的命名空间
- 结果中显示所属命名空间

---

### Phase 2: Agent 生态对齐 (优先级: 高)

**目标:** 扩展 agent 检测，支持完整的 vercel-labs/skills 生态

#### Task 2.1: 扩展 agent-detector

**Files:**
- `skillhub-cli/src/core/agent-detector.ts`

**目标:** 支持 41+ agents（vercel-labs/skills 全部）

**当前状态:** skillhub-cli 支持 16 agents
**目标状态:** clawhub 级别 41+ agents

#### Task 2.2: 添加 add 命令（多 agent 安装）

**Files:**
- `skillhub-cli/src/commands/add.ts` (已存在，需验证)

**功能:**
```bash
skillhub add <source>                       # 支持 GitHub shorthand, URL, local path
skillhub add <source> --agent <agent>       # 指定 agent 类型
```

---

### Phase 3: 补充 clawhub 最佳功能 (优先级: 中)

#### Task 3.1: 添加 update 命令

**Files:**
- Create: `skillhub-cli/src/commands/update.ts`

**功能:**
```bash
skillhub update [slug]                      # 更新已安装的 skills
skillhub update --all                       # 更新所有
```

#### Task 3.2: 添加 uninstall 命令

**Files:**
- Create: `skillhub-cli/src/commands/uninstall.ts`

**功能:**
```bash
skillhub uninstall <slug>                   # 卸载 skill
```

#### Task 3.3: 添加 sync 命令

**Files:**
- Create: `skillhub-cli/src/commands/sync.ts`

**功能:**
```bash
skillhub sync                              # 扫描本地 skills 并发布新/更新的
skillhub sync --dry-run                    # 预览模式
```

---

### Phase 4: 输出格式优化 (优先级: 低)

#### Task 4.1: 添加 --json 全局选项

**Files:**
- Modify: `skillhub-cli/src/cli.ts`

**功能:**
```bash
skillhub search <query> --json              # JSON 输出
skillhub inspect <slug> --json              # JSON 输出
```

#### Task 4.2: 添加 --copy 安装选项

**Files:**
- Modify: `skillhub-cli/src/commands/install.ts`

**功能:**
```bash
skillhub install <slug> --copy              # 复制模式安装（非 symlink）
```

---

## Transfer 机制保留

| 机制 | 命令 | 决策 |
|------|------|------|
| 技能级别 | `skillhub transfer <skill>` | 从 clawhub 移植 |
| 命名空间级别 | `skillhub transfer --namespace <ns>` | **保留** (SkillHub 特有) |

---

## 生产部署策略

### 组件说明

| 组件 | 当前状态 | 发布方式 |
|------|---------|---------|
| `@motovis/skillhub` (CLI) | skillhub-cli | npm publish |
| skillhub-server | 官方镜像 | 按需定制 |
| skillhub-web | 官方镜像 | 按需定制 |

### 定制镜像构建

```bash
# 1. 构建定制 server 镜像
docker build -f Dockerfile.server \
  -t ghcr.io/your-org/skillhub-server:custom-feature .

# 2. 推送到 registry
docker push ghcr.io/your-org/skillhub-server:custom-feature

# 3. 更新 .env.release
SKILLHUB_VERSION=custom-feature
SKILLHUB_SERVER_IMAGE=ghcr.io/your-org/skillhub-server
SKILLHUB_WEB_IMAGE=ghcr.io/your-org/skillhub-web

# 4. 重启服务
docker compose --env-file .env.release -f compose.release.yml up -d
```

### 环境变量参考

```bash
# .env.release
SKILLHUB_VERSION=latest              # 或自定义标签
SKILLHUB_SERVER_IMAGE=ghcr.io/iflytek/skillhub-server
SKILLHUB_WEB_IMAGE=ghcr.io/iflytek/skillhub-web
POSTGRES_IMAGE=postgres:16-alpine
REDIS_IMAGE=redis:7-alpine
MINIO_IMAGE=minio/minio:latest
```

---

## 验证步骤

### CLI 验证
```bash
# 验证安装
skillhub --version
skillhub --help

# 验证命名空间
skillhub namespaces
skillhub search test --namespace global
skillhub install openspec --namespace vision2group

# 验证 agent
skillhub add https://github.com/vercel/skills --agent claude

# 验证新增命令
skillhub update --all
skillhub uninstall <slug>
skillhub sync --dry-run
```

---

## 预估工时

| Phase | Task | 预估时间 | 依赖 |
|-------|------|----------|------|
| - | npm 发布配置 | 1 小时 | 无 |
| 1 | namespaces 命令验证 | 0.5 小时 | 无 |
| 1 | --namespace 选项验证 | 1 小时 | Phase 1.1 |
| 1 | 跨命名空间搜索验证 | 1 小时 | Phase 1.2 |
| 2 | agent 扩展到 41+ | 4 小时 | 无 |
| 2 | add 命令验证 | 0.5 小时 | 无 |
| 3 | update 命令 | 2 小时 | 无 |
| 3 | uninstall 命令 | 1 小时 | 无 |
| 3 | sync 命令 | 3 小时 | 无 |
| 4 | --json 选项 | 1 小时 | 无 |
| 4 | --copy 选项 | 1 小时 | 无 |

**总计: ~16 小时**

---

## 风险与缓解

| 风险 | 影响 | 缓解策略 |
|------|------|----------|
| npm 包名被占用 | 低 | 使用 scoped package `@motovis/skillhub` |
| 官方镜像定制困难 | 中 | 保持 fork 自己镜像的能力 |
| 版本同步 | 低 | 持续跟踪上游 releases |

---

## NOT in Scope

1. **clawhub fork** - 不创建 fork，仅参考
2. **强制 PR** - 贡献回上游是可选的，不是必须的
3. **后端 API 破坏性变更** - 仅修改 CLI，API 保持兼容

---

## 下一步

1. **立即**: 更新 `package.json` 发布配置
2. **立即**: 发布到 npm 测试
3. **Phase 1**: 验证核心命名空间功能
4. **Phase 2**: 扩展 agent 检测到 41+
5. **Phase 3**: 补充缺失命令

---

## 附录: clawhub 源码参考

**clawhub 开源信息:**
- GitHub: https://github.com/openclaw/clawhub
- License: MIT
- 源码: TypeScript (98.3%)
- 可用命令: 26 个
- agent 支持: 41+

**关键文件参考:**
- `/home/chenbaowang/.npm/_npx/a92a6dbcf543fba6/node_modules/clawhub/dist/`
- API 路由: `dist/schema/routes.d.ts`
- 命令实现: `dist/cli/commands/`
