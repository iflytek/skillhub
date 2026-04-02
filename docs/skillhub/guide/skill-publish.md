# Skill 发布与版本管理

## 功能描述

Skill 发布是 SkillHub 的核心功能。开发者可以将本地开发的 Agent 技能包一键上传到注册中心，系统会自动处理版本管理、元数据提取、文件索引等工作。

![概念图](/diagrams/skill-publish-concept.png)

**解决的问题**：

传统方式下，团队成员通过 Git 仓库或文件共享来分发技能包。这种方式存在几个痛点：

- **版本混乱**：不同版本散落在各处，难以追踪
- **权限失控**：无法精细控制谁能访问哪些技能包
- **发现困难**：新成员不知道团队已有哪些可用技能

SkillHub 提供了类似 npm 的发布体验，但增加了企业级的权限控制和审核机制。

**核心特性**：

- **语义化版本**：支持 `major.minor.patch` 版本号规范
- **标签系统**：`latest`、`beta`、`stable` 等自定义标签
- **多版本共存**：同一技能包可以保留多个历史版本
- **版本解析**：智能解析版本选择器（如 `^1.2.0`、`~2.0.0`）
- **文件浏览**：在线浏览技能包内的文件结构
- **下载分发**：支持按版本、按标签下载

## 使用场景

**场景一：开发者发布新技能**

你刚完成了一个 Claude Code 技能包，想让团队其他成员也能使用。

![操作截图](/screenshots/homepage.png)

**场景二：版本迭代**

技能包需要修复 bug 或添加新功能，发布新版本并保持向后兼容。

**场景三：Beta 测试**

新功能还不稳定，先发布 `beta` 标签让少数人测试，稳定后再推广到 `latest`。

**场景四：版本回滚**

发现新版本有严重问题，需要将 `latest` 标签指向上一个稳定版本。

## 使用步骤

1. **准备技能包**

确保技能包符合 SkillHub 规范：
- 包含 `skill.md`（技能描述）
- 包含 `package.json` 或 `SKILL.md`（元数据）
- 文件结构清晰，无敏感信息

2. **使用 CLI 发布（推荐）**

```bash
# 配置注册中心
export CLAWHUB_REGISTRY=http://localhost:8080

# 发布到默认命名空间
npx clawhub publish ./my-skill

# 发布到指定命名空间
npx clawhub publish ./my-skill --namespace my-team
```

3. **使用 Web UI 发布**

访问 `http://localhost:3000/dashboard/publish`，选择命名空间、上传 zip 文件、选择可见性后点击「发布」。

4. **使用 REST API 发布**

```bash
POST /api/v1/skills/{namespace}/publish
Content-Type: multipart/form-data

file: skill-package.zip
visibility: PUBLIC
```

![流程图](/diagrams/skill-publish-flow.png)

5. **安全扫描**

发布后，[Skill Scanner](/guide/scanner) 会自动扫描技能包，检测潜在的安全风险。扫描结果会显示在技能包详情页。

6. **等待审核**（如果命名空间开启了审核）

团队管理员会收到审核通知，审核通过后技能包正式发布。

7. **发布成功**

技能包可以通过搜索发现，其他人可以通过 CLI 或 Web UI 下载使用。

## API 接口

**发布技能包**：
```bash
POST /api/v1/skills/{namespace}/publish
Content-Type: multipart/form-data

# 参数
file: MultipartFile (必需)
visibility: PUBLIC | PRIVATE | INTERNAL (可选，默认 PUBLIC)
```

**参数说明**：
| 参数 | 类型 | 说明 |
|------|------|------|
| namespace | string | 命名空间 slug（路径参数） |
| file | MultipartFile | 技能包 zip 文件 |
| visibility | enum | 可见性级别：PUBLIC（公开）、PRIVATE（私有）、INTERNAL（内部） |

**获取 Skill 详情**：
```bash
GET /api/v1/skills/{namespace}/{slug}
```

**列出版本**：
```bash
GET /api/v1/skills/{namespace}/{slug}/versions?page=0&size=20
```

**获取版本详情**：
```bash
GET /api/v1/skills/{namespace}/{slug}/versions/{version}
```

**下载特定版本**：
```bash
GET /api/v1/skills/{namespace}/{slug}/versions/{version}/download
```

**按标签下载**：
```bash
GET /api/v1/skills/{namespace}/{slug}/tags/{tagName}/download
```

**版本解析**：
```bash
GET /api/v1/skills/{namespace}/{slug}/resolve?version=^1.2.0
```

## 注意事项

> **版本号规范**：SkillHub 使用语义化版本（Semantic Versioning）。版本号格式为 `major.minor.patch`，例如 `1.2.3`。

- **首次发布**：版本号建议从 `0.1.0` 或 `1.0.0` 开始
- **标签管理**：`latest` 标签会自动指向最新的稳定版本
- **审核流程**：如果命名空间开启了审核，新版本需要等待管理员批准
- **文件大小限制**：单个技能包不超过 100MB（可配置）
- **命名规范**：Skill slug 支持小写字母、数字、连字符和 Unicode 字符
- **版本不可变**：已发布的版本不能修改，只能发布新版本
