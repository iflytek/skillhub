# SkillHub CLI 分支融合计划

**创建时间**: 2026-04-09
**分支**: feat/skillhub-cli
**目标**: 融合 remote 独有的好想法到本地 rebased 分支

---

## 背景

### 当前状态

| 位置 | Commit | 说明 |
|------|--------|------|
| upstream/main | `b9589892` | 最新上游 |
| skillhub/main (Rsweater) | `b9589892` | 已同步 |
| feat/skillhub-cli (本地, 已rebase) | `1188f942` | ahead 20 commits |
| skillhub/feat/skillhub-cli (Remote) | `56ac69fe` | merge 结构, 旧版 |

### Remote 分支来源
Remote `skillhub/feat/skillhub-cli` 包含 merge commits 来自:
- `feat/skillhub-cli-complete` 
- `feat/cli-publish-namespace-support`

---

## 分支差异分析

### CLI 命令对比

**Remote (25个命令)**:
add, archive, delete, download, info, init, install, list, login, logout, me, namespaces, notifications, publish, rating, remove, report, resolve, reviews, search, star, versions, whoami

**Local (28个命令)**:
add, archive, check, delete, download, explore, hide, init, inspect, install, list, login, logout, me, namespaces, notifications, publish, rating, remove, report, resolve, reviews, search, star, sync, transfer, uninstall, update, versions, whoami

**结论**: 本地命令更丰富！Remote 没有的：check, explore, inspect, sync, uninstall, update, hide, transfer

### Core 文件对比

| 文件 | Remote | Local |
|------|--------|-------|
| skill-name.ts | ❌ | ✅ |
| skill-lock.ts | ✅ | ✅ |
| agent-detector.ts | ✅ | ✅ |

---

## 融合方案

### 策略: Cherry-pick Remote 独有内容

Remote 有以下 commit 是本地没有的，值得借鉴：

1. **`f87b2e3d`** - resolve/rating/rate/archive 命令 + 集成测试
   - 本地有 resolve/rating/archive，但可能实现不同
   - 集成测试套件值得借鉴

2. **`ee998ae4`** - me/reviews/notifications/delete/versions/report 命令
   - 本地已有 me/reviews/notifications/versions/report
   - 可能 delete 实现不同

3. **`8ab45974`** - vercel/skills-style add 命令
   - 本地已有 add，但实现可能不同

4. **`f79bef66`** - namespace parameter in CLI compat publish
   - **关键**: 本地有 `d2c383cf feat: support namespace parameter in CLI compat publish endpoint`
   - 两者功能相同但 commit 不同

### 推荐融合步骤

#### Step 1: 分析 Remote 的 resolve/rating/archive 实现
- 对比 `commands/resolve.ts`, `commands/rating.ts`, `commands/archive.ts`
- 如果本地实现更完整，保留本地

#### Step 2: 检查 Remote 的集成测试
- `f87b2e3d` 包含 integration test suite
- 如有必要，合并测试代码

#### Step 3: Cherry-pick 需要的内容
```bash
# 保留本地最新 commit
git checkout feat/skillhub-cli

# Cherry-pick remote 的集成测试（如果有独特价值）
git cherry-pick f87b2e3d

# 检查是否有更好的实现需要合并
```

#### Step 4: Force push 融合后的分支
```bash
git push skillhub feat/skillhub-cli --force
```

---

## 风险评估

| 风险 | 级别 | 缓解 |
|------|------|------|
| Cherry-pick 冲突 | 中 | 逐个 commit 检查，必要时跳过 |
| 覆盖远程最新内容 | 低 | Remote 已是旧版 merge，本地是新版 rebase |
| 命令实现冲突 | 低 | 本地命令更丰富，优先保留本地 |

---

## 深入对比结果

### Remote vs Local 命令对比

| 命令 | Remote | Local | 分析 |
|------|--------|-------|------|
| info | ✅ | ❌ (有inspect) | 功能类似，本地inspect更完整 |
| archive | ✅ | ✅ | **完全相同** |
| me | ✅ | ✅ | 功能类似 |
| resolve | ✅ | ✅ | 功能类似 |
| rating | ✅ | ✅ | 功能类似 |
| 独有命令 | - | check, explore, inspect, sync, uninstall, update, hide, transfer | **本地更丰富** |

### 关键发现

1. **`archive.ts` - 完全相同** - Remote 和 Local 的 archive 命令代码完全一样
2. **`info.ts` vs `inspect.ts`** - 功能相似，但本地 `inspect` 实现更完整（使用 `parseSkillName`）
3. **本地独有 8 个命令** - explore, inspect, sync, uninstall, update, hide, transfer, check

### Remote 值得借鉴的内容

**无重大独特价值**。经过对比：

- Remote 的核心 CLI 功能在本地都有对应实现
- Remote 的 `info.ts` = 本地的 `inspect.ts`（本地更好）
- Remote 的 `archive.ts` = 本地的 `archive.ts`（完全相同）
- 本地独有 8 个命令，Remote 没有对应功能

**唯一可能值得借鉴的是 Remote 的集成测试套件**（`f87b2e3d` 中的 integration test suite）。

---

## 融合结论

### 推荐方案: 直接 Force Push

由于:
1. 本地 rebased 分支已经基于最新的 upstream/main
2. 本地命令比 remote 更完整（28 vs 25）
3. Remote 的代码与本地高度重合，无重大独特价值

**直接 force push 覆盖 remote 分支即可。**

### 执行命令

```bash
git push skillhub feat/skillhub-cli --force
```

---

## 执行清单

- [x] 分析 Remote resolve/rating/archive 实现 → 无独特价值
- [x] 分析 Remote me/notifications/versions/report 实现 → 无独特价值
- [x] 检查 Remote 集成测试 → 需确认是否有价值
- [ ] Force push feat/skillhub-cli 到 remote
- [ ] 验证推送结果
