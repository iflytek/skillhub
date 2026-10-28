# Skill 搜索与发现

## 功能描述

SkillHub 提供了强大的全文搜索功能，让用户可以快速找到需要的技能包。

搜索支持关键词、命名空间和标签筛选，以及相关性、下载量和最新排序。

![概念图](/diagrams/skill-discovery-concept.png)

**核心特性**：

- **全文搜索**：搜索技能包名称、描述、标签、作者
- **智能过滤**：按命名空间、标签、可见性过滤
- **多种排序**：按相关性、下载量、最新排序
- **权限感知**：只显示用户有权访问的技能包
- **实时更新**：已发布且当前用户可见的技能可被搜索；索引刷新可能有延迟

**搜索算法**：

SkillHub 使用 PostgreSQL 全文搜索，支持：
- 中英文分词
- 模糊匹配
- 权重排序（标题权重 > 描述权重 > 标签权重）

## 使用场景

**场景一：新成员探索**

新加入团队的开发者想了解团队已有哪些可用的技能包。

![操作截图](/screenshots/skill-discovery-search.png)

**场景二：按需查找**

开发者需要一个处理 PDF 的技能包，搜索 "pdf" 关键词。

**场景三：浏览热门**

查看团队内下载量最高、评分最好的技能包，学习最佳实践。

**场景四：按标签过滤**

只查看 `data-processing` 标签的技能包。

## 使用步骤

### 使用 CLI 搜索和安装（推荐）

```bash
# 配置注册中心
export SKILLHUB_REGISTRY=http://localhost:8080

# 搜索技能包
npx @astron-team/skillhub@latest search pdf

# 安装技能包
npx @astron-team/skillhub@latest install pdf-parser

# 安装指定命名空间的技能包
npx @astron-team/skillhub@latest install my-team--pdf-parser
```

### 使用 Web UI 搜索

1. **访问搜索页面**

访问 `http://localhost:3000/search` 或在首页使用搜索框。

2. **输入关键词**

在搜索框输入关键词，例如 "pdf parser"。

3. **应用过滤器**

- 选择命名空间（例如只看 `iflytek` 命名空间）
- 选择标签（例如 `data-processing`）
- 选择排序方式（例如按下载量降序）

![流程图](/diagrams/skill-discovery-flow.png)

4. **查看结果**

搜索结果会实时更新，显示匹配的技能包列表。

5. **查看详情**

点击技能包卡片，查看详细信息、版本历史、文件列表。

如果技能被当前用户可见的套件收录，卡片会显示“关联套件”。点击卡片进入技能详情后，可以
查看关联套件、入口或普通成员身份，以及可见的配套技能，并跳转到套件详情了解整体用法和
安装指引。一个技能可以关联多个套件；这个标识不表示必须整套安装，也不改变单独安装技能的
方式。不可访问的套件不会显示关联提示。

6. **安装使用**

找到合适的技能包后，使用 CLI 命令安装或点击「下载」按钮。

## API 接口

**搜索技能包**：
```bash
GET /api/web/skills?q=pdf&namespace=iflytek&label=data-processing&sort=downloads&page=0&size=20
```

**参数说明**：
| 参数 | 类型 | 说明 |
|------|------|------|
| q | string | 搜索关键词（可选） |
| namespace | string | 命名空间过滤（可选） |
| label | string[] | 标签过滤（可选，可多选） |
| sort | enum | 排序方式：relevance（相关性）、downloads（下载量）、newest（最新） |
| page | number | 页码（从 0 开始） |
| size | number | 每页数量（默认 20，最大 100） |

响应字段和分页封装以部署实例的 OpenAPI 为准。

## 注意事项

> **权限控制**：搜索结果会根据用户权限自动过滤。NAMESPACE_ONLY 面向空间成员；PRIVATE 面向所有者及有权管理员，并非所有登录用户或所有空间成员。

- **搜索性能**：SkillHub 使用 PostgreSQL 全文搜索，支持中英文分词
- **实时更新**：已发布且有权访问的技能在索引刷新后可被搜索
- **标签规范**：建议使用统一的标签命名规范，便于过滤
