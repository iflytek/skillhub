# 命名空间与团队管理

## 功能描述

命名空间（Namespace）是 SkillHub 的核心组织单元。每个命名空间代表一个团队或项目，拥有独立的成员、权限和技能包。

![概念图](/diagrams/namespace-concept.png)

**命名空间的作用**：

- **隔离**：不同团队的技能包互不干扰
- **权限**：基于角色的访问控制（RBAC）
- **协作**：团队成员可以共同管理技能包
- **治理**：管理员可以审核、归档、冻结技能包

**角色体系**：

| 角色 | 权限 |
|------|------|
| **Owner** | 完全控制，包括删除命名空间、管理所有成员 |
| **Admin** | 管理成员、审核技能包、修改设置 |
| **Member** | 发布技能包、查看私有技能包 |

**命名空间状态**：

- **Active**：正常运行
- **Frozen**：冻结状态，无法发布新技能包
- **Archived**：归档状态，从搜索结果中隐藏

## 使用场景

**场景一：创建团队命名空间**

团队负责人创建一个新的命名空间，用于管理团队的技能包。

![操作截图](/screenshots/namespace-create.png)

**场景二：添加团队成员**

管理员邀请新成员加入命名空间，分配合适的角色。

![操作截图](/screenshots/namespace-members.png)

**场景三：权限管理**

调整成员角色，控制谁可以发布、审核、管理技能包。

**场景四：命名空间冻结**

发现命名空间有安全问题，临时冻结所有发布操作。

## 使用步骤

**创建命名空间**：

1. 访问 `/dashboard/namespaces`
2. 点击「创建命名空间」
3. 填写信息：
   - 名称：团队名称（例如 "iFlytek AI Team"）
   - Slug：URL 标识符（例如 "iflytek"）
   - 描述：简要说明团队职责和技能包范围

![流程图](/diagrams/namespace-create-flow.png)

4. 提交创建，系统自动将你设为 Owner

**添加成员**：

1. 进入命名空间详情页
2. 点击「成员」标签
3. 点击「添加成员」
4. 搜索用户（支持按用户名、邮箱搜索）
5. 选择角色（Owner / Admin / Member）
6. 确认添加

**管理权限**：

1. 在成员列表中找到目标用户
2. 点击「修改角色」
3. 选择新角色并确认
4. 系统会记录权限变更到审计日志

**冻结命名空间**：

1. 进入命名空间设置
2. 点击「冻结命名空间」
3. 填写冻结原因（可选）
4. 确认冻结

> 冻结后，命名空间内的所有技能包无法发布新版本，但已有版本仍可下载。

## API 接口

**创建命名空间**：
```bash
POST /api/v1/namespaces
Content-Type: application/json

{
  "name": "iFlytek AI Team",
  "slug": "iflytek",
  "description": "iFlytek's AI agent skills"
}
```

**参数说明**：
| 参数 | 类型 | 说明 |
|------|------|------|
| name | string | 命名空间名称（必需，2-50 字符） |
| slug | string | URL 标识符（必需，唯一，2-64 字符，只能包含小写字母、数字、连字符） |
| description | string | 描述（可选，最多 500 字符） |

**获取命名空间详情**：
```bash
GET /api/v1/namespaces/{slug}
```

**更新命名空间**：
```bash
PUT /api/v1/namespaces/{slug}
Content-Type: application/json

{
  "name": "iFlytek AI Team (Updated)",
  "description": "Updated description"
}
```

**添加成员**：
```bash
POST /api/v1/namespaces/{slug}/members
Content-Type: application/json

{
  "userId": "user-123",
  "role": "MEMBER"
}
```

**更新成员角色**：
```bash
PUT /api/v1/namespaces/{slug}/members/{userId}/role
Content-Type: application/json

{
  "role": "ADMIN"
}
```

**移除成员**：
```bash
DELETE /api/v1/namespaces/{slug}/members/{userId}
```

**冻结命名空间**：
```bash
POST /api/v1/namespaces/{slug}/freeze
Content-Type: application/json

{
  "reason": "Security investigation"
}
```

**解冻命名空间**：
```bash
POST /api/v1/namespaces/{slug}/unfreeze
```

## 注意事项

> **Slug 唯一性**：命名空间 slug 在全局范围内必须唯一，且创建后不可修改。建议使用团队或项目的简短标识符。

- **Owner 权限**：每个命名空间至少需要一个 Owner，最后一个 Owner 无法被移除
- **角色继承**：命名空间成员自动拥有该命名空间下所有技能包的访问权限
- **冻结机制**：管理员可以冻结命名空间，冻结后无法发布新技能包
- **归档机制**：归档的命名空间会从搜索结果中隐藏，但已有技能包仍可访问
- **审计日志**：所有成员变更、权限调整都会记录到审计日志
