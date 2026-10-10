# OSS-01 Core 契约审计与冻结

## 1. 审计结论

SkillHub 开源项目已具备 AstronClaw 主链路所需的绝大部分 Core 能力。现有接口覆盖了 skill 唯一标识查询、版本元数据查询、创建（发布）和删除。**无需在开源 Core 中新增 AstronClaw 专属接口**；对 AstronClaw 而言，查询类和主链路类能力都应统一由 SaaS 层 `AstronClaw Adapter` 封装后对外提供，而不是直接绑定开源 Core 的接口形态。

---

## 2. Core 接口清单

以下接口构成 Core 基线能力，供 SaaS 层统一封装后对 AstronClaw 提供；这些接口本身不应被视为 AstronClaw 的长期直接契约。

### 2.1 skill 唯一标识与详情查询

| 接口 | 路径 | 说明 |
|------|------|------|
| skill 详情 | `GET /api/v1/skills/{namespace}/{slug}` | 返回 `SkillDetailResponse`，包含完整 identity 和状态 |
| 版本解析 | `GET /api/v1/skills/{namespace}/{slug}/resolve?version=&tag=&hash=` | 返回 `ResolveVersionResponse`，解析人类可读版本选择器到精确版本 |

### 2.2 指定版本安装元数据查询

| 接口 | 路径 | 说明 |
|------|------|------|
| 版本详情 | `GET /api/v1/skills/{namespace}/{slug}/versions/{version}` | 返回 `SkillVersionDetailResponse`，含 metadata 和 manifest |
| 版本文件列表 | `GET /api/v1/skills/{namespace}/{slug}/versions/{version}/files` | 返回 `List<SkillFileResponse>` |
| 版本下载 | `GET /api/v1/skills/{namespace}/{slug}/versions/{version}/download` | 下载指定版本 bundle |
| 版本列表 | `GET /api/v1/skills/{namespace}/{slug}/versions?page=&size=` | 分页返回版本列表 |

### 2.3 创建（发布）个人 skill

| 接口 | 路径 | 说明 |
|------|------|------|
| 发布 skill | `POST /api/v1/skills/{namespace}/publish` | 上传包并发布，返回 `PublishResponse` |

### 2.4 删除个人 skill

| 接口 | 路径 | 说明 |
|------|------|------|
| 硬删除（by ID） | `DELETE /api/v1/skills/id/{skillId}` | 需 SUPER_ADMIN 权限 |
| 硬删除（by 坐标） | `DELETE /api/v1/skills/{namespace}/{slug}` | 需 SUPER_ADMIN 权限 |
| 归档 | `POST /api/v1/skills/{namespace}/{slug}/archive` | owner 或 namespace admin 可操作 |
| 取消归档 | `POST /api/v1/skills/{namespace}/{slug}/unarchive` | 恢复为 ACTIVE |

### 2.5 版本生命周期

| 接口 | 路径 | 说明 |
|------|------|------|
| 删除版本 | `DELETE /api/v1/skills/{namespace}/{slug}/versions/{version}` | 仅 DRAFT/REJECTED/SCAN_FAILED 可删 |
| 撤回审核 | `POST /api/v1/skills/{namespace}/{slug}/versions/{version}/withdraw-review` | PENDING_REVIEW → DRAFT |
| 重新发布 | `POST /api/v1/skills/{namespace}/{slug}/versions/{version}/rerelease` | 重新发布版本 |

### 2.6 ClawHub 兼容接口（已有）

| 接口 | 路径 | 说明 |
|------|------|------|
| 解析 skill | `GET /api/v1/resolve?slug=&version=` | ClawHub 协议兼容 |
| 解析 skill（路径） | `GET /api/v1/resolve/{canonicalSlug}?version=` | ClawHub 协议兼容 |
| 下载 | `GET /api/v1/download/{canonicalSlug}?version=` | 302 重定向到下载地址 |
| 删除 skill | `DELETE /api/v1/skills/{canonicalSlug}` | owner 可操作 |
| 取消删除 | `POST /api/v1/skills/{canonicalSlug}/undelete` | owner 可操作 |
| 发布 skill | `POST /api/v1/skills` | ClawHub 协议兼容 |
| 发布到 namespace | `POST /api/v1/publish` | ClawHub 协议兼容 |

---

## 3. 字段语义冻结表

### 3.1 Skill Identity 字段

| 字段 | 类型 | 含义 | 稳定性 | 说明 |
|------|------|------|--------|------|
| `skill.id` | Long | skill 全局唯一主键 | 不可变 | 自增，创建后永不改变，可作为外部映射主键 |
| `namespace` (slug) | String(64) | skill 所属命名空间标识 | 不可变 | 全局唯一，创建后不可改名 |
| `skill.slug` | String(100) | skill 在 namespace 内的唯一标识 | 不可变 | 创建后不可改名，`namespace + slug` 构成业务坐标 |
| `skill.displayName` | String(200) | skill 展示名称 | 可变 | 仅用于展示，不可作为映射依据 |
| `skill.ownerId` | String | skill 创建者 ID | 不可变 | 创建时绑定，不可转移 |
| `skill.summary` | String(TEXT) | skill 简介 | 可变 | 展示用 |
| `skill.visibility` | Enum | 可见性 | 可变 | `PUBLIC` / `NAMESPACE_ONLY` / `PRIVATE` |
| `skill.status` | Enum | skill 状态 | 可变 | `ACTIVE` / `HIDDEN` / `ARCHIVED` |
| `skill.hidden` | boolean | 是否被管理员隐藏 | 可变 | 与 status 独立的隐藏标记 |
| `skill.latestVersionId` | Long | 最新版本指针 | 可变 | 指向当前最新已发布版本，yank/删除后自动回退 |
| `skill.downloadCount` | Long | 下载次数 | 可变 | 累计值 |
| `skill.starCount` | Integer | 收藏数 | 可变 | 累计值 |

### 3.2 SkillVersion 字段

| 字段 | 类型 | 含义 | 稳定性 | 说明 |
|------|------|------|--------|------|
| `version.id` | Long | 版本全局唯一主键 | 不可变 | 自增 |
| `version.skillId` | Long | 所属 skill ID | 不可变 | 外键 |
| `version.version` | String(64) | 版本号 | 不可变 | 如 `1.0.0`，创建后不可改 |
| `version.status` | Enum | 版本状态 | 可变 | 见状态语义表 |
| `version.bundleReady` | boolean | bundle 是否可用 | 可变 | `true` 表示 bundle 已构建完成，可下载安装 |
| `version.downloadReady` | boolean | 是否允许下载 | 可变 | yank 后设为 `false` |
| `version.publishedAt` | Instant | 发布时间 | 一次写入 | 首次发布时设置 |
| `version.parsedMetadataJson` | JSONB | 解析后的元数据 | 一次写入 | 包含 `package_name` 等运行时信息 |
| `version.manifestJson` | JSONB | manifest 原始内容 | 一次写入 | skill 包的 manifest |
| `version.changelog` | String(TEXT) | 变更日志 | 可变 | 展示用 |
| `version.fileCount` | Integer | 文件数量 | 一次写入 | 发布时确定 |
| `version.totalSize` | Long | 总大小（字节） | 一次写入 | 发布时确定 |
| `version.yankedAt` | Instant | yank 时间 | 一次写入 | yank 时设置 |
| `version.yankReason` | String(TEXT) | yank 原因 | 一次写入 | yank 时设置 |

### 3.3 关键字段含义冻结

| 字段 | 冻结定义 |
|------|----------|
| `skill_id` | `skill.id`，Long 类型自增主键，全局唯一，创建后不可变。AstronClaw 应以此作为 `external_skill_mapping` 的外部主键 |
| `namespace` | `namespace.slug`，String(64)，全局唯一，不可改名。与 `slug` 组合构成业务坐标 |
| `slug` | `skill.slug`，String(100)，namespace 内唯一，不可改名。`namespace/slug` 是人类可读的稳定坐标 |
| `version` | `skill_version.version`，String(64)，同一 skill 内唯一，不可改。如 `1.0.0` |
| `bundle_url` | 通过 `GET /{namespace}/{slug}/versions/{version}/download` 获取，或通过 `resolve` 接口的 `downloadUrl` 字段获取。不是数据库字段，而是动态生成的下载地址 |
| `bundle_ready` | `skill_version.bundleReady`，boolean。`true` 表示 bundle 已构建完成可安装。AstronClaw 安装前必须校验此字段 |
| `package_name` | 存储在 `skill_version.parsedMetadataJson` 中，从 skill 包的 manifest 解析而来。同一 skill 跨版本应保持稳定。AstronClaw 用于运行时安装/卸载标识 |

### 3.4 Namespace 字段

| 字段 | 类型 | 含义 | 稳定性 |
|------|------|------|--------|
| `namespace.id` | Long | 命名空间主键 | 不可变 |
| `namespace.slug` | String(64) | 命名空间标识 | 不可变，全局唯一 |
| `namespace.displayName` | String(128) | 展示名称 | 可变 |
| `namespace.type` | Enum | 类型 | 不可变，`GLOBAL` / `TEAM` |
| `namespace.status` | Enum | 状态 | 可变，`ACTIVE` / `FROZEN` / `ARCHIVED` |

---

## 4. 状态语义冻结表

### 4.1 Skill 状态（`SkillStatus`）

| 状态 | 市场可见 | 可新装 | 已装是否保留 | 可被 owner 操作 | 说明 |
|------|----------|--------|------------|----------------|------|
| `ACTIVE` | 是（受 visibility 控制） | 是（需有 PUBLISHED 版本） | 是 | 是 | 正常状态 |
| `HIDDEN` | 否 | 否 | 是 | 受限 | 管理员隐藏，独立于 status 的 `hidden` 标记 |
| `ARCHIVED` | 否 | 否 | 是 | 可取消归档 | owner 或 namespace admin 归档 |

### 4.2 版本状态（`SkillVersionStatus`）

| 状态 | 是否允许安装 | 是否允许下载 | 市场可见 | 可转换到 | 说明 |
|------|------------|------------|---------|---------|------|
| `DRAFT` | 否 | 否 | 否 | SCANNING, 可删除 | 初始状态，编辑中 |
| `SCANNING` | 否 | 否 | 否 | SCAN_FAILED, PENDING_REVIEW, PUBLISHED | 安全扫描中 |
| `SCAN_FAILED` | 否 | 否 | 否 | 可删除 | 安全扫描失败 |
| `PENDING_REVIEW` | 否 | 否 | 否 | PUBLISHED, REJECTED, → DRAFT(撤回) | 等待审核 |
| `PUBLISHED` | 是 | 是 | 是 | YANKED | 已发布，可安装 |
| `REJECTED` | 否 | 否 | 否 | 可删除 | 审核拒绝 |
| `YANKED` | 否 | 否 | 否（或弱可见） | 不可逆 | 已撤回，已装不受影响 |

### 4.3 可见性（`SkillVisibility`）

| 可见性 | 市场列表可见 | 谁可查看 | 谁可安装 |
|--------|------------|---------|---------|
| `PUBLIC` | 是 | 所有人 | 所有人（需 PUBLISHED + bundleReady） |
| `NAMESPACE_ONLY` | 否 | namespace 成员 | namespace 成员 |
| `PRIVATE` | 否 | 仅 owner | 仅 owner |

### 4.4 删除语义

| 操作 | 类型 | 可逆 | 数据影响 | 已装实例影响 |
|------|------|------|---------|------------|
| 硬删除 skill | 永久删除 | 否 | 删除所有记录、文件、存储对象，slug 可复用 | 不影响，AstronClaw 已装快照独立 |
| 归档 skill | 状态变更 | 是 | 无数据删除，status → ARCHIVED | 不影响 |
| 隐藏 skill | 标记变更 | 是 | 无数据删除，hidden → true | 不影响 |
| 删除版本 | 永久删除 | 否 | 仅删除 DRAFT/REJECTED/SCAN_FAILED 版本 | 不影响（这些版本未被安装） |
| Yank 版本 | 状态变更 | 否 | status → YANKED，downloadReady → false | 不影响已装实例 |

### 4.5 AstronClaw 安装判断规则

AstronClaw 判断一个 skill 版本是否可安装，需同时满足：

```
skill.status == ACTIVE
  AND skill.hidden == false
  AND skill.visibility 允许当前用户访问
  AND version.status == PUBLISHED
  AND version.bundleReady == true
```

已安装实例不受后续状态变更影响。即使 skill 被删除/归档/隐藏，或版本被 yank，AstronClaw 本地安装快照仍可正常使用和卸载。

## 5. 错误语义表

### 5.1 统一响应结构

```json
{
  "code": 0,
  "msg": "操作成功",
  "data": { ... },
  "timestamp": "2026-04-10T08:00:00Z",
  "requestId": "req-xxx"
}
```

- `code = 0` 表示成功
- `code > 0` 表示错误，值为 HTTP 状态码

### 5.2 错误码映射

| HTTP 状态码 | 场景 | 异常类型 | 说明 |
|------------|------|---------|------|
| 400 | 参数非法 | `BadRequestException` / `DomainBadRequestException` | 请求参数校验失败 |
| 401 | 未认证 | `UnauthorizedException` / `AuthFlowException` | 未登录或 token 过期 |
| 403 | 无权限 | `ForbiddenException` / `DomainForbiddenException` | 无操作权限 |
| 404 | 未找到 | `DomainNotFoundException` | skill/version/namespace 不存在 |
| 408 | 请求超时 | `AsyncRequestTimeoutException` | 异步请求超时 |
| 503 | 存储不可用 | `StorageAccessException` | 对象存储访问失败 |
| 500 | 服务异常 | `Exception` | 未预期的内部错误 |

### 5.3 Core 主链路关键错误场景

| 场景 | HTTP 状态码 | msg 示例 | AstronClaw 处理建议 |
|------|-----------|---------|-------------------|
| skill 不存在 | 404 | `error.skill.notFound` | 映射失败，提示用户 |
| 版本不存在 | 404 | `error.skill.notFound` | 安装/升级失败，提示用户 |
| 版本不可安装（非 PUBLISHED） | 400 | `error.badRequest` | 拒绝安装，提示版本状态 |
| bundle 未就绪 | 400 | `error.badRequest` | 拒绝安装，提示稍后重试 |
| 无权访问（PRIVATE skill） | 403 | `error.forbidden` | 提示无权限 |
| namespace 不存在 | 404 | `error.namespace.notFound` | 映射失败 |
| 存储服务不可用 | 503 | `error.storage.unavailable` | 降级处理，已装 skill 不受影响 |
| 删除不允许（非 owner） | 403 | `error.forbidden` | 提示无权限 |

---

## 6. Core vs SaaS Adapter 能力分界

### 6.1 Core 已满足的能力

说明：

下表表示“开源 Core 已具备、可供 SaaS 封装”的能力，并不表示 AstronClaw 应直接调用这些开源接口。

| PRD 需求 | Core 接口 | 满足程度 | 备注 |
|---------|----------|---------|------|
| skill 唯一标识查询 | `GET /{namespace}/{slug}` | 完全满足 | 返回 `id`、`namespace`、`slug` |
| 指定版本安装元数据 | `GET /{namespace}/{slug}/versions/{version}` | 基本满足 | 返回 status、metadata；`package_name` 在 `parsedMetadataJson` 中 |
| 版本解析 | `GET /{namespace}/{slug}/resolve` | 完全满足 | 支持 version/tag/hash 解析 |
| bundle 下载 | `GET /{namespace}/{slug}/versions/{version}/download` | 完全满足 | 直接下载 |
| 创建（发布）个人 skill | `POST /{namespace}/publish` | 完全满足 | 返回 skillId、namespace、slug、version、status |
| 删除个人 skill | `DELETE /{namespace}/{slug}` (ClawHub 兼容) | 完全满足 | owner 可操作 |
| 归档 skill | `POST /{namespace}/{slug}/archive` | 完全满足 | 可逆操作 |
| 版本状态查询 | `GET /{namespace}/{slug}` 中的 headlineVersion/publishedVersion | 完全满足 | 包含版本状态 |
| labels 数据 | `GET /{namespace}/{slug}` 中的 labels 字段 | 完全满足 | 返回 `List<SkillLabelDto>` |

### 6.2 需要 SaaS Adapter 新增的能力

| PRD 需求 | 原因 | Adapter 建议 |
|---------|------|-------------|
| 市场列表查询（搜索/过滤/排序） | Core 不提供面向页面的聚合列表 | `GET /api/v1/astronclaw/adapter/skills/market` |
| 市场详情（AstronClaw DTO） | Core 返回的 DTO 包含 Core 内部字段，需适配 | `GET /api/v1/astronclaw/adapter/skills/{id}` |
| owner 维度"我创建的"查询 | Core 的 `/me/skills` 返回 Core DTO，需适配 | `GET /api/v1/astronclaw/adapter/skills/mine` |
| `is_installed` 补全 | 安装关系在 AstronClaw 侧 | AstronClaw 本地补全，不在 Adapter |
| `package_name` 顶层字段 | 当前在 `parsedMetadataJson` 内，需提取 | Adapter 解析 JSON 后平铺返回 |
| `bundle_url` 直接返回 | 当前需通过 download 接口获取 | Adapter 可直接返回预签名 URL |
| 统一 `can_install` 判断 | 需组合 status + visibility + bundleReady | Adapter 计算后返回布尔值 |
| 统一 `can_delete` 判断 | 需组合 owner + status | Adapter 计算后返回布尔值 |

### 6.3 分界原则

```
Core 负责：skill 生命周期真相（identity、version、status、artifact）
Adapter 负责：面向 AstronClaw 的 DTO 适配（字段平铺、状态聚合、权限预判断）
```

补充原则：

1. 即使开源 `Core` 已经具备某项主链路能力，`AstronClaw` 仍应统一通过 SaaS Adapter 消费。
2. 该原则同时适用于唯一标识查询、版本元数据、创建个人 skill、删除个人 skill。
3. 开源文档中的接口清单用于说明 `Core` 能力边界，不应被解读为 AstronClaw 的直接对接建议。

---

## 7. 成功 / 失败 / 边界样例

### 7.1 查询 skill identity — 成功

```
GET /api/v1/skills/my-namespace/my-skill
```

```json
{
  "code": 0,
  "data": {
    "id": 42,
    "slug": "my-skill",
    "displayName": "My Skill",
    "ownerId": "user-123",
    "status": "ACTIVE",
    "visibility": "PUBLIC",
    "namespace": "my-namespace",
    "labels": [{"slug": "nlp", "type": "CATEGORY", "displayName": "NLP"}],
    "headlineVersion": {"id": 100, "version": "1.2.0", "status": "PUBLISHED"},
    "publishedVersion": {"id": 100, "version": "1.2.0", "status": "PUBLISHED"}
  }
}
```

AstronClaw 映射关键字段：`id=42`，`namespace=my-namespace`，`slug=my-skill`。

### 7.2 查询 skill identity — 不存在

```
GET /api/v1/skills/my-namespace/nonexistent
```

```json
{
  "code": 404,
  "msg": "Skill not found",
  "data": null
}
```

### 7.3 查询指定版本元数据 — 成功

```
GET /api/v1/skills/my-namespace/my-skill/versions/1.2.0
```

```json
{
  "code": 0,
  "data": {
    "id": 100,
    "version": "1.2.0",
    "status": "PUBLISHED",
    "changelog": "Bug fixes",
    "fileCount": 3,
    "totalSize": 102400,
    "publishedAt": "2026-04-01T10:00:00Z",
    "parsedMetadataJson": "{\"name\":\"my-skill\",\"package_name\":\"my_namespace__my_skill\",\"version\":\"1.2.0\"}",
    "manifestJson": "{...}"
  }
}
```

`package_name` 从 `parsedMetadataJson` 中提取。

### 7.4 查询已 YANKED 版本

```
GET /api/v1/skills/my-namespace/my-skill/versions/1.0.0
```

```json
{
  "code": 0,
  "data": {
    "id": 98,
    "version": "1.0.0",
    "status": "YANKED",
    "publishedAt": "2026-03-01T10:00:00Z"
  }
}
```

AstronClaw 判断 `status != PUBLISHED`，拒绝新安装。已装实例不受影响。

### 7.5 发布（创建）个人 skill — 成功

```
POST /api/v1/skills/my-namespace/publish
Content-Type: multipart/form-data
file: <skill-package.tar.gz>
visibility: PRIVATE
```

```json
{
  "code": 0,
  "data": {
    "skillId": 43,
    "namespace": "my-namespace",
    "slug": "new-skill",
    "version": "0.1.0",
    "status": "DRAFT",
    "fileCount": 2,
    "totalSize": 51200
  }
}
```

### 7.6 删除个人 skill — 成功

```
DELETE /api/v1/skills/my-namespace/my-skill
```

```json
{
  "code": 0,
  "data": {
    "ok": true
  }
}
```

### 7.7 删除个人 skill — 无权限

```
DELETE /api/v1/skills/other-namespace/other-skill
```

```json
{
  "code": 403,
  "msg": "Forbidden",
  "data": null
}
```

### 7.8 边界：skill 已归档后查询

```
GET /api/v1/skills/my-namespace/archived-skill
```

```json
{
  "code": 0,
  "data": {
    "id": 44,
    "slug": "archived-skill",
    "status": "ARCHIVED",
    "visibility": "PUBLIC"
  }
}
```

skill 仍可查询，但 AstronClaw 应根据 `status=ARCHIVED` 判断不可新装。

---

## 8. 遗留问题与建议

### 8.1 `package_name` 提取

当前 `package_name` 嵌套在 `parsedMetadataJson` JSONB 字段中，不是顶层字段。

建议：SaaS Adapter 在返回 AstronClaw DTO 时，解析 JSON 并将 `package_name` 提取为顶层字段。Core 不需要改动。

### 8.2 `bundle_url` 获取方式

当前没有直接返回 `bundle_url` 的字段，需通过 download 接口获取。`ResolveVersionResponse` 中有 `downloadUrl` 字段。

建议：SaaS Adapter 可通过 `resolve` 接口获取 `downloadUrl`，或直接生成预签名 URL 返回给 AstronClaw。

### 8.3 删除接口权限

当前 `DELETE /api/v1/skills/{namespace}/{slug}`（portal 路径）需要 SUPER_ADMIN 权限。ClawHub 兼容接口 `DELETE /api/v1/skills/{canonicalSlug}` 允许 owner 操作。

建议：SaaS Adapter 应统一封装 owner 可操作的删除接口，对 AstronClaw 暴露稳定契约；AstronClaw 不直接依赖开源删除接口路径。

### 8.4 `hidden` 与 `status` 的关系

当前 `hidden` 是独立于 `status` 的布尔标记（管理员操作），而 `HIDDEN` 是 `SkillStatus` 枚举值之一但实际代码中 skill 的 status 枚举包含 `ACTIVE`、`HIDDEN`、`ARCHIVED`。

建议：SaaS Adapter 统一为 AstronClaw 提供一个 `is_visible` 聚合字段，屏蔽内部 hidden 标记与 status 的复杂关系。
