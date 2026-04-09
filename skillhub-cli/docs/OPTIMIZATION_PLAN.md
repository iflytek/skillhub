# SkillHub CLI 优化方案

> **日期**: 2026-04-09  
> **版本**: v1.4.0  
> **目标**: 对标 vercel-labs/skills，实现交互式选择和 @skill 语法

---

## 问题描述

### 已知差距

| # | 功能 | vercel-labs/skills | skillhub-cli | 状态 |
|---|------|-------------------|--------------|------|
| 1 | `--skill <name>` | ✅ | ✅ | 已完成 |
| 2 | `--skill '*'` | ✅ | ✅ | 已完成 |
| 3 | 交互式选择 | ✅ (fzf) | ✅ | 已完成 |
| 4 | `@skill` 语法 | ✅ | ✅ | 已完成 |
| 5 | `DISABLE_TELEMETRY` | ✅ | ✅ | 已完成 |
| 6 | `find` 命令 (交互式搜索) | ✅ | ✅ | 已完成 |

---

## Feature 1: @skill 语法

### 描述

允许使用 `owner/repo@skill-name` 格式直接指定安装特定 skill。

### 语法

```bash
# 安装特定 skill
skillhub install owner/repo@skill-name

# 安装多个特定 skills
skillhub install owner/repo@skill1 owner/repo@skill2

# 混合使用
skillhub install owner/repo@skill-name other-repo
```

### 设计决策

**解析逻辑**:
1. 检测 `source` 中是否包含 `@` 符号
2. 如果 `@` 在 `/` 之后（表示是 `repo@something`），则解析为 skill 名称
3. 将 `skillFilter` 注入到 `options.skill` 中

### 实现计划

#### Step 1: 修改 source-parser.ts

```typescript
// src/core/source-parser.ts

export interface ParsedSource {
  // ... 现有字段
  skillFilter?: string;  // 新增：从 @skill 语法提取
}

export function parseSource(input: string): ParsedSource {
  // 检测 @skill 语法：owner/repo@something
  const atSignMatch = input.match(/^(.+)\/([^/]+)@(.+)$/);
  if (atSignMatch) {
    return {
      type: "github",  // 假设为 github，可扩展
      owner: atSignMatch[1],
      repo: atSignMatch[2],
      ref: undefined,
      skillFilter: atSignMatch[3],
    };
  }
  // ... 原有逻辑
}
```

#### Step 2: 修改 install.ts

```typescript
// src/commands/install.ts

// 在 installFromGit 中:
if (parsed.skillFilter) {
  options.skill = options.skill || [];
  if (!options.skill.includes(parsed.skillFilter)) {
    options.skill.push(parsed.skillFilter);
  }
}
```

### 验证

```bash
# 测试
skillhub install vercel-labs/skills@openspec
skillhub install owner/repo@skill1@skill2  # 多个 @
```

---

## Feature 2: 交互式选择

### 描述

当 repo 包含多个 skills 且未指定 `--skill` 时，使用 fzf 风格的多选界面。

### 设计决策

**不引入外部依赖**: 使用纯 Node.js 实现基础交互式选择。

**备选方案**:
1. **完整 fzf 实现** - 需要 native 依赖，复杂
2. **基础 readline 实现** - 简单，无需额外依赖 ✅
3. **使用 @inquirer/prompts** - 需要安装新包

### 实现计划

#### Step 1: 创建 prompts 工具

```typescript
// src/utils/prompts.ts

export async function multiSelect(
  message: string,
  items: Array<{ value: string; label: string }>
): Promise<string[] | null> {
  // 使用 readline 实现基础多选
}
```

#### Step 2: 修改 install.ts

```typescript
// src/commands/install.ts

// 当 skills.length > 1 && !opts.skill && !opts.yes 时
if (skills.length > 1 && !opts.skill && !opts.yes) {
  const selected = await multiSelect(
    "Select skills to install",
    skills.map((s) => ({ value: s.name, label: `${s.name} — ${s.description}` }))
  );
  
  if (!selected) {
    console.log("Cancelled.");
    return;
  }
  
  selectedSkills = skills.filter((s) => selected.includes(s.name));
}
```

### 交互流程

```
$ skillhub install owner/repo

Found 5 skills:

? Select skills to install (按空格选择，按回车确认)
  ○ skill-one — First skill
  ○ skill-two — Second skill
  ◉ skill-three — Third skill (已选择)
  ○ skill-four — Fourth skill
  ○ skill-five — Fifth skill

安装总结:
  Skills: skill-three
  Agents: claude-code
  Scope: project

继续安装? [y/N]
```

### 验证

```bash
# 测试交互式选择
skillhub install owner/repo

# 跳过交互式，直接全选
skillhub install owner/repo --yes

# 跳过交互式，使用 --skill
skillhub install owner/repo --skill openspec
```

---

## 影响范围

| 文件 | 修改 |
|------|------|
| `src/core/source-parser.ts` | 添加 skillFilter 解析 |
| `src/commands/install.ts` | 集成 skillFilter 和交互式选择 |
| `src/utils/prompts.ts` | 新建，交互式工具函数 |
| `tests/install.test.ts` | 添加 @skill 和交互式测试 |
| `tests/source-parser.test.ts` | 添加 skillFilter 测试 |

---

## 验证标准

- [x] `install owner/repo@skill` 正确解析并安装
- [x] `install owner/repo` 在多个 skills 时进入交互式
- [x] 交互式选择可以多选 skills
- [x] 交互式可以取消选择
- [x] 所有测试通过

---

## 风险评估

| 风险 | 影响 | 缓解 |
|------|------|------|
| @ 符号在路径中出现 | 低 | 只在 `/` 之后检测 |
| Windows 路径问题 | 低 | 使用标准 path 模块 |
| 交互式在 CI 环境挂起 | 中 | 使用 `--yes` 跳过 |

---

## 历史记录

### v1.4.0 (2026-04-09)

- ✅ explore 命令添加 find 别名
- ✅ 交互式 fzf 风格搜索 (直接输入关键词搜索)
- ✅ 添加 telemetry opt-out 支持 (DISABLE_TELEMETRY, DO_NOT_TRACK)
- ✅ 新建 src/utils/telemetry.ts 遥测工具模块

### v1.3.0 (2026-04-09)

- ✅ install 命令集成 skillhub.lock
- ✅ uninstall 命令已集成 lock (之前完成)
- ✅ explore 命令添加 --install 交互式安装
- ✅ 新增 check 命令验证已安装 skills
- ✅ 完善 update 命令实现

### v1.2.0 (2026-04-09)

- ✅ 添加 `@skill` 语法 (owner/repo@skill-name)
- ✅ 实现交互式多选 (multiSelect)
- ✅ 合并 remove/uninstall → uninstall
- ✅ 合并 add/install → install (source 自动检测)
- ✅ 合并 info/inspect → inspect (info/view 作为 alias)
- ✅ delete 添加 unpublish alias

### v1.1.0 (2026-04-09)

- ✅ 添加 `--skill` 选项
- ✅ 支持 `--skill '*'` 全选
- ✅ 添加 skill 过滤逻辑
