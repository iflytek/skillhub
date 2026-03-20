# Skill Label System Design

> Date: 2026-03-20
> Status: Draft
> Scope: Phase 1 — 系统推荐标签 + 特权标签

## 1. Overview

为 SkillHub 引入 label 系统，为 skill 提供分类和标记能力。Label 挂在 skill 级别（与版本无关），支持多语言展示和搜索。

注意：本系统使用 "label" 而非 "tag"，因为 `skill_tag` 已被版本分发通道功能占用。

### 1.1 一期范围

**包含：**
- 系统推荐标签（RECOMMENDED）：管理员 CRUD + 多语言翻译 + 排序，用于搜索页分类筛选
- 特权标签（PRIVILEGED）：管理员专属赋予，如"官方推荐"、"官方认证"、"从Clawhub镜像"
- Skill 详情页 label 展示与管理
- 搜索页分类板块（单选互斥筛选）
- 多语言搜索命中（所有语言翻译写入搜索文档）

**不包含（保留兼容性）：**
- 用户自定义标签
- 用户自定义标签审核流程

### 1.2 关键决策

- `label_*` 是全新模型，与现有 `skill_tag` 彻底隔离；`skill_tag` 继续只承担“版本分发别名”的职责，不复用表、Service、Controller、DTO、API 路径
- `skill_search_document.keywords` 是搜索文档的共享聚合字段，不是 label 专属字段；一期在搜索文档重建时，将“现有业务 keywords 来源”和“label 翻译文本”作为两个独立来源重新组合写入
- label 搜索集成只允许“基于权威源全量重建单个 skill 的搜索文档”，不允许读取现有 `skill_search_document.keywords` 后做增量 append
- promotion 在当前系统中会创建新的 target skill，而不是把 source skill 移动到新空间；因此 label 生命周期必须按“source skill / target skill 两条独立 skill 记录”建模

## 2. Data Model

注意：新表统一使用 `TIMESTAMPTZ` 作为时间戳类型标准（现有旧表使用 `TIMESTAMP`，后续统一迁移）。

### 2.1 label_definition（标签定义表）

```sql
CREATE TABLE label_definition (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(64) UNIQUE NOT NULL,   -- 英文标识，必填，如 code-generation
    type        VARCHAR(16) NOT NULL CHECK (type IN ('RECOMMENDED', 'PRIVILEGED')),
    visible_in_filter BOOLEAN NOT NULL DEFAULT true, -- 是否在搜索页分类板块展示
    sort_order  INTEGER NOT NULL DEFAULT 0,    -- 分类板块显示顺序
    created_by  VARCHAR(128) REFERENCES user_account(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

- `slug` 即英文名称，作为语言无关的唯一标识
- `type` 区分系统推荐标签和特权标签，决定权限控制策略。DDL 层通过 CHECK 约束限制合法值；应用层权限校验对未知 type 采用 deny-by-default 策略
- `visible_in_filter` 控制是否出现在搜索页分类板块，RECOMMENDED 和 PRIVILEGED 均可配置

### 2.2 label_translation（标签翻译表）

```sql
CREATE TABLE label_translation (
    id          BIGSERIAL PRIMARY KEY,
    label_id    BIGINT NOT NULL REFERENCES label_definition(id) ON DELETE CASCADE,
    locale      VARCHAR(16) NOT NULL,          -- 语言代码，如 en、zh、ja
    display_name VARCHAR(128) NOT NULL,        -- 该语言的显示名称
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(label_id, locale)
);
```

- 支持动态语言：管理员可为任意语言添加翻译，不限于系统当前支持的语言列表
- 前端展示 fallback 顺序：当前语言 → en → slug
- 后端返回 `displayName` 时，“当前语言”以请求 locale 为准；实现上使用 Spring locale 解析结果（等价于基于 `Accept-Language` / request locale），再 fallback 到 `en` 和 `slug`

### 2.3 skill_label（skill 与 label 关联表）

```sql
CREATE TABLE skill_label (
    id          BIGSERIAL PRIMARY KEY,
    skill_id    BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    label_id    BIGINT NOT NULL REFERENCES label_definition(id) ON DELETE CASCADE,
    created_by  VARCHAR(128) REFERENCES user_account(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(skill_id, label_id)
);

CREATE INDEX idx_skill_label_label_id ON skill_label(label_id);
```

- Label 挂在 skill 级别，与版本无关
- 级联删除：删除 label_definition 时自动清理关联
- `(label_id)` 索引用于分类筛选时按 label 查找关联 skill 的性能优化
- 单个 skill 最多关联 10 个 label（应用层校验）

### 2.5 与现有 `skill_tag` 的关系

现有 `skill_tag` 已用于版本分发通道，例如 `latest`、`beta` 等 tag 指向某个发布版本。它具备以下特征：
- 语义是“版本别名”，不是 skill 分类
- `version_id` 必填，tag 必须解析到某个 skill version
- API 和前端心智都已围绕“安装/下载某个版本别名”展开

因此：
- 新 label 系统不得复用 `skill_tag` 表结构
- 新 label 系统不得复用 `/tags` 相关 API 路径
- 代码实现中必须使用独立的命名：`LabelDefinition` / `SkillLabel` / `LabelTranslation`

### 2.4 兼容性设计：用户自定义标签

未来用户自定义标签可通过以下方式扩展，无需新建表：

1. `label_definition.type` 增加 `USER_DEFINED` 枚举值
2. `label_definition` 增加字段：
   - `status VARCHAR(16)` — `PENDING_REVIEW` / `APPROVED` / `REJECTED`，用于审核流程
   - `submitted_by VARCHAR(128)` — 提交人
3. `label_translation` 对于用户自定义标签只需存储用户输入的原始语言，无需多语言翻译
4. 搜索行为：用户自定义标签以原始文本写入搜索文档 keywords，只能搜索用户输入的语言

这种设计保证了：
- 现有表结构无需破坏性变更
- 权限模型自然扩展（`USER_DEFINED` 类型有独立的权限规则）
- 搜索集成方式一致（均通过 keywords 字段）

## 3. Permission Model

| 操作 | 对象 | 超级管理员 | 命名空间管理员 | Skill Owner | 普通用户 |
|------|------|:---:|:---:|:---:|:---:|
| CRUD 标签定义 | label_definition | ✅ | ❌ | ❌ | ❌ |
| 管理翻译 | label_translation | ✅ | ❌ | ❌ | ❌ |
| 赋予/移除 RECOMMENDED label | skill_label | ✅ | ✅（本空间，仅限搜索页可见的 RECOMMENDED 标签） | ✅（自己的 skill，仅限搜索页可见的 RECOMMENDED 标签） | ❌ |
| 赋予/移除 PRIVILEGED label | skill_label | ✅ | ❌ | ❌ | ❌ |
| 查看 label | 所有表 | ✅ | ✅ | ✅ | ✅（受 skill 可见性约束） |

- 标签定义和翻译的管理是全局操作，仅超级管理员
- 赋予 label 到 skill 的权限取决于 label 的 type
- 查看权限跟随 skill 本身的可见性规则，不额外控制
- 实现上必须抽出统一的 `LabelPermissionChecker`；`SUPER_ADMIN` 始终可绕过 namespace membership 直接执行 label 管理操作，避免 controller / service 各自复制权限逻辑

### 3.1 跨空间权限边界

- 命名空间管理员只能管理其所管理空间内 skill 的 label

### 3.2 Promotion 后的 label 生命周期

当前 promotion 的事实模型是“审批后在目标全局空间创建一个新的 target skill”，而不是把 source skill 迁移到全局空间。

一期采用以下规则：
- promotion 不自动复制 source skill 的任何 label 到 target skill
- source skill 和 target skill 各自维护独立的 `skill_label`
- source 空间管理员对 source skill 的 label 权限不变
- target skill 的 label 由 target skill 当前权限模型控制；source 空间管理员不会因 source skill 的管理权限而自动获得 target skill 的 label 管理权

这样做的原因：
- 避免在一期引入“promotion 时 label 复制/回写/同步”的额外复杂度
- 与当前 promotion “创建新 skill 副本”的领域模型一致
- 后续如需复制策略，可在 promotion approval 流程中显式扩展，而不破坏现有表结构

## 4. Search Integration

采用翻译文本展开写入搜索文档方案。

### 4.1 搜索架构现状

当前搜索基于 PostgreSQL Full-Text Search：
- `skill_search_document` 表有 `search_vector` 列，类型为 `tsvector GENERATED ALWAYS AS ... STORED`
- 权重体系：title (A) > summary/keywords (B) > search_text (C)
- `search_vector` 在 keywords 等字段更新时自动重新生成，无需手动维护
- 查询时通过 `d.search_vector @@ to_tsquery('simple', :tsQuery)` 进行全文匹配

### 4.2 Keywords 字段写入

在构建 `SkillSearchDocument` 时，将 skill 关联的所有 label 的所有语言翻译文本写入 `keywords` 字段。

**重要：** `skill_search_document.keywords` 不是 label 专属字段，而是搜索文档的共享聚合字段。当前系统中，该字段已经承载来自 skill metadata/frontmatter 的 keywords/tag 信息。label 翻译文本只是新增来源之一，不能覆盖或破坏现有来源。

实现要求：
- 搜索文档重建时，从权威源重新计算完整的 `keywords`
- 现有业务 keywords 来源与 label 翻译文本作为两个独立来源进行组合
- 不允许读取旧的 `skill_search_document.keywords` 后做增量 append
- label 删除、翻译修改、skill 移除 label 后，旧 label 文本必须通过重建被彻底清理，不得残留

建议实现上的组合顺序：
1. 保留现有搜索重建逻辑产出的原有 keywords 内容
2. 追加该 skill 关联 label 的全部翻译文本
3. 最终统一写回新的 `SkillSearchDocument.keywords`

示例：
```
[原有 keywords 内容] Code Generation 代码生成 Official 官方推荐
```

### 4.3 搜索文档重建触发时机

| 事件 | 影响范围 | 处理方式 |
|------|---------|---------|
| Skill 被赋予/移除 label | 单个 skill | 同步重建该 skill 搜索文档 |
| label_translation 被修改 | 所有关联该 label 的 skill | 异步批量重建 |
| label_definition 被删除 | 所有关联该 label 的 skill | 异步批量重建 |

#### 异步批量重建方案

- 使用 Spring `@Async` 执行异步任务
- 在 `skillhub-app` 层的 application service 中实现 label 相关的搜索同步入口（不放在 `skillhub-search` 模块的 `SearchRebuildService` 中，避免搜索模块对 `skill_label` 表的直接依赖，保持模块边界清晰）
- 对 `label_translation` 修改、`label_definition` 删除等“影响多个 skill”的变更，在事务内先收集受影响的 `skill_id` 列表，再在 `AFTER_COMMIT` 阶段触发异步任务
- `label_definition` 删除场景严禁在删除后再通过 `skill_label` 反查，因为 `skill_label` 已被级联删除；必须在删除前快照受影响的 `skill_id`
- 异步任务分批调用 `SearchRebuildService.rebuildBySkill(Long)`
- 批量大小：每批 50 个 skill，批次间无需间隔（数据库写入压力可控，系统推荐标签数量有限）
- 失败隔离策略：批量重建循环中必须对每个 skill 单独 `try/catch` 并记录日志，保证单个 skill 失败不影响后续 skill
- 错误处理：单个 skill 重建失败记录错误日志，不自动重试（下次 label 变更或手动 rebuildAll 时会修复）
- 如单次受影响 skill 数过多，应允许后台人工触发搜索全量重建作为兜底手段
- 对“热门 label 导致大量 skill 批量重建”的场景，一期不单独引入任务表；优先依赖现有异步线程池执行，小规模批量直接处理，超大批量由后台人工触发 `rebuildAll` 兜底

### 4.4 分类筛选

搜索页分类板块的筛选不走全文搜索，而是通过 `skill_label` JOIN `label_definition` 按 slug 做大小写不敏感过滤，再与搜索结果取交集。避免全文搜索的模糊性问题。

当前搜索入口为：
```
GET /api/web/skills?q=xxx
```

一期在现有入口上增加可选 query parameter `label`，支持多值以预留未来组合筛选能力（一期前端只做单选）：
```
GET /api/web/skills?q=xxx&label=code-generation
GET /api/web/skills?q=xxx&label=code-generation&label=official  (未来)
```

#### SearchQuery 改动

`SearchQuery` 需要新增 `labelSlugs` 字段：
```java
public record SearchQuery(
    String keyword,
    Long namespaceId,
    SearchVisibilityScope visibilityScope,
    String sortBy,
    int page,
    int size,
    List<String> labelSlugs
) {}
```

注意：
- 这不是“零成本追加字段”；当前 controller、application service、query service、测试代码都需要同步修改
- 实现时应显式梳理以下变更点：HTTP 参数解析、`SkillSearchAppService` 参数透传、`PostgresFullTextQueryService` SQL 条件、相关单元测试/控制器测试
- 若后续搜索过滤条件继续增加，应考虑把 `SearchQuery` 从位置参数 record 演进为更可扩展的请求对象

`PostgresFullTextQueryService` 的 SQL 拼接逻辑中，当 `labelSlugs` 非空时追加：
```sql
AND d.skill_id IN (
    SELECT sl.skill_id FROM skill_label sl
    JOIN label_definition ld ON ld.id = sl.label_id
    WHERE ld.slug IN (:labelSlugs)
)
```

count 查询同步追加相同条件。语义重排在 label 过滤后的候选集上执行，无需额外处理。

当前多 label 筛选采用 OR 语义（匹配任一 label 即命中）。未来如需 AND 语义（同时具有所有 label），可通过 `GROUP BY skill_id HAVING COUNT(*) = :labelCount` 扩展，API 层增加 `labelMode=any|all` 参数区分。

### 4.5 tsvector 权重

不改变现有权重体系。`search_vector` 是 `GENERATED ALWAYS AS ... STORED` 列，keywords 字段更新后自动重新生成，无需手动维护：
- A 权重：title (displayName)
- B 权重：summary / keywords（含 label 翻译文本）
- C 权重：searchText

## 5. API Design

### 5.1 管理后台 API（超级管理员）

所有响应遵循项目统一响应规范 `{ code, msg, data, timestamp, requestId }`。

#### 列出所有标签定义
```
GET /api/v1/admin/labels
```
Response `data`:
```json
[
  {
    "slug": "code-generation",
    "type": "RECOMMENDED",
    "visibleInFilter": true,
    "sortOrder": 10,
    "translations": [
      { "locale": "en", "displayName": "Code Generation" },
      { "locale": "zh", "displayName": "代码生成" }
    ],
    "createdAt": "2026-03-20T10:00:00Z"
  }
]
```
不分页，系统标签数量有限（建议上限 100 个 label_definition）。

#### 创建标签定义
```
POST /api/v1/admin/labels
```
```json
{
  "slug": "code-generation",
  "type": "RECOMMENDED",
  "visibleInFilter": true,
  "sortOrder": 10,
  "translations": [
    { "locale": "en", "displayName": "Code Generation" },
    { "locale": "zh", "displayName": "代码生成" }
  ]
}
```

#### 更新标签定义
```
PUT /api/v1/admin/labels/{slug}
```
Body 不包含 slug 字段（slug 不可修改，以 path 参数为准）：
```json
{
  "type": "RECOMMENDED",
  "visibleInFilter": true,
  "sortOrder": 10,
  "translations": [
    { "locale": "en", "displayName": "Code Generation" },
    { "locale": "zh", "displayName": "代码生成" }
  ]
}
```
translations 采用全量替换策略：请求中的 translations 列表完全替代现有翻译。如果删除了某个语言的翻译，会触发关联 skill 的异步搜索文档重建。

#### 删除标签定义
```
DELETE /api/v1/admin/labels/{slug}
```
硬删除。级联删除关联的 translations 和 skill_label 记录，触发异步搜索文档重建。删除操作会记录到 audit_log。

#### 批量更新排序
```
PUT /api/v1/admin/labels/sort-order
```
```json
{
  "items": [
    { "slug": "code-generation", "sortOrder": 1 },
    { "slug": "official", "sortOrder": 2 }
  ]
}
```

### 5.2 Skill Label 管理 API

路由约定：
- 为与现有 skill read 接口风格保持一致，skill 详情读取类 label API 采用双路由暴露：`/api/v1/...` 与 `/api/web/...`
- 管理后台 label definition API 继续只暴露在 `/api/v1/admin/...`
- 搜索页所需的公开 labels 列表 API 一期同时暴露 `/api/v1/labels` 与 `/api/web/labels`，前端默认使用 `/api/web/labels`

#### 获取 skill 的所有 label
```
GET /api/v1/skills/{namespace}/{slug}/labels
GET /api/web/skills/{namespace}/{slug}/labels
```
Response `data`:
```json
[
  {
    "slug": "code-generation",
    "type": "RECOMMENDED",
    "displayName": "代码生成"
  },
  {
    "slug": "official",
    "type": "PRIVILEGED",
    "displayName": "官方推荐"
  }
]
```
`displayName` 根据请求语言返回，fallback 顺序：当前语言 → en → slug。

DTO 约定：
- 一期统一返回 `slug`、`type`、`displayName`
- 如后续需要区分视觉样式，可在前端基于 `type` 判断

#### 赋予 label
```
PUT /api/v1/skills/{namespace}/{slug}/labels/{labelSlug}
PUT /api/web/skills/{namespace}/{slug}/labels/{labelSlug}
```
权限校验：RECOMMENDED → owner / 命名空间管理员 / 超级管理员；PRIVILEGED → 仅超级管理员。

#### 移除 label
```
DELETE /api/v1/skills/{namespace}/{slug}/labels/{labelSlug}
DELETE /api/web/skills/{namespace}/{slug}/labels/{labelSlug}
```
权限校验同赋予。

### 5.3 公开查询 API

#### 获取可用标签列表（搜索页分类板块）
```
GET /api/v1/labels
GET /api/web/labels
```
返回 `visible_in_filter=true` 且 `type='RECOMMENDED'` 的标签，按 `sort_order` 排序。`PRIVILEGED` 一期不出现在搜索页分类筛选中，避免运营/特权标签与功能分类混淆。Response `data`:
```json
[
  {
    "slug": "code-generation",
    "type": "RECOMMENDED",
    "displayName": "代码生成"
  }
]
```
`displayName` 根据请求语言返回，fallback 顺序：当前语言 → en → slug。不分页。

## 6. ClawHub 兼容层

ClawHub CLI 兼容层的搜索接口 `GET /api/v1/search` 一期不支持 label 筛选。ClawHub 协议中没有 label 概念，无需兼容。

## 7. Frontend Design

### 7.1 搜索页

- 搜索框下方增加分类板块，水平排列 label 列表（数据来自 `GET /api/v1/labels`）
- 每个 label 显示当前语言的 display_name，fallback 顺序：当前语言 → en → slug
- 点击某个 label 高亮选中，搜索请求追加 `label` 参数；再次点击取消选中
- Label 之间单选互斥：点击另一个 label 切换选中，不支持组合筛选
- 选中状态通过 URL query parameter 同步，支持分享链接

### 7.2 Skill 详情页

- 在 skill 信息区域以 chip/badge 形式展示该 skill 的所有 label
- 特权标签使用不同的视觉样式区分（不同颜色或图标）
- 有权限的用户（owner / 命名空间管理员 / 超级管理员）看到编辑入口
- 编辑交互：弹出面板；超级管理员可从全部 label definition 中勾选/取消勾选，owner / 命名空间管理员仅可操作搜索页可见的 RECOMMENDED 标签
- 特权标签区域仅超级管理员可见和可操作

补充说明：
- 这不是仅靠新增独立 label API 就能完成的能力，skill detail DTO / OpenAPI / 前端类型 / 详情页查询链路都需要增加 labels 字段
- 建议 skill 详情首屏直接返回 labels，避免详情页再额外发起一次 label 查询导致展示和权限状态碎片化
- 一期仅要求 `SkillDetailResponse` 增加 `labels: List<SkillLabelDto>`；`SkillSummaryResponse` 暂不增加 labels，保持搜索结果与列表卡片改动最小
- `SkillLabelDto` 字段固定为 `slug`、`type`、`displayName`

### 7.3 管理后台

- 标签管理页面：列表展示所有标签定义，支持拖拽排序
- 创建/编辑标签：表单包含 slug（创建时填写，不可修改）、type 选择、visible_in_filter 开关，以及动态翻译条目（可添加任意语言的翻译）
- 删除标签需二次确认，提示会影响已关联的 skill

## 8. Testing

一期至少补充以下测试：
- `PostgresSearchRebuildService`：验证原有 keywords 来源与 label translations 的组合结果
- `PostgresSearchRebuildService`：验证 label 删除/翻译修改后，旧 label 词不会残留
- `PostgresFullTextQueryService`：验证 `labelSlugs` 过滤 SQL 生效，且 count 查询同步生效
- `SkillSearchController` / `SkillSearchAppService`：验证 `label` 参数透传
- promotion 相关测试：验证 source skill 与 target skill 的 labels 独立，不发生隐式复制

## 9. Audit

以下动作需记录到 `audit_log`，供后台追踪：
- `LABEL_CREATE`
- `LABEL_UPDATE`
- `LABEL_DELETE`
- `LABEL_SORT_ORDER_UPDATE`
- `SKILL_LABEL_ATTACH`
- `SKILL_LABEL_DETACH`
