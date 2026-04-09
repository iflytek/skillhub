# SkillHub CLI 工作汇报

> **日期**: 2026-04-09  
> **项目**: SkillHub CLI 重构  
> **状态**: 进行中

---

## 一、项目背景

### 1.1 需求来源

SkillHub 是一个企业级自托管的 Agent 技能注册中心，支持团队私有化部署。随着 SkillHub 平台的发展，需要一个功能完善、体验良好的 CLI 工具来支持：

- 技能的发布、搜索、安装
- 多命名空间管理
- 多 Agent 支持
- 与现有生态（vercel-labs/skills、clawhub）兼容

### 1.2 竞品分析

| 特性 | vercel-labs/skills | clawhub | SkillHub CLI (目标) |
|------|-------------------|---------|-------------------|
| 源码来源 | GitHub/git | GitHub/git/local | GitHub/git/local/registry |
| 命名空间支持 | ❌ | ❌ | ✅ |
| 多 Agent 安装 | ❌ | ✅ (41+) | ✅ (46+) |
| 交互式安装 | ✅ | ❌ | ✅ |
| skill-lock 锁文件 | ✅ | ❌ | ✅ |
| CLI 命令数 | ~15 | ~26 | ~30 |

### 1.3 技术选型

**参考实现**: [vercel-labs/skills](https://github.com/vercel-labs/skills)

选择 vercel-labs/skills 作为参考的原因：

1. **设计优雅**: 采用 fzf 风格的交互式搜索
2. **源码解析**: 完整的 GitHub/git 源码解析能力
3. **skill-lock 机制**: 成熟的锁文件管理
4. **代码质量**: 高质量的 TypeScript 实现

**适配目标**: SkillHub 平台

---

## 二、技术架构

### 2.1 技术栈

```
┌─────────────────────────────────────────────────┐
│              SkillHub CLI                        │
├─────────────────────────────────────────────────┤
│  TypeScript + Node.js                           │
│  ├── commander.js    (命令行框架)                │
│  ├── ora             (终端 spinner)              │
│  ├── chalk           (终端颜色)                  │
│  ├── semver          (版本号解析)                │
│  └── undici          (HTTP 客户端)               │
└─────────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────┐
│           SkillHub Backend (Java 21)             │
├─────────────────────────────────────────────────┤
│  Spring Boot 3.2.3                              │
│  ├── REST API (端口 8080)                        │
│  ├── PostgreSQL 16                              │
│  ├── Redis 7                                    │
│  └── S3/MinIO 存储                              │
└─────────────────────────────────────────────────┘
```

### 2.2 核心模块

```
skillhub-cli/src/
├── cli.ts                    # CLI 入口，命令注册
├── commands/                 # 命令实现
│   ├── install.ts           # 统一的安装命令
│   ├── add.ts               # Git/本地安装 (保留独立)
│   ├── uninstall.ts         # 卸载命令
│   ├── publish.ts           # 发布命令
│   ├── search.ts            # 搜索命令
│   ├── ...
│   └── [其他 26 个命令]
├── core/                     # 核心模块
│   ├── api-client.ts        # API 客户端
│   ├── skill-lock.ts        # 锁文件管理
│   ├── source-parser.ts     # 源码解析
│   ├── skill-discovery.ts   # Skill 发现
│   ├── installer.ts         # 安装逻辑
│   ├── agent-detector.ts    # Agent 检测
│   └── config.ts            # 配置管理
├── schema/                   # 类型定义
│   └── routes.ts            # API 路由
└── utils/                   # 工具函数
    └── logger.ts           # 日志输出
```

### 2.3 命令设计

#### install 命令 (核心创新)

```bash
# 自动检测源码类型
skillhub install <source> [--source auto|registry|git|local]

# 示例
skillhub install openspec                    # registry (默认)
skillhub install vercel-labs/skills         # git (自动检测)
skillhub install ./local-skill              # local (自动检测)
skillhub install global--openspec           # registry with namespace
```

**设计决策**: `--source auto` 是关键创新，无需用户手动指定来源。

#### uninstall 命令

```bash
skillhub uninstall [name] [--all] [--global] [--agent <agents>] [--yes]
```

**设计决策**: 合并了 `remove` 和 `uninstall`，统一用户体验。

---

## 三、功能实现

### 3.1 已完成功能

| 功能 | 描述 | 状态 | 参考 |
|------|------|------|------|
| 认证 | login/logout/whoami | ✅ | clawhub |
| 搜索 | search with namespace filter | ✅ | clawhub |
| 安装 | install + add 合并 | ✅ | vercel-labs/skills |
| 卸载 | uninstall (合并 remove) | ✅ | clawhub |
| 发布 | publish + sync | ✅ | clawhub |
| 命名空间 | --namespace 选项 | ✅ | **独有** |
| 多 Agent | 46 个 agents | ✅ | clawhub |
| skill-lock | 锁文件管理 | ✅ | vercel-labs/skills |
| JSON 输出 | --json 全局选项 | ✅ | **独有** |
| Explore | 浏览最新 skills | ✅ | clawhub |
| Inspect | 跨 NS 搜索详情 | ✅ | **独有** |
| Update | 更新已安装 skill | ✅ | **独有** |

### 3.2 命令统计

```
总命令数: 30 个

认证 (3):      login, logout, whoami
搜索发现 (7):   search, namespaces, info, inspect, explore, versions, resolve
安装发布 (6):   install, add, publish, sync, download, init
本地管理 (5):   list, uninstall, update, archive, remove
社交 (7):      star, rating, rate, me, reviews, notifications, report
删除 (1):      delete
工具 (1):      transfer (未注册)
```

### 3.3 与 vercel-labs/skills 的差异

| 特性 | vercel-labs/skills | SkillHub CLI |
|------|-------------------|--------------|
| 源码获取 | GitHub/git/local | GitHub/git/local/Registry |
| skill-lock | ✅ | ✅ |
| 交互式安装 | fzf 风格 | 待实现 |
| Registry 支持 | ❌ | ✅ |
| 命名空间 | ❌ | ✅ |
| 多 Agent | ❌ | ✅ |

---

## 四、代码质量

### 4.1 测试覆盖

```bash
✅ 87 tests passing
✅ 8 test files
```

**测试文件**:
- `commands.test.ts` - 命令注册测试
- `install.test.ts` - 安装功能测试
- `uninstall.test.ts` - 卸载功能测试
- `skill-lock.test.ts` - 锁文件测试
- `api-client.test.ts` - API 客户端测试
- `source-parser.test.ts` - 源码解析测试
- `installer.test.ts` - 安装器测试
- `skill-name.test.ts` - 技能名称测试

### 4.2 构建状态

```
✅ Build succeeded
dist/cli.mjs (3.6 kB)
Total dist size: 85.9 kB
```

### 4.3 代码规范

- ✅ TypeScript strict mode
- ✅ ESLint + Prettier
- ✅ 单元测试 (TDD)
- ✅ 清晰的模块划分
- ❌ JSDoc 注释 (部分缺失)

---

## 五、正在进行的工作

### 5.1 命令合并进度

| Phase | 描述 | 状态 |
|-------|------|------|
| Phase 1.1 | 合并 remove/uninstall → uninstall | ✅ |
| Phase 1.2 | 合并 add/install → install (source auto) | ✅ |
| Phase 1.3 | 合并 info/inspect | ⏳ |
| Phase 1.4 | delete 添加 unpublish alias | ⏳ |

### 5.2 待实现功能

| 功能 | 描述 | 优先级 |
|------|------|--------|
| skill-lock 集成 | install/uninstall 与锁文件集成 | 高 |
| 交互式 explore | fzf 风格搜索 | 中 |
| 交互式 install | 多选安装 | 中 |
| check 命令 | 检查已安装 skills 状态 | 中 |
| update --lock | 基于锁文件更新 | 中 |

---

## 六、部署计划

### 6.1 npm 发布配置

```json
{
  "name": "@motovis/skillhub",
  "version": "1.0.0",
  "description": "CLI for SkillHub — publish, search, and manage agent skills",
  "bin": {
    "skillhub": "./dist/cli.mjs"
  }
}
```

### 6.2 发布命令

```bash
cd skillhub-cli
npm publish --access public --scope=@motovis
```

---

## 七、总结

### 7.1 成果

1. **完整的 CLI 工具**: 30 个命令，覆盖 skill 全生命周期
2. **创新设计**: `--source auto` 自动检测，用户无需记忆来源类型
3. **兼容性好**: 基于 vercel-labs/skills 设计，同时保持与 clawhub 的兼容性
4. **代码质量**: 87 个测试，构建成功，TypeScript 严格模式

### 7.2 亮点

1. **命名空间支持** (独有): 天然支持团队隔离和权限管理
2. **多 Agent 安装** (扩展): 支持 46 个 agents，覆盖主流 AI 工具
3. **skill-lock 机制** (借鉴): 版本锁定，防止依赖漂移
4. **跨 NS 搜索** (独有): `inspect` 命令跨所有命名空间搜索

### 7.3 下一步

1. 完成 Phase 1.3/1.4 命令合并
2. 实现 skill-lock 与 install/uninstall 集成
3. 添加交互式 explore 和 install
4. 完善文档和用户指南

---

## 八、参考文档

- [vercel-labs/skills](https://github.com/vercel-labs/skills) - 参考实现
- [clawhub](https://github.com/openclaw/clawhub) - CLI 兼容性参考
- [SkillHub Backend](https://github.com/iflytek/skillhub) - 后端实现
- [CLI Commands Reference](./CLI_COMMANDS.md) - 命令详细文档
- [Manual Test Guide](./MANUAL_TEST_GUIDE.md) - 测试文档
