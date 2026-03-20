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

## 2. Data Model

### 2.1 label_definition（标签定义表）

```sql
CREATE TABLE label_definition (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(64) UNIQUE NOT NULL,   -- 英文标识，必填，如 code-generation
    type        VARCHAR(16) NOT NULL,          -- RECOMMENDED | PRIVILEGED
    visible_in_filter BOOLEAN NOT NULL DEFAULT true, -- 是否在搜索页分类板块展示
    sort_order  INTEGER NOT NULL DEFAULT 0,    -- 分类板块显示顺序
    created_by  VARCHAR(128) REFERENCES user_account(id),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

- `slug` 即英文名称，作为语言无关的唯一标识
- `type` 区分系统推荐标签和特权标签，决定权限控制策略
- `visible_in_filter` 控制是否出现在搜索页分类板块，RECOMMENDED 和 PRIVILEGED 均可配置

### 2.2 label_translation（标签翻译表）

```sql
CREATE TABLE label_translation (
    id          BIGSERIAL PRIMARY KEY,
    label_id    BIGINT NOT NULL REFERENCES label_definition(id) ON DELETE CASCADE,
    locale      VARCHAR(16) NOT NULL,          -- 语言代码，如 en、zh、ja
    display_name VARCHAR(128) NOT NULL,        -- 该语言的显示名称
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(label_id, locale)
);
```

- 支持动态语言：管理员可为任意语言添加翻译，不限于系统当前支持的语言列表
- 前端展示 fallback 顺序：当前语言 → en → slug

### 2.3 skill_label（skill 与 label 关联表）

```sql
CREATE TABLE skill_label (
    id          BIGSERIAL PRIMARY KEY,
    skill_id    BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    label_id    BIGINT NOT NULL REFERENCES label_definition(id) ON DELETE CASCADE,
    created_by  VARCHAR(128) REFERENCES user_account(id),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(skill_id, label_id)
);
```

- Label 挂在 skill 级别，与版本无关
- 级联删除：删除 label_definition 时自动清理关联

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
| 赋予/移除 RECOMMENDED label | skill_label | ✅ | ✅（本空间） | ✅（自己的 skill） | ❌ |
| 赋予/移除 PRIVILEGED label | skill_label | ✅ | ❌ | ❌ | ❌ |
| 查看 label | 所有表 | ✅ | ✅ | ✅ | ✅（受 skill 可见性约束） |

- 标签定义和翻译的管理是全局操作，仅超级管理员
- 赋予 label 到 skill 的权限取决于 label 的 type
- 查看权限跟随 skill 本身的可见性规则，不额外控制

## 4. Search Integration

采用翻译文本展开写入搜索文档方案。

### 4.1 Keywords 字段写入

在构建 `SkillSearchDocument` 时，将 skill 关联的所有 label 的所有语言翻译文本追加到 `keywords` 字段。

**重要：** keywords 字段可能已有其他业务写入的内容，label 翻译文本作为独立部分追加拼接，不得覆盖原有内容。实现上在搜索文档构建逻辑中分段拼接，各部分职责清晰。

示例：
```
[原有 keywords 内容] Code Generation 代码生成 Official 官方推荐
```

### 4.2 搜索文档重建触发时机

| 事件 | 影响范围 | 处理方式 |
|------|---------|---------|
| Skill 被赋予/移除 label | 单个 skill | 同步重建该 skill 搜索文档 |
| label_translation 被修改 | 所有关联该 label 的 skill | 异步批量重建 |
| label_definition 被删除 | 所有关联该 label 的 skill | 异步批量重建 |

### 4.3 分类筛选

搜索页分类板块的筛选不走全文搜索，而是通过 `skill_label` JOIN `label_definition` 精确过滤 `label_id`，再与搜索结果取交集。避免全文搜索的模糊性问题。

搜索 API 增加可选 query parameter：
```
GET /api/v1/skills/search?q=xxx&label=code-generation
```

### 4.4 tsvector 权重

不改变现有权重体系，keywords 保持 B 权重：
- A 权重：title (displayName)
- B 权重：summary / keywords（含 label 翻译文本）
- C 权重：searchText

## 5. API Design

### 5.1 管理后台 API（超级管理员）

#### 列出所有标签定义
```
GET /api/v1/admin/labels
```
Response: 所有 label_definition + 关联的 label_translation 列表

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
Body 同创建，slug 不可修改。

#### 删除标签定义
```
DELETE /api/v1/admin/labels/{slug}
```
级联删除关联的 translations 和 skill_label 记录，触发异步搜索文档重建。

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

#### 获取 skill 的所有 label
```
GET /api/v1/skills/{namespace}/{slug}/labels
```

#### 赋予 label
```
PUT /api/v1/skills/{namespace}/{slug}/labels/{labelSlug}
```
权限校验：RECOMMENDED → owner / 命名空间管理员 / 超级管理员；PRIVILEGED → 仅超级管理员。

#### 移除 label
```
DELETE /api/v1/skills/{namespace}/{slug}/labels/{labelSlug}
```
权限校验同赋予。

### 5.3 公开查询 API

#### 获取可用标签列表（搜索页分类板块）
```
GET /api/v1/labels
```
返回 `visible_in_filter=true` 的标签，按 `sort_order` 排序，包含当前请求语言的翻译。

## 6. Frontend Design

### 6.1 搜索页

- 搜索框下方增加分类板块，水平排列 label 列表（数据来自 `GET /api/v1/labels`）
- 每个 label 显示当前语言的 display_name，fallback 顺序：当前语言 → en → slug
- 点击某个 label 高亮选中，搜索请求追加 `label` 参数；再次点击取消选中
- Label 之间单选互斥：点击另一个 label 切换选中，不支持组合筛选
- 选中状态通过 URL query parameter 同步，支持分享链接

### 6.2 Skill 详情页

- 在 skill 信息区域以 chip/badge 形式展示该 skill 的所有 label
- 特权标签使用不同的视觉样式区分（不同颜色或图标）
- 有权限的用户（owner / 命名空间管理员 / 超级管理员）看到编辑入口
- 编辑交互：弹出面板，从系统推荐标签列表中勾选/取消勾选
- 特权标签区域仅超级管理员可见和可操作

### 6.3 管理后台

- 标签管理页面：列表展示所有标签定义，支持拖拽排序
- 创建/编辑标签：表单包含 slug（创建时填写，不可修改）、type 选择、visible_in_filter 开关，以及动态翻译条目（可添加任意语言的翻译）
- 删除标签需二次确认，提示会影响已关联的 skill
