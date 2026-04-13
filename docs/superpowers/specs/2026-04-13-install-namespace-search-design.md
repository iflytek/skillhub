# Install 命令交互流程优化设计

## 概述

为 `install` 命令添加 namespace/skill 搜索选择步骤，与 resolve 命令保持一致的交互体验。

## 背景

当前 `install` 命令在被调用时（如 `install openspec`），如果 skill 存在于多个 namespace，用户无法交互式选择。resolve 命令已有完善的 namespace/skill 搜索选择流程，应复用该逻辑。

## 设计方案

### 新交互流程

**不指定 namespace 时**（如 `install openspec`）：

```
1. 交互式搜索选择 namespace/skill
   └─ 复用 runInteractiveSearch()，从 resolve 命令

2. 版本选择（单选列表，带 tag 标注）

3. 下载并解压 skill 包

4. 选择 skills（多选，从解压的包中选择）

5. 选择 agents（多选）

6. 选择 scope（project/global）

7. 选择安装方式（symlink/copy）

8. 确认安装
```

**指定 namespace 时**（如 `install global/openspec`）：

```
1. 版本选择（跳过步骤 1 的搜索）

2-8. 同上
```

### 改动文件

- `skillhub-cli/src/commands/install.ts`

### 实现要点

1. **复用 `runInteractiveSearch`** - 从 `core/interactive-search.ts` 导入
2. **复用 `searchSkills`** - 搜索 skill 的 API 调用
3. **复用 `parseNamespace`** - 解析 namespace/name 格式
4. **条件分支** - 根据是否指定 namespace 决定是否跳过步骤 1
5. **保留现有逻辑** - 步骤 2-8 保持不变

### 关键代码结构

```typescript
async function installFromRegistry(slug: string, opts, spinner) {
  let ns = "global";
  let actualSlug = slug;

  // 解析 namespace/slug
  if (slug.includes("/") && !slug.startsWith("/")) {
    const parts = slug.split("/");
    if (parts.length === 2) {
      ns = parts[0];
      actualSlug = parts[1];
    }
  }

  const config = loadConfig();
  const token = await readToken();
  const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

  // 新增：当未指定 namespace 时，先进行交互式 skill 搜索
  if (ns === "global") {
    const results = await searchSkills(client, actualSlug, 50);
    const uniqueResults = deduplicateByNsName(results);

    if (uniqueResults.length === 0) {
      error(`Skill not found: ${actualSlug}`);
      process.exit(1);
    }

    if (uniqueResults.length === 1) {
      ns = uniqueResults[0].namespace;
      actualSlug = uniqueResults[0].name;
    } else {
      const selected = await runInteractiveSearch(client, actualSlug);
      if (!selected) {
        info("Cancelled.");
        return;
      }
      const [selectedNs, selectedName] = selected.split("/", 2);
      ns = selectedNs;
      actualSlug = selectedName;
    }
  }

  // 原有版本选择 + install 流程继续
  ...
}
```

## 验证清单

- [ ] `skillhub install openspec` 显示 namespace/skill 搜索选择
- [ ] `skillhub install global/openspec` 跳过搜索直接版本选择
- [ ] 版本选择正常显示带 tag 标注
- [ ] 原有 install 流程完整保留
- [ ] 取消搜索选择时正确退出
