# Resolve 命令命名空间交互选择设计

## 概述

当用户运行 `resolve <slug>` 而未指定命名空间时，如果多个命名空间中存在同名 skill，CLI 将显示交互式选择器让用户选择目标命名空间。

## 背景

当前 `resolve` 和 `explore/search` 命令对命名空间的处理不一致：

| 命令 | 命名空间处理 |
|------|-------------|
| `explore/search` | 搜索所有命名空间，显示匹配结果 |
| `resolve` | 默认 global 命名空间 |

这导致用户运行 `resolve openspec` 时只会解析 global/openspec，而不知道 vision2group/openspec 也存在。

## 设计目标

1. **保持显式行为** - 显式指定命名空间时行为不变
2. **智能隐式选择** - 未指定命名空间时，自动搜索所有命名空间
3. **单一结果直接解析** - 只有一个匹配时直接显示结果
4. **多结果交互选择** - 多个匹配时显示交互选择器

## 实现方案

### 1. 新建共享交互模块

**文件**: `skillhub-cli/src/core/interactive-search.ts`

将 `explore.ts` 中的 `runInteractiveSearch` 函数抽取为共享模块：

```typescript
export interface InteractiveSearchResult {
  namespace: string;
  name: string;
}

export async function runInteractiveSearch(
  client: ApiClient,
  initialQuery: string = ""
): Promise<string | null> {
  // 复用 explore.ts 的交互选择逻辑
  // 返回格式: "namespace/name" 或 null（用户取消）
}
```

### 2. 修改 explore.ts

**文件**: `skillhub-cli/src/commands/explore.ts`

改为导入共享的交互选择器：

```typescript
import { runInteractiveSearch } from "../core/interactive-search.js";

// 移除本地 runInteractiveSearch 实现
// registerExplore 中调用共享模块
```

### 3. 修改 resolve.ts

**文件**: `skillhub-cli/src/commands/resolve.ts`

添加命名空间检测和交互选择逻辑：

```typescript
// 在 action 中:
const { namespace, slug } = parseSkillName(slug);

// 如果指定了命名空间，保持原有行为
if (namespace !== "global") {
  // 现有 resolve 逻辑
  return;
}

// 未指定命名空间，搜索所有命名空间
const selected = await runInteractiveSearch(client, slug);
if (!selected) {
  info("Cancelled.");
  return;
}

// 解析选中的结果
const [ns, name] = selected.split("/", 2);
const path = `/api/v1/skills/${ns}/${name}/resolve`;
// ... 现有 resolve 逻辑
```

### 4. 搜索 API 去重

为 resolve 专门创建一个搜索函数，按 skill name 去重：

```typescript
async function searchSkillsByName(
  client: ApiClient,
  name: string
): Promise<InteractiveSearchResult[]> {
  const result = await client.get<SearchResponse>(
    `${ApiRoutes.search}?q=${encodeURIComponent(name)}&limit=50`
  );
  
  // 去重：按 namespace + name 唯一化
  const seen = new Set<string>();
  const results: InteractiveSearchResult[] = [];
  
  for (const s of result.results) {
    const { namespace, name: skillName } = parseNamespace(s.slug);
    const key = `${namespace}/${skillName}`;
    if (!seen.has(key)) {
      seen.add(key);
      results.push({ namespace, name: skillName });
    }
  }
  
  return results;
}
```

## 用户体验流程

```
$ skillhub resolve openspec
# 用户未指定命名空间

正在搜索 "openspec" 在所有命名空间中...
找到 2 个匹配:
  1. global/openspec
  2. vision2group/openspec

[交互选择器启动]
  > global/openspec
    vision2group/openspec

# 用户选择 vision2group/openspec
# 显示解析结果
```

## 边界情况处理

| 场景 | 处理方式 |
|------|----------|
| 只有一个匹配 | 直接解析，不显示选择器 |
| 多个匹配 | 显示选择器，用户选择后解析 |
| 用户取消 (ESC/Ctrl+C) | 显示 "Cancelled." 并退出 |
| 没有任何匹配 | 显示错误 "Skill not found: openspec" |
| 指定了命名空间 | 保持现有行为，直接解析 |

## 改动文件清单

| 文件 | 改动类型 | 说明 |
|------|----------|------|
| `src/core/interactive-search.ts` | 新建 | 共享交互选择器模块 |
| `src/commands/explore.ts` | 修改 | 改为使用共享模块 |
| `src/commands/resolve.ts` | 修改 | 添加交互选择逻辑 |

## 测试场景

1. `resolve openspec` (只有一个 global 匹配) → 直接解析
2. `resolve openspec` (有 global 和 vision2group 匹配) → 显示选择器
3. `resolve vision2group/openspec` (显式命名空间) → 直接解析
4. `resolve notexist` (不存在) → 显示错误
5. 用户按 ESC 取消选择 → 显示 Cancelled

## 优先级

**P0**: 核心交互选择功能
**P1**: explore.ts 重构使用共享模块
**P2**: 文档更新 (MANUAL_TEST_GUIDE.md)
