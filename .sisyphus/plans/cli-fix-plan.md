# SkillHub CLI 修复方案

## 现状分析

### 后端双层 API 架构

根据 `docs/06-api-design.md` 第 7.12 节：

| 层 | 端点示例 | 响应格式 |
|---|---|---|
| **Native API** | `/api/v1/skills/**`, `/api/v1/me/**` | 统一包装 `ApiResponse<T> { code, msg, data, timestamp, requestId }` |
| **Compatibility API** | `/api/v1/whoami`, `/api/v1/search`, `/api/v1/publish` | **不套包装**，直接返回 ClawHub 格式 |

### 上游主分支实现情况

上游 `upstream/main` 的 CLI 代码在 `feat/skillhub-cli` 分支上，包含 4 个 feature commits：
1. `d2c383cf` feat: support namespace parameter in CLI compat publish endpoint
2. `1c33d5fc` feat(cli): add complete skill management commands with vercel/skills-style add
3. `f1461431` feat(cli): add me, reviews, notifications, delete, versions, report commands
4. `ee544bc3` feat(cli): add resolve, rating, rate, archive commands + integration test suite

**上游实现的关键问题**：
- 上游 CLI 代码**没有 ApiResponse 解包逻辑** — 所有 HTTP 方法直接返回原始 JSON
- 上游 CLI 混用 Native API 和 Compat API，但**不区分响应格式**
- 上游 `api-client.ts` 的 `get/post/put/delete` 方法都没有 `unwrapApiResponse` 函数

### 当前已修复问题

| # | 文件 | 修复内容 | 状态 |
|---|---|---|---|
| 1 | `api-client.ts` | ApiResponse 自动解包检测（检查 `code`+`data` 字段） | ✅ 已修复 |
| 2 | `info.ts` | 字段映射适配 SkillDetailResponse | ✅ 已修复 |
| 3 | `versions.ts` | PageResponse 分页适配 | ✅ 已修复 |
| 4 | `me.ts` | PageResponse 分页 + 字段映射 | ✅ 已修复 |
| 5 | `notifications.ts` | PageResponse 分页适配 | ✅ 已修复 |
| 6 | `namespaces.ts` | ApiResponse 数组适配 | ✅ 已修复 |
| 7 | `publish.ts` | 改用现代路径 + `--version` → `-v, --ver` | ✅ 已修复 |
| 8 | `reviews.ts` | PageResponse 分页适配 | ✅ 已修复 |
| 9 | `resolve.ts` | `matched: string` → `boolean` | ✅ 已修复 |
| 10 | `list.ts` | broken symlink try/catch | ✅ 已修复 |

### 剩余问题

| # | 命令 | 问题 | 严重性 |
|---|---|---|---|
| 1 | `login` / `whoami` | 调用 `/api/v1/whoami` (Compat 层)，返回 `{user:{handle,displayName,image}}`，CLI 期望 `{userId,displayName,email}` | 🔴 Critical |
| 2 | `star` | 先 GET `/api/v1/skills/{ns}/{slug}` (Native) 获取 skill ID，ApiResponse 解包后 `detail.id` 可用 | ✅ 已修复 |
| 3 | `rating` / `rate` | 同上，先获取 skill ID 再操作 | ✅ 已修复 |

**唯一剩余问题**: `login` / `whoami` 命令的字段映射。

## 修复方案

### 修复 `login.ts` 和 `whoami.ts`

Compat 层 whoami 返回:
```json
{ "user": { "handle": "xxx", "displayName": "xxx", "image": "xxx" } }
```

CLI 当前期望:
```typescript
interface WhoamiResponse {
  userId: string;
  displayName: string;
  email?: string;
}
```

**方案**: 更新 `login.ts` 和 `whoami.ts` 适配 compat 层返回格式。

### 修改文件

1. `skillhub-cli/src/schema/routes.ts` — 更新 WhoamiResponse 接口
2. `skillhub-cli/src/commands/login.ts` — 更新字段引用
3. `skillhub-cli/src/commands/whoami.ts` — 更新字段引用

### 具体改动

```typescript
// routes.ts — 更新 WhoamiResponse
export interface WhoamiResponse {
  user: {
    handle: string;
    displayName: string;
    image: string;
  };
}
```

```typescript
// login.ts — 更新显示逻辑
success(`Authenticated as ${user.user.displayName} (${user.user.handle})`);
```

```typescript
// whoami.ts — 更新显示逻辑
info(`${user.user.displayName} (@${user.user.handle})`);
if (user.user.image) dim(`Avatar: ${user.user.image}`);
```

## 测试计划

后端已启动，使用 mock auth (`X-Mock-User-Id` header) 或 bootstrap admin 登录。

### 测试步骤

1. Bootstrap 登录获取 session
2. 测试 `login` / `whoami` 命令
3. 测试 `search` 命令
4. 测试 `publish` 命令
5. 测试 `info` / `versions` / `resolve` 命令
6. 测试 `star` / `rating` / `rate` 命令
7. 测试 `me skills` / `me stars` 命令
8. 测试 `notifications` / `reviews` / `namespaces` 命令
9. 测试 `delete` / `archive` 命令
10. 测试 `download` / `install` 命令

## 风险评估

- **低风险**: 所有改动都是 CLI 端字段映射适配，不涉及后端
- **ApiResponse 解包**: 使用 `code` + `data` 字段检测，对 Compat 层自动跳过，对 Native 层正确解包
- **向后兼容**: 不影响现有功能，仅修复不工作的命令
