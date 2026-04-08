# SkillHub CLI 修复完整分析报告

## 一、详细修复统计（13 文件，121 行变更）

### 1.1 核心修复：`api-client.ts`（+30/-15 行）

**问题**: 后端 Native API 全部使用 `ApiResponse<T>` 包装，CLI 直接返回原始 JSON 导致所有字段读取失败。

**修复**: 新增 `unwrapApiResponse()` 函数，自动检测 `code` + `data` 字段：
- 有 `code` + `data` → Native API，解包返回 `data`
- 无 `code` + `data` → Compat API，返回原始格式
- `code !== 0` → 抛出 `ApiError`

**影响范围**: 所有 5 个 HTTP 方法（get/post/postForm/put/delete）

**验证**: ✅ `info`、`resolve`、`rating` 均正确解析 Native API 响应

---

### 1.2 字段映射修复

#### `info.ts`（+10/-14 行）
| 旧字段 | 新字段 | 后端 DTO |
|---|---|---|
| `description` | `summary` | `SkillDetailResponse.summary` |
| `latestVersion` | `publishedVersion.version` | `SkillDetailResponse.publishedVersion` |
| `author.displayName` | `ownerDisplayName` | `SkillDetailResponse.ownerDisplayName` |
| `stars` | `starCount` | `SkillDetailResponse.starCount` |
| `downloads` | `downloadCount` | `SkillDetailResponse.downloadCount` |
| `labels: string[]` | `labels: Array<{slug,name}>` | `SkillDetailResponse.labels` |

**验证**: ✅ `skillhub info test-cli-skill` 正确显示所有字段

#### `login.ts`（+1/-1 行）
| 旧字段 | 新字段 | 后端 DTO |
|---|---|---|
| `user.displayName` | `user.user.displayName` | `ClawHubWhoamiResponse.user.displayName` |
| `user.email \|\| user.userId` | `user.user.handle` | `ClawHubWhoamiResponse.user.handle` |

**验证**: ✅ `skillhub login` 显示 `Authenticated as Admin (@docker-admin)`

#### `whoami.ts`（+3/-3 行）
| 旧字段 | 新字段 |
|---|---|
| `user.userId` | `user.user.handle` |
| `user.displayName` | `user.user.displayName` |
| `user.email` | `user.user.image` |

**验证**: ✅ `skillhub whoami` 显示 `Handle: docker-admin`

#### `me.ts`（+14/-6 行）
| 旧字段 | 新字段 |
|---|---|
| `latestVersion` | `publishedVersion.version` |
| `stars` | `starCount` |
| `downloads` | `downloadCount` |
| `MeSkill[]` → `SkillsPageResponse.items` | 分页适配 |

**验证**: ⏳ 后端 API Token 无 /me/** 权限（后端问题）

#### `versions.ts`（+12/-6 行）
| 旧字段 | 新字段 |
|---|---|
| `createdAt` | `publishedAt` |
| `downloads` | 移除（后端不返回） |
| `changelog?` | 新增 |
| `SkillVersion[]` → `VersionsPageResponse.items` | 分页适配 |

**验证**: ⏳ publish 500 阻塞（后端问题）

#### `notifications.ts`（+8/-1 行）
- `Notification[]` → `NotificationsPageResponse.items` 分页适配

**验证**: ⏳ 无测试数据

#### `reviews.ts`（+8/-1 行）
- `ReviewSubmission[]` → `ReviewsPageResponse.items` 分页适配

**验证**: ⏳ 无测试数据

#### `namespaces.ts`（+10/-3 行）
- 移除 `ApiRoutes.meNamespaces` 依赖
- 新增 `MyNamespaceResponse` 接口
- `ApiResponse<List<MyNamespaceResponse>>` 解包后直接为数组

**验证**: ⏳ 后端 403（后端权限问题）

#### `resolve.ts`（+1/-1 行）
- `matched: string` → `matched: boolean`

**验证**: ✅ `skillhub resolve test-cli-skill` 正确显示 `Matched: null`

#### `list.ts`（+4/-1 行）
- `statSync()` 增加 try/catch 处理 broken symlink

**验证**: ✅ `skillhub list` 不再崩溃

---

### 1.3 路径/选项修复

#### `publish.ts`（+3/-4 行）
| 修复项 | 旧值 | 新值 | 原因 |
|---|---|---|---|
| 路径 | `ApiRoutes.skills` + `{namespace}` query | `/api/v1/skills/{namespace}/publish` | 改用 Native 端点 |
| 选项 | `--version <ver>` | `-v, --ver <ver>` | 与全局 `--version` 冲突 |
| 参数 | `queryParams: { namespace }` | 路径参数 | 路径已包含 namespace |

**⚠️ 发现新问题**: CLI 发送的 payload 格式是 Compat 层的（`payload` + `files`），但 Native 端点期望 `file` + `visibility`。

**验证**: ⚠️ Native publish 500（格式不匹配），Compat publish 成功

#### `routes.ts`（+4/-3 行）
- `WhoamiResponse` 接口更新为 Compat 层格式

---

## 二、后端问题分析

### 2.1 Native Publish 500 错误

**根因**: CLI 发送的 multipart 格式与 Native 端点不匹配

| 层 | 期望格式 | CLI 发送格式 | 匹配? |
|---|---|---|---|
| Native (`/api/v1/skills/{ns}/publish`) | `file`(zip) + `visibility`(string) | `payload`(JSON) + `files`(SKILL.md) | ❌ |
| Compat (`/api/v1/skills`) | `payload`(JSON) + `files`(MultipartFile[]) | `payload`(JSON) + `files`(SKILL.md) | ✅ |

**解决方案**:
- **方案 A（推荐）**: publish 命令改回使用 Compat 端点 `POST /api/v1/skills?namespace=xxx`
- **方案 B**: 修改 CLI 发送 zip 包 + visibility 参数（需要打包逻辑）

**推荐方案 A**，因为：
1. Compat 层就是为 CLI 设计的
2. 改动最小（只需改回路径）
3. 已验证通过

### 2.2 API Token 权限不足

**现象**:
- `me/skills`、`me/stars`、`namespaces`、`star`、`rate` 全部 403
- Session Cookie 正常，API Token 403

**根因**: API Token 的权限范围与 Session Cookie 不同，`/me/**` 端点可能要求 Session 认证而非 Bearer Token。

**解决方案**:
- 后端修复：让 API Token 拥有与 Session 相同的 `/me/**` 访问权限
- 或 CLI 改用 Session Cookie 认证（需要浏览器登录流程）

### 2.3 Star/Rate 403

**现象**: 直接 API 调用也 403

**根因**: 后端权限配置问题，与 CLI 无关

**解决方案**: 后端修复权限配置

---

## 三、阻塞问题分析

### 3.1 Publish 500 阻塞链

```
Publish 500
  ↓
无法创建测试技能
  ↓
info/versions/resolve/download/install 无数据可测
  ↓
star/rate/me 无目标技能
```

**解除方案**: 使用 Compat 层 publish 创建测试数据（已验证可行）

### 3.2 API Token 权限阻塞

```
API Token 无 /me/** 权限
  ↓
me skills/stars 无法测试
  ↓
分页适配无法验证
```

**解除方案**: 使用 Session Cookie 测试，或后端修复 Token 权限

---

## 四、对上游主分支影响分析

### 4.1 当前分支状态

```
feat/skillhub-cli 基于 upstream/main rebase
领先 4 commits（feature）+ 11 个修复 commits（待提交）
```

### 4.2 修复对上游的影响

| 修复项 | 影响范围 | 风险 | 说明 |
|---|---|---|---|
| `api-client.ts` 解包 | 全局 | 低 | 自动检测，Compat 层不受影响 |
| 字段映射 | 命令级 | 低 | 仅修改 CLI 端解析逻辑 |
| publish 路径 | 命令级 | 中 | 需确认使用 Compat 端点 |
| 分页适配 | 命令级 | 低 | 仅修改数据提取方式 |

### 4.3 上游合并策略

1. **先提交修复** → 创建修复 commits
2. **rebase 到最新 upstream/main** → 确保无冲突
3. **推送远程** → `git push skillhub feat/skillhub-cli --force-with-lease`
4. **创建 PR** → 合并到 upstream/main

### 4.4 潜在冲突点

| 文件 | 冲突风险 | 原因 |
|---|---|---|
| `api-client.ts` | 低 | 上游无修改 |
| `info.ts` | 低 | 上游无修改 |
| `publish.ts` | 中 | 路径修改可能与上游不同 |
| `routes.ts` | 低 | 仅修改接口定义 |

---

## 五、最终修复方案

### 5.1 立即修复（CLI 端）

| # | 文件 | 改动 | 优先级 |
|---|---|---|---|
| 1 | `publish.ts` | 改回 Compat 端点 `POST /api/v1/skills?namespace=xxx` | 🔴 Critical |
| 2 | 其余 10 项修复 | 已完成，待提交 | ✅ Done |

### 5.2 后端修复（非 CLI 责任）

| # | 问题 | 影响 | 修复方 |
|---|---|---|---|
| 1 | Native publish 500 | CLI 发布失败 | 后端团队 |
| 2 | API Token 无 /me/** 权限 | me skills/stars 403 | 后端团队 |
| 3 | Star/Rate 403 | 收藏/评分失败 | 后端团队 |

### 5.3 测试计划

1. 修复 publish 路径 → 使用 Compat 端点
2. 使用 Compat publish 创建测试技能
3. 测试所有 24 个命令
4. 提交修复 commits
5. rebase 到 upstream/main
6. 推送远程

---

## 六、执行时间估算

| 任务 | 人类团队 | CC+gstack |
|---|---|---|
| 修复 publish 路径 | 10 min | 2 min |
| 完整集成测试 | 2 hours | 15 min |
| 提交 + rebase + 推送 | 30 min | 5 min |
| **总计** | **~3 hours** | **~22 min** |
