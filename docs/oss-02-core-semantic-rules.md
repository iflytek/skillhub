# OSS-02 Core 语义规则收口

## 1. 文档目标

本文档固化 SkillHub Core 的运行时语义规则，确保开源版与 SaaS 版对删除、YANKED、同名冲突、package_name 等规则口径一致，避免 AstronClaw 接入后出现状态漂移。本文定义的是可由 SaaS 统一封装并对 AstronClaw 提供的 `Core` 规则基线，不表示 AstronClaw 直接对接这些开源接口。

---

## 2. 变更概要

### 2.1 新增功能

| 功能 | 说明 |
|------|------|
| UPLOADED 状态 | 新增版本状态，表示"已上传，未提交审核" |
| PRIVATE skill 自动发布 | PRIVATE skill 发布后进入 UPLOADED 状态，不自动进入审核 |
| 提交审核接口 | 新增 `POST /{namespace}/{slug}/submit-review`，允许 UPLOADED 状态的版本提交审核 |
| 撤回审核后进入 UPLOADED | 撤回审核后版本状态变为 UPLOADED，而不是 DRAFT |

### 2.2 状态机变更

**变更前**：
```
DRAFT → SCANNING → PENDING_REVIEW → PUBLISHED
                         ↓            ↓
                    REJECTED      YANKED
```

**变更后**：
```
DRAFT → SCANNING → UPLOADED → PENDING_REVIEW → PUBLISHED
         ↓              ↓           ↓            ↓
    SCAN_FAILED    (可删除)     REJECTED      YANKED
         ↓                       ↓
      (可删除)                (可删除)
```

### 2.3 权限模型变更

**核心原则**：权限只和 status 相关，visibility 只影响状态流转。

---

## 3. 版本状态定义

### 3.1 状态枚举

```java
public enum SkillVersionStatus {
    DRAFT,           // 草稿，编辑中
    SCANNING,        // 安全扫描中
    SCAN_FAILED,     // 扫描失败
    UPLOADED,        // 已上传，未提交审核（新增）
    PENDING_REVIEW,  // 等待审核
    PUBLISHED,       // 已发布
    REJECTED,        // 审核拒绝
    YANKED           // 已撤回
}
```

### 3.2 状态语义

| 状态 | 含义 | 文件状态 | 可下载 | 可编辑 | 有检测报告 |
|------|------|---------|-------|-------|----------|
| DRAFT | 草稿，编辑中 | 可能不完整 | 否 | 是 | 否 |
| SCANNING | 安全扫描中 | 完整 | 否 | 否 | 否 |
| SCAN_FAILED | 扫描失败 | 完整 | 否 | 是 | 是（失败） |
| UPLOADED | 已上传，扫描通过 | 完整 | owner | 否 | 是 |
| PENDING_REVIEW | 审核中 | 完整 | owner | 否 | 是 |
| PUBLISHED | 已发布 | 完整 | 看 visibility | 否 | 是 |
| REJECTED | 审核拒绝 | 完整 | 否 | 是 | 是 |
| YANKED | 已撤回 | 完整 | 否 | 否 | 是 |

---

## 4. 发布流程设计

### 4.1 发布路径

| visibility | 发布后初始状态 | 是否创建审核任务 |
|------------|--------------|----------------|
| PRIVATE | UPLOADED | 否 |
| NAMESPACE_ONLY | PENDING_REVIEW | 是 |
| PUBLIC | PENDING_REVIEW | 是 |

### 4.2 PRIVATE skill 完整生命周期

```
用户发布 PRIVATE skill
    ↓
状态：SCANNING（安全扫描中）
    ↓
扫描通过
    ↓
状态：UPLOADED
visibility：PRIVATE
    ↓
owner 可下载/安装/测试
市场不可见
管理员可见（用于审计）
已有检测报告
    ↓
owner 测试满意，确认发布（confirm-publish）
    ↓
状态：PUBLISHED
visibility：PRIVATE（正式私有版本）
    ↓
owner 可下载/安装
市场不可见
    ↓
用户想公开，提交审核
    ↓
状态：PENDING_REVIEW
requestedVisibility：PUBLIC
    ↓
owner 仍可下载/测试
    ↓
审核通过
    ↓
状态：PUBLISHED
visibility：PUBLIC（不再是 PRIVATE）
    ↓
市场可见，所有人可下载
```

### 4.3 PUBLIC/NAMESPACE_ONLY skill 生命周期

```
用户发布 PUBLIC/NAMESPACE_ONLY skill
    ↓
状态：PENDING_REVIEW
    ↓
owner 可下载/测试
    ↓
审核通过
    ↓
状态：PUBLISHED
visibility：PUBLIC 或 NAMESPACE_ONLY
    ↓
市场可见（受 visibility 控制）
```

---

## 5. 权限矩阵

### 5.1 status 决定下载权限

| status | 市场可见 | 可下载 |
|--------|---------|-------|
| DRAFT | 否 | 否 |
| SCANNING | 否 | 否 |
| SCAN_FAILED | 否 | 否 |
| UPLOADED | 否 | owner |
| PENDING_REVIEW | 否 | owner |
| PUBLISHED | 看 visibility | 看 visibility |
| REJECTED | 否 | 否 |
| YANKED | 否 | 否 |

### 5.2 PUBLISHED 状态下，visibility 决定可见性

| visibility | 市场可见 | 可下载 |
|------------|---------|-------|
| PUBLIC | 是 | 所有人 |
| NAMESPACE_ONLY | 命名空间内 | 命名空间成员 |
| PRIVATE | 否 | owner |

### 5.3 AstronClaw 安装判断规则

```
可安装 = 
  skill.status == ACTIVE
  AND skill.hidden == false
  AND 存在至少一个可下载版本
  AND 该版本 bundleReady == true

可下载版本判断：
  - UPLOADED/PENDING_REVIEW：仅 owner
  - PUBLISHED：按 visibility 规则
```

---

## 6. 状态流转详细设计

### 6.1 状态转换表

| 当前状态 | 操作 | 目标状态 | 说明 |
|---------|------|---------|------|
| DRAFT | 上传包 | SCANNING | 开始安全扫描 |
| SCANNING | 扫描通过 | UPLOADED 或 PENDING_REVIEW | 看 visibility |
| SCANNING | 扫描失败 | SCAN_FAILED | - |
| SCAN_FAILED | 重新上传 | SCANNING | - |
| UPLOADED | 提交审核 | PENDING_REVIEW | 新增操作 |
| UPLOADED | 确认发布 | PUBLISHED | PRIVATE skill 正式发布，不触发新扫描 |
| UPLOADED | 重新上传 | SCANNING | 允许重新上传 |
| UPLOADED | 删除 | (删除) | 允许删除，未正式发布 |
| PENDING_REVIEW | 审核通过 | PUBLISHED | - |
| PENDING_REVIEW | 审核拒绝 | REJECTED | - |
| PENDING_REVIEW | 撤回审核 | UPLOADED | 变更：原为 DRAFT |
| PUBLISHED | Yank | YANKED | - |
| REJECTED | 重新上传 | SCANNING | - |

### 6.2 状态机图

```
                    ┌─────────────────────────────────────────┐
                    │              上传包                      │
                    └─────────────────────────────────────────┘
                                      ↓
                              ┌───────────────┐
                              │   SCANNING    │
                              └───────────────┘
                               /            \
                     扫描通过  /              \ 扫描失败
                             /                \
               ┌────────────────────────┐  ┌───────────────┐
               │ visibility=PRIVATE     │  │ SCAN_FAILED   │
               │ → UPLOADED             │  └───────────────┘
               │ visibility=PUBLIC/     │         │
               │   NAMESPACE_ONLY       │         │ 重新上传
               │ → PENDING_REVIEW       │         ↓
               └────────────────────────┘  ┌───────────────┐
                             │             │   SCANNING    │
                             ↓             └───────────────┘
               ┌────────────────────────┐
               │       UPLOADED         │◄────────────────────────┐
               │  (PRIVATE skill 专属)   │                         │
               │  已有检测报告           │                         │
               └────────────────────────┘                         │
                    /           \                                 │
        确认发布   /             \ 提交审核                        │
   (不触发新扫描) /               \                               │
                /                 \                              │
               ↓                   ↓                             │
    ┌───────────────────┐  ┌───────────────────┐                 │
    │ PUBLISHED         │  │  PENDING_REVIEW   │                 │
    │ visibility=PRIVATE│  └───────────────────┘                 │
    └───────────────────┘           │                           │
              │                     │                           │
              │ 提交审核             │ 审核通过                   │
              ↓                     ↓                           │
    ┌───────────────────┐  ┌───────────────────┐                 │
    │  PENDING_REVIEW   │  │    PUBLISHED      │                 │
    └───────────────────┘  │ visibility=PUBLIC │                 │
              │            │ 或 NAMESPACE_ONLY │                 │
              │            └───────────────────┘                 │
              │ 撤回审核              │                          │
              └──────────────────────┘                          │
                      (进入 UPLOADED)                            │
                                                                  │
    ┌───────────────────┐                                        │
    │     REJECTED      │────────────────────────────────────────┘
    └───────────────────┘              重新上传
              │
              │ 删除
              ↓
           (删除)
```

---

## 7. 新增接口设计

说明：

以下接口属于开源 `Core` 为 SaaS 提供的基础状态机能力。对 `AstronClaw` 而言，后续仍应统一通过 `SkillHub SaaS` 的 `AstronClaw Adapter` 消费这些能力，而不是直接绑定这些开源接口路径。

### 7.1 提交审核接口

**接口**：`POST /api/v1/skills/{namespace}/{slug}/submit-review`

**请求参数**：
```json
{
  "version": "1.0.0",
  "targetVisibility": "PUBLIC"
}
```

**前置条件**：
- 版本状态为 UPLOADED
- 操作者为 skill owner 或 namespace ADMIN/OWNER

**执行效果**：
- 版本状态 → PENDING_REVIEW
- `requestedVisibility` 设为目标可见性
- 创建审核任务

**响应**：
```json
{
  "code": 0,
  "data": {
    "versionId": 100,
    "status": "PENDING_REVIEW",
    "requestedVisibility": "PUBLIC"
  }
}
```

### 7.2 确认发布接口（PRIVATE skill）

**接口**：`POST /api/v1/skills/{namespace}/{slug}/confirm-publish`

**请求参数**：
```json
{
  "version": "1.0.0"
}
```

**前置条件**：
- 版本状态为 UPLOADED
- skill.visibility = PRIVATE
- 操作者为 skill owner

**执行效果**：
- 版本状态 → PUBLISHED
- visibility 保持 PRIVATE
- **不触发新的扫描**，复用 UPLOADED 时的扫描结果
- 未来可扩展：加入"发布扫描"功能

**响应**：
```json
{
  "code": 0,
  "data": {
    "skillId": 42,
    "versionId": 100,
    "status": "PUBLISHED",
    "visibility": "PRIVATE"
  }
}
```

---

## 8. 删除 / 隐藏 / 归档 / YANKED 语义规则

### 8.1 操作语义总表

| 操作 | 触发方式 | 可逆 | 市场可见 | 可新装 | 已装保留 | 可卸载 | slug 可复用 |
|------|---------|------|---------|-------|---------|-------|-----------|
| **硬删除 skill** | owner 或 SUPER_ADMIN | 否 | 否 | 否 | 是 | 是 | 是 |
| **归档 skill** | owner / namespace admin | 是 | 否 | 否 | 是 | 是 | 否 |
| **隐藏 skill** | 管理员 | 是 | 否 | 否 | 是 | 是 | 否 |
| **Yank 版本** | owner / namespace admin | 否 | 否 | 否 | 是 | 是 | N/A |

### 8.2 Yank 版本

**定义**：YANK 是"撤回已发布版本"的操作，用于将一个已发布的版本从可用状态移除。

**触发条件**：
- owner 或 namespace ADMIN/OWNER 对 PUBLISHED 状态的版本执行 yank

**执行效果**：
- `version.status` → `YANKED`（不可逆，无 un-yank 操作）
- `version.downloadReady` → `false`
- 记录 `yankedAt`、`yankedBy`、`yankReason`
- 如果该版本是 `skill.latestVersionId` 指向的版本：
  - 自动回退到上一个 PUBLISHED 版本
  - 如果没有其他 PUBLISHED 版本，`latestVersionId` → `null`

**对 AstronClaw 的影响**：
- 已安装实例不受影响
- 无法新装该版本
- 升级场景：目标版本被 yank → 升级失败

对接原则：
- 上述语义应由 SaaS Adapter 原样继承并稳定对外提供
- AstronClaw 通过 Adapter 感知这些状态，不直接绑定开源返回形态

**补救方式**：
- 不能 un-yank
- 只能发布新版本（rerelease 或重新上传）

---

## 9. 同名冲突规则

### 9.1 唯一性约束

数据库约束：`UNIQUE(namespace_id, slug, owner_id)`

含义：
- 同一 namespace 下，不同 owner 可以有相同 slug
- 同一 namespace 下，同一 owner 只能有一个相同 slug 的 skill

### 9.2 冲突规则设计原则

**核心原则**：只有 PUBLISHED 状态才会阻塞同名发布，但区分 visibility。

| 对方状态 | 我发布同名 PRIVATE | 我发布同名 PUBLIC | 说明 |
|---------|-------------------|------------------|------|
| UPLOADED | ✅ 允许 | ✅ 允许 | 多个 UPLOADED 可共存 |
| PENDING_REVIEW | ✅ 允许 | ✅ 允许 | 还未正式发布 |
| PRIVATE + PUBLISHED | ❌ 拒绝 | ❌ 拒绝 | 只允许一个正式私有版本 |
| PUBLIC + PUBLISHED | ❌ 拒绝 | ❌ 拒绝 | 市场已占用 |

### 9.3 冲突规则表（详细）

| 场景 | 是否允许 | 说明 |
|------|---------|------|
| 同 namespace，同 slug，同 owner | 允许（复用） | 新版本挂到已有 skill 下 |
| 同 namespace，同 slug，不同 owner，对方只有 UPLOADED | 允许 | 多个 UPLOADED 可共存测试 |
| 同 namespace，同 slug，不同 owner，对方只有 PENDING_REVIEW | 允许 | 还未正式发布 |
| 同 namespace，同 slug，不同 owner，对方有 PRIVATE + PUBLISHED | 拒绝 | 只允许一个正式私有版本 |
| 同 namespace，同 slug，不同 owner，对方有 PUBLIC/NAMESPACE_ONLY + PUBLISHED | 拒绝 | 市场已占用 |
| 不同 namespace，同 slug | 允许 | namespace 隔离 |

### 9.4 完整流程示例

```
用户 A 发布 PRIVATE `ns/my-skill`
    ↓
状态：UPLOADED
    ↓
用户 B 发布 PRIVATE `ns/my-skill`
    ↓
状态：UPLOADED ✅ 允许（多个 UPLOADED 可共存）
    ↓
用户 A 确认发布 → PRIVATE + PUBLISHED ✅ 允许
    ↓
用户 B 确认发布 → ❌ 被拒绝
    ↓
错误信息：error.skill.publish.nameConflict.private
    ↓
用户 B 可以：
  1. 改名发布
  2. 等用户 A 删除/归档后再发布
  3. 提交审核变成 PUBLIC（如果 A 是 PRIVATE）
```

### 9.5 代码改动

**文件**：`SkillPublishService.java`

```java
// 冲突检查逻辑（第 230-242 行）
for (Skill existing : existingSkills) {
    if (!existing.getOwnerId().equals(publisherId)) {
        // 检查是否有 PUBLISHED 版本
        boolean hasPublished = !skillVersionRepository
                .findBySkillIdAndStatus(existing.getId(), SkillVersionStatus.PUBLISHED)
                .isEmpty();
        
        if (hasPublished) {
            // PUBLISHED 版本存在，无论 visibility 如何都拒绝
            // 因为只允许一个 PRIVATE + PUBLISHED 或 PUBLIC + PUBLISHED
            if (existing.getVisibility() == SkillVisibility.PRIVATE) {
                throw new DomainBadRequestException("error.skill.publish.nameConflict.private", skillSlug);
            } else {
                throw new DomainBadRequestException("error.skill.publish.nameConflict", skillSlug);
            }
        }
    }
}
```

### 9.6 错误信息

| 错误码 | 说明 |
|-------|------|
| `error.skill.publish.nameConflict` | 已有同名 PUBLIC/NAMESPACE_ONLY skill 发布 |
| `error.skill.publish.nameConflict.private` | 已有同名 PRIVATE skill 正式发布 |

---

## 10. package_name / runtime 规则

### 10.1 当前实现

- `package_name` 不是 Core 的结构化字段
- 存储在 `skill_version.parsedMetadataJson` JSONB 字段中
- 由 skill 作者在 SKILL.md frontmatter 中定义

### 10.2 SaaS Adapter 职责

- 从 `parsedMetadataJson` 中提取 `package_name`
- 作为顶层字段返回给 AstronClaw
- 可选：检查跨 skill 的 package_name 唯一性
- 统一封装 `submit-review`、`confirm-publish`、删除、查询等 Core 能力，对 AstronClaw 暴露稳定接口

### 10.3 规则建议

| 规则 | 建议 |
|------|------|
| 格式 | 建议使用 `namespace__slug` 格式，避免冲突 |
| 跨版本稳定性 | 同一 skill 跨版本应保持 package_name 一致 |
| 唯一性 | SaaS Adapter 可检查并警告冲突，但不强制阻止 |

---

## 11. 代码改动清单

说明：

以下改动属于开源 `Core` 的规则实现，用于给 SaaS 封装层提供稳定能力基线；不等同于直接向 AstronClaw 暴露这些开源接口。

### 11.1 枚举新增

**文件**：`SkillVersionStatus.java`

```java
public enum SkillVersionStatus {
    DRAFT,
    SCANNING,
    SCAN_FAILED,
    UPLOADED,      // 新增
    PENDING_REVIEW,
    PUBLISHED,
    REJECTED,
    YANKED
}
```

### 11.2 发布逻辑改动

**文件**：`SkillPublishService.java`

```java
// 第 279-285 行，改为
if (visibility == SkillVisibility.PRIVATE) {
    version.setStatus(SkillVersionStatus.UPLOADED);
    version.setPublishedAt(currentTime());
    // 不创建审核任务
} else if (autoPublish) {
    version.setStatus(SkillVersionStatus.PUBLISHED);
    version.setPublishedAt(currentTime());
} else {
    version.setStatus(SkillVersionStatus.PENDING_REVIEW);
    // 创建审核任务
}
```

### 11.3 撤回审核改动

**文件**：`SkillGovernanceService.java`

```java
// withdrawPendingVersion 方法，改为
skillVersion.setStatus(SkillVersionStatus.UPLOADED);  // 原为 DRAFT
```

### 11.4 下载权限改动

**文件**：`SkillDownloadService.java`、`SkillQueryService.java`

```java
// UPLOADED 和 PENDING_REVIEW 状态允许 owner 下载
private boolean canDownload(SkillVersion version, Skill skill, String currentUserId) {
    return switch (version.getStatus()) {
        case UPLOADED, PENDING_REVIEW -> skill.getOwnerId().equals(currentUserId);
        case PUBLISHED -> true;  // 按 visibility 判断
        default -> false;
    };
}
```

### 11.5 新增服务

**文件**：`SkillReviewSubmitService.java`（新增）

- 实现 UPLOADED 版本提交审核逻辑

### 11.6 新增控制器

**文件**：`SkillReviewSubmitController.java`（新增）

- 暴露 `POST /{namespace}/{slug}/submit-review` 接口
- 暴露 `POST /{namespace}/{slug}/confirm-publish` 接口

### 11.7 管理员可见性

**文件**：`VisibilityChecker.java`

- SUPER_ADMIN 可以看到所有 skill，包括 UPLOADED 状态

### 11.8 数据库迁移

**文件**：新增迁移脚本

- 更新 `skill_version_status` 枚举类型，添加 UPLOADED 值

---

## 12. 阻塞上线条件

| 问题 | 严重程度 | 状态 |
|------|---------|------|
| 新增 UPLOADED 状态 | 高 | 已完成 |
| PRIVATE skill 发布逻辑改动 | 高 | 已完成 |
| 提交审核接口 | 高 | 已完成 |
| 撤回审核后进入 UPLOADED | 中 | 已完成 |
| 同名冲突检查补全 | 中 | 已完成 |
| 管理员可见 UPLOADED skill | 低 | 已完成 |
| package_name 唯一性检查 | 低 | 可选（SaaS Adapter 职责） |

---

## 13. 对老版本的影响

### 13.1 数据兼容性

| 影响点 | 分析 | 需要处理 |
|--------|------|---------|
| 老版本数据 | 不受影响，状态不变 | 否 |
| 数据库枚举 | 需添加 UPLOADED 值 | 是 |
| API 兼容性 | 新接口是新增，不影响老接口 | 否 |

### 13.2 状态流转影响

| 场景 | 老逻辑 | 新逻辑 | 影响 |
|------|--------|--------|------|
| 老版本撤回审核 | PENDING_REVIEW → DRAFT | PENDING_REVIEW → UPLOADED | 前端需适配新状态 |
| 老版本删除 | DRAFT/REJECTED/SCAN_FAILED 可删 | UPLOADED 也可删 | 需更新代码判断 |

### 13.3 代码改动点

**文件**：`SkillGovernanceService.java`

**1. 删除版本逻辑**（第163-166行）：
```java
// 原代码
if (version.getStatus() != SkillVersionStatus.DRAFT
        && version.getStatus() != SkillVersionStatus.REJECTED
        && version.getStatus() != SkillVersionStatus.SCAN_FAILED) {
    throw new DomainBadRequestException("error.skill.version.delete.unsupported", version.getVersion());
}

// 改为：允许删除 UPLOADED 状态
if (version.getStatus() != SkillVersionStatus.DRAFT
        && version.getStatus() != SkillVersionStatus.REJECTED
        && version.getStatus() != SkillVersionStatus.SCAN_FAILED
        && version.getStatus() != SkillVersionStatus.UPLOADED) {
    throw new DomainBadRequestException("error.skill.version.delete.unsupported", version.getVersion());
}
```

**2. 撤回审核逻辑**（第245行）：
```java
// 原代码
version.setStatus(SkillVersionStatus.DRAFT);

// 改为
version.setStatus(SkillVersionStatus.UPLOADED);
```

### 13.4 前端适配

| 状态 | 前端展示建议 |
|------|-------------|
| UPLOADED | "已上传" 或 "待确认" |
| 可删除状态 | DRAFT、SCAN_FAILED、REJECTED、UPLOADED |
| 可编辑状态 | DRAFT、SCAN_FAILED、REJECTED |

### 13.5 迁移策略

1. **数据库迁移**：添加 UPLOADED 枚举值
2. **代码部署**：先部署后端，再部署前端
3. **老数据处理**：无需处理，老版本状态保持不变
4. **回滚方案**：如需回滚，UPLOADED 状态的版本按 DRAFT 处理
