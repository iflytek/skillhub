# Skill Lifecycle

Date: 2026-03-18
Status: current code-aligned reference

本文件是 skill 生命周期的单一规范入口。结论以当前代码实现为准，并已经同步到领域模型、业务流程、API、前端、搜索和兼容层文档。

## 1. 设计原则

- skill 生命周期不再被建模为一个混杂状态机，而是拆分为容器状态、版本状态、审核工作流状态和可见性覆盖层
- 前端不再从 `status + hidden + latestVersionStatus + viewingVersionStatus` 拼装状态，而统一消费后端 lifecycle projection
- destructive action 和 reversible action 必须分离；`withdraw-review` 只表示撤回提审，不表示删除版本
- 对外仍可保留 `latest` 协议词汇，但内部语义必须严格等价于 latest published

## 2. 状态模型

### 2.1 Skill 容器状态

- `ACTIVE`
- `ARCHIVED`

说明：

- `hidden` 是独立治理覆盖层，不属于 `Skill.status`
- `SkillStatus.HIDDEN` 不再视为有效生命周期语义

### 2.2 SkillVersion 版本状态

- `DRAFT`
- `PENDING_REVIEW`
- `PUBLISHED`
- `REJECTED`
- `YANKED`

状态含义：

- `DRAFT`：可再次提交审核或删除的非公开版本
- `PENDING_REVIEW`：冻结待审版本
- `PUBLISHED`：当前可分发版本
- `REJECTED`：审核拒绝后保留的版本
- `YANKED`：曾发布、现已撤回分发的版本

### 2.3 ReviewTask 审核工作流状态

- `PENDING`
- `APPROVED`
- `REJECTED`

`ReviewTask` 仅表达审核流程，不再被前端当作展示态来源。

## 3. 核心语义

### 3.1 Latest

- `Skill.latestVersionId` 的唯一语义是 latest published pointer
- 它只能指向 `PUBLISHED` 版本
- 若 skill 没有任何已发布版本，则允许为 `null`
- `latest` 系统保留标签自动跟随该指针

### 3.2 Lifecycle Projection

详情页、我的技能、我的收藏、搜索等读模型统一基于以下 projection：

- `headlineVersion`：当前页面主展示版本
- `publishedVersion`：最新已发布版本
- `ownerPreviewVersion`：owner / namespace 管理者可见的待审核预览版本
- `resolutionMode`：`PUBLISHED` / `OWNER_PREVIEW` / `NONE`

约束：

- 公开浏览、安装、下载、搜索只认 `publishedVersion`
- owner 详情页只有在不存在 `publishedVersion` 时，才允许 `headlineVersion = ownerPreviewVersion`
- promotion、compat latest、默认下载等公开分发行为都只能绑定 `publishedVersion`

## 4. 代码实际链路

### 4.1 首次上传

- 普通用户上传后直接创建 `PENDING_REVIEW` 版本
- 同时创建 `PENDING` review task
- 不会创建初始 `DRAFT`
- 不会更新 `latestVersionId`

### 4.2 审核通过

- `PENDING_REVIEW -> PUBLISHED`
- review task 标记为 `APPROVED`
- `Skill.latestVersionId` 指向该版本
- skill 展示元数据从发布版本刷新

### 4.3 审核拒绝

- `PENDING_REVIEW -> REJECTED`
- review task 标记为 `REJECTED`
- 版本保留，可后续删除

### 4.4 撤回审核

- `withdraw-review` 的统一语义是 `PENDING_REVIEW -> DRAFT`
- 同时删除关联的 `PENDING review_task`
- 该操作是可逆、非破坏性的
- 当前代码只允许提交人本人撤回

### 4.5 重传新版本

- 若发现旧的 `PENDING_REVIEW` 版本，会先把旧版本自动降回 `DRAFT`
- 然后创建新的待审版本
- 自动撤回与手动撤回必须保持同一语义

### 4.6 已发布版本重发

- rerelease 当前本质上是从已发布版本复制并重新走发布流程
- 当前实现允许特权路径直接产出新 `PUBLISHED` 版本
- 该能力应被理解为发布路径特例，不是生命周期展示态

### 4.7 隐藏 / 恢复 / 归档 / 撤回已发布版本

- 隐藏：只改 `hidden=true`
- 恢复：只改 `hidden=false`
- 归档：`Skill.status = ARCHIVED`
- 取消归档：`Skill.status = ACTIVE`
- yank：`PUBLISHED -> YANKED`

### 4.8 Yank 后指针修正

- yank 已发布版本时，若命中当前 `latestVersionId`，必须重算 latest published pointer
- 若仍有其他 `PUBLISHED` 版本，则指向最新一个
- 若已无任何 `PUBLISHED` 版本，则 `latestVersionId = null`

## 5. 对外协议约束

### 5.1 Public / Search / Compat

- 对外协议可以继续暴露 `latestVersion`、`latest`、默认下载等概念
- 但它们都必须严格表示“最新已发布版本”
- compat 层内部实现必须从统一 lifecycle projection 的 `publishedVersion` 映射，不允许自行推导“当前版本”

### 5.2 Frontend

- 页面状态展示统一消费 projection
- 不再新增旧兼容字段依赖
- `hidden` 仅作为治理标记展示，不参与版本状态拼装

## 6. 权限边界

- `withdraw-review`：仅提交人本人
- 删除版本：owner 或 namespace 管理者，且仅限 `DRAFT` / `REJECTED`
- 归档 / 取消归档：owner 或 namespace 管理者
- 隐藏 / 恢复技能、撤回已发布版本：平台技能治理权限

## 7. 当前最终约束

- 一个 skill 生命周期的唯一规范入口就是本文件
- 其它文档如 `02-domain-model`、`05-business-flows`、`06-api-design`、`08-frontend-architecture` 必须与本文件保持一致
- 若后续代码再次改变生命周期语义，应先修改代码，再同步更新本文件和相关子文档
