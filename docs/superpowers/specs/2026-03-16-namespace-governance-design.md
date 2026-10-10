# Namespace 治理补齐设计文档

> **Goal:** 在现有 namespace 基础能力上，补齐命名空间生命周期治理闭环。实现团队命名空间状态管理、管理台读模型拆分、前后端治理交互、跨模块状态约束、审计记录和错误语义统一。

> **前置条件:** Phase 2 命名空间模型、成员管理、Skill 核心链路已完成；Phase 3 审核与提升流程已接入 namespace 角色体系。

> **重要约束：系统内置全局空间**
> `@global` 是系统内置命名空间，不允许任何业务接口修改其基础信息、成员、状态或所有权。它只允许读取。

## 关键设计决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 治理模式 | 生命周期收敛型 | 一次性统一状态机、权限矩阵、页面行为和跨模块约束，避免零散补丁 |
| 全局空间策略 | `@global` 内置只读 | 与产品定位一致，避免把全局公共空间误当作普通团队空间治理 |
| 团队空间状态机 | `ACTIVE / FROZEN / ARCHIVED` | 已在领域模型中定义，补齐接口和行为即可 |
| 恢复语义 | `ARCHIVED -> ACTIVE` | 软归档恢复后直接回归正常运营态，避免多余状态分支 |
| 服务边界 | `NamespaceGovernanceService` 独立承载状态流转 | 避免 `NamespaceService` 混杂 CRUD、成员和生命周期逻辑 |
| 管理读模型 | 新增 `/me/namespaces` | 区分公开目录和管理台视图，支持返回冻结/归档空间 |
| 归档权限 | 团队空间仅 `OWNER` 可归档/恢复 | 归档是高风险操作，需要明确责任人 |
| 冻结权限 | 团队空间 `OWNER/ADMIN` 可冻结/解冻 | 保留日常治理能力，同时不扩大归档权限 |
| 错误暴露策略 | 归档空间对非成员公开访问按不可见处理 | 符合软归档“对外隐藏”语义 |

## Tech Stack（沿用现有实现）

- Backend: Spring Boot 3.x + JDK 21 + Spring Data JPA + Spring Security
- Frontend: React 19 + TypeScript + TanStack Query + TanStack Router
- Governance/Audit: 复用 `AuditLogService`

---

## 1. 背景与问题

现有设计与实现已经具备 namespace 的基础模型、成员角色和审核边界，但仍存在以下缺口：

1. 缺少 namespace 状态管理接口，`FROZEN / ARCHIVED` 仅停留在领域枚举层
2. 公开空间列表与“我的命名空间”复用同一查询接口，无法呈现管理态空间
3. 发布、审核、提升等写操作尚未统一受 namespace 状态约束
4. 前端成员管理和治理交互处于禁用或缺失状态
5. `@global` 的“内置只读”定位尚未在业务接口层被系统化约束

本设计目标是把 namespace 从“基础协作对象”提升为“完整治理对象”。

## 2. 目标与非目标

### 2.1 目标

- 补齐团队命名空间状态管理：冻结、解冻、归档、恢复
- 明确 `@global` 为不可变系统空间
- 拆分公开读模型和管理台读模型
- 统一 namespace 状态对发布、审核、提升、公开可见性的影响
- 补齐管理台页面交互与状态提示
- 为状态变更增加审计记录和稳定错误语义

### 2.2 非目标

- 不新增“删除命名空间”能力
- 不重构 skill 生命周期模型
- 不引入新的平台后台审批流
- 不改变现有 namespace 基础数据结构

## 3. 生命周期模型

### 3.1 命名空间类型边界

#### GLOBAL

- 代表系统内置公共空间（`@global`）
- 只允许读取
- 不允许更新基础信息
- 不允许成员增删改
- 不允许冻结、解冻、归档、恢复
- 不允许转让所有权

#### TEAM

- 普通团队协作空间
- 支持完整生命周期治理

### 3.2 状态机

仅 `TEAM` 类型可发生以下流转：

```text
ACTIVE -> FROZEN
FROZEN -> ACTIVE
ACTIVE -> ARCHIVED
FROZEN -> ARCHIVED
ARCHIVED -> ACTIVE
```

不支持以下流转：

- `ARCHIVED -> FROZEN`
- 任意对 `GLOBAL` 类型的状态变更

### 3.3 状态语义

#### ACTIVE

- 公开可见
- 成员可管理
- 可发布、可审核、可提升

#### FROZEN

- 只读态
- 公开内容仍可浏览和下载
- 成员仍可查看空间详情、成员列表、审核列表
- 禁止发布新版本
- 禁止审核操作
- 禁止发起提升
- 禁止编辑命名空间信息
- 禁止成员增删改
- 禁止所有权转移

#### ARCHIVED

- 软归档
- 公开列表、公开搜索、公开详情默认隐藏
- 普通用户不可下载
- 命名空间成员仍可在管理台看到该空间
- 除恢复外，禁止所有写操作
- 恢复后回到 `ACTIVE`

## 4. 权限矩阵

### 4.1 团队空间角色权限

| 操作 | OWNER | ADMIN | MEMBER |
|------|-------|-------|--------|
| 编辑空间基础信息 | `ACTIVE` 可 | `ACTIVE` 可 | 不可 |
| 添加/移除成员 | `ACTIVE` 可 | `ACTIVE` 可 | 不可 |
| 修改成员角色 | `ACTIVE` 可 | `ACTIVE` 可 | 不可 |
| 转让所有权 | `ACTIVE` 可 | 不可 | 不可 |
| 冻结 | 可 | 可 | 不可 |
| 解冻 | 可 | 可 | 不可 |
| 归档 | 可 | 不可 | 不可 |
| 恢复 | 可 | 不可 | 不可 |

### 4.2 全局空间权限

`@global` 不接受任何业务写操作。无论调用者拥有哪些平台角色或 namespace 角色，都返回“系统内置命名空间不可修改”错误。

## 5. 后端架构设计

### 5.1 服务拆分

建议新增 `NamespaceGovernanceService`，负责所有 namespace 生命周期变更：

- `freezeNamespace`
- `unfreezeNamespace`
- `archiveNamespace`
- `restoreNamespace`

现有服务职责调整如下：

- `NamespaceService`
  - 创建命名空间
  - 查询 namespace
  - 更新基础信息
  - 只保留基础管理员校验
- `NamespaceMemberService`
  - 成员增删改
  - 所有权转移
- `NamespaceGovernanceService`
  - 生命周期状态流转
  - `@global` 只读校验
  - 状态合法性校验
  - 审计记录

建议补充 `NamespaceAccessPolicy` 或同级帮助类，集中回答以下问题：

- 当前 namespace 是否允许编辑
- 是否允许成员管理
- 是否允许发布
- 是否允许审核
- 是否允许提升
- 是否允许公开访问

### 5.2 控制器设计

现有 [`NamespaceController`](/Users/yunzhi/Documents/skillhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/NamespaceController.java) 增加以下端点：

```text
GET  /api/v1/me/namespaces
POST /api/v1/namespaces/{slug}/freeze
POST /api/v1/namespaces/{slug}/unfreeze
POST /api/v1/namespaces/{slug}/archive
POST /api/v1/namespaces/{slug}/restore
```

Web 别名同步开放在 `/api/web/...`。

### 5.3 公开视图与管理视图拆分

#### 公开视图

- `GET /api/v1/namespaces`
  - 仅返回 `ACTIVE` namespace
- `GET /api/v1/namespaces/{slug}`
  - 匿名或普通公开访问仅可读取 `ACTIVE`
  - `ARCHIVED` 对非成员按不可见处理

#### 管理视图

- `GET /api/v1/me/namespaces`
  - 返回当前用户所属 namespace
  - 包含 `ACTIVE / FROZEN / ARCHIVED`
  - 用于“我的命名空间”页面

这是本次设计的关键修正：当前前端“我的命名空间”错误复用了公开 `/namespaces`，必须改为管理视图接口。

## 6. 跨模块业务约束

### 6.1 发布链路

在 [`SkillPublishService`](/Users/yunzhi/Documents/skillhub/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java) 中增加 namespace 状态校验：

- `FROZEN`：拒绝发布新版本
- `ARCHIVED`：拒绝发布新版本

错误语义建议区分：

- `namespace.frozen`
- `namespace.archived`

### 6.2 审核链路

审核相关写操作在 namespace 非 `ACTIVE` 时全部拒绝：

- 提交审核
- 审核通过
- 审核拒绝
- 撤回提审后再次提审

审核列表是否可读：

- `FROZEN`：可读，不可写
- `ARCHIVED`：成员可读，不可写

### 6.3 提升链路

`PromotionController` 发起提升时增加 namespace 状态校验：

- `FROZEN`：拒绝发起
- `ARCHIVED`：拒绝发起

### 6.4 公开可见性

#### namespace 层

- 公开列表只显示 `ACTIVE`
- 归档空间不进入公开目录

#### skill 层

- 若所属 namespace 为 `ARCHIVED`，公开搜索和公开详情页不再暴露该 skill
- 若所属 namespace 为 `FROZEN`，skill 仍可公开浏览和下载

## 7. 前端交互设计

涉及页面：

- [`web/src/pages/dashboard/my-namespaces.tsx`](/Users/yunzhi/Documents/skillhub/web/src/pages/dashboard/my-namespaces.tsx)
- [`web/src/pages/dashboard/namespace-members.tsx`](/Users/yunzhi/Documents/skillhub/web/src/pages/dashboard/namespace-members.tsx)
- [`web/src/pages/dashboard/namespace-reviews.tsx`](/Users/yunzhi/Documents/skillhub/web/src/pages/dashboard/namespace-reviews.tsx)
- [`web/src/features/namespace/namespace-header.tsx`](/Users/yunzhi/Documents/skillhub/web/src/features/namespace/namespace-header.tsx)

### 7.1 我的命名空间

- 数据源切换为 `GET /api/web/me/namespaces`
- 卡片展示 status badge
- 团队空间显示治理操作入口
- `@global` 显示“系统内置，只读”提示

按钮可见性：

- `OWNER`
  - `ACTIVE`: 冻结、归档
  - `FROZEN`: 解冻、归档
  - `ARCHIVED`: 恢复
- `ADMIN`
  - `ACTIVE`: 冻结
  - `FROZEN`: 解冻
  - `ARCHIVED`: 无治理按钮
- `MEMBER`
  - 无治理按钮

### 7.2 成员管理页

- `ACTIVE`：允许添加成员、改角色、移除成员
- `FROZEN / ARCHIVED`：列表仍可读，但操作按钮禁用
- 页面顶部展示只读状态说明

### 7.3 审核页

- `ACTIVE`：正常审核
- `FROZEN / ARCHIVED`：列表可读，审核按钮禁用
- 页面顶部展示“当前命名空间不可处理审核任务”

### 7.4 命名空间头部

[`NamespaceResponse`](/Users/yunzhi/Documents/skillhub/server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/NamespaceResponse.java) 已包含 `status`，前端只需新增状态 badge 和说明文案，无需调整响应结构。

## 8. 审计与错误语义

### 8.1 审计动作

复用 `AuditLogService`，新增以下 action：

- `FREEZE_NAMESPACE`
- `UNFREEZE_NAMESPACE`
- `ARCHIVE_NAMESPACE`
- `RESTORE_NAMESPACE`

审计对象：

- resourceType: `NAMESPACE`
- resourceId: namespace.id

建议 detail 中记录：

- `slug`
- `fromStatus`
- `toStatus`
- `reason`（可选）

### 8.2 错误语义

建议统一以下错误类别：

- `error.namespace.system.immutable`
  - 对 `@global` 发起任意写操作
- `error.namespace.state.transition.invalid`
  - 非法状态流转
- `error.namespace.frozen`
  - 冻结态下执行写操作
- `error.namespace.archived`
  - 归档态下执行写操作或公开访问受限资源

公开访问归档空间时，对非成员优先按“不可见”处理，而不是显式暴露“已归档”。

## 9. 数据与接口兼容性

### 9.1 数据层

- 现有 `namespace.status` 字段已存在，无需迁移
- 现有 `NamespaceResponse` 已带 `status` 字段，无需扩展 DTO

### 9.2 接口层

- 保留现有公开 `/namespaces`
- 新增 `/me/namespaces` 供管理台使用
- 现有前端查询需要切换，避免继续把公开目录误用为我的空间

### 9.3 行为层

- `ARCHIVED` namespace 下的 skill 公开入口行为会收紧
- 管理台会首次出现冻结/归档空间

## 10. 测试策略

### 10.1 后端单元测试

- `NamespaceGovernanceServiceTest`
  - 冻结/解冻/归档/恢复合法流转
  - `@global` 不可变
  - `OWNER/ADMIN/MEMBER` 权限矩阵
- `NamespaceServiceTest`
  - 冻结/归档状态下禁止基础信息更新
- `NamespaceMemberServiceTest`
  - 冻结/归档状态下禁止成员管理和所有权转移
- `SkillPublishServiceTest`
  - `FROZEN / ARCHIVED` namespace 下发布失败
- 审核/提升相关服务测试
  - 非 `ACTIVE` namespace 下写操作失败

### 10.2 控制器测试

- `NamespaceControllerTest`
  - `GET /me/namespaces`
  - `POST /freeze`
  - `POST /unfreeze`
  - `POST /archive`
  - `POST /restore`
- 公开接口测试
  - 归档空间对匿名用户不可见

### 10.3 前端测试

- 我的命名空间状态 badge 与治理按钮可见性
- 成员页只读态
- 审核页只读态
- `@global` 无治理入口

## 11. 实施顺序建议

1. 后端生命周期服务与权限矩阵
2. 跨模块状态拦截（发布、审核、提升、公开可见性）
3. `GET /me/namespaces` 管理视图接口
4. 前端管理台接入与状态交互
5. 审计与文档补齐

## 12. 风险与取舍

### 风险

- 若只改 namespace 接口、不改 skill/search/review 约束，会产生状态语义不一致
- 若继续复用公开 `/namespaces` 作为管理台数据源，冻结/归档空间无法被恢复

### 取舍

- 本次不增加删除能力，避免把“归档”和“删除”混淆
- 恢复统一回到 `ACTIVE`，不保留“恢复到冻结”的复杂分支
- `@global` 完全只读，避免未来平台和团队混用治理规则
