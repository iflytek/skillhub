# 发布与更新技能

## 从文件到可安装版本

先确定要发布的是单个技能还是套装：单个技能的上传根目录包含一个 `SKILL.md`；多个技能一起创作和导入请用[套装导入](/guide/suite-bundle)。不要把多份 `SKILL.md` 随意塞进单技能 ZIP。

1. 在本地 Agent 中验证技能指令、脚本和引用路径，整理成独立目录。
2. 确认目标命名空间可写，选择 PUBLIC、NAMESPACE_ONLY 或 PRIVATE。公开发布不是共享所有者电脑上的环境：依赖工具及必要配置应写入技能说明。
3. 上传后核对预览的名称、版本、摘要和警告，再提交。预检查阻断必须修正；可确认的警告需逐项理解，不能把确认当作校验通过。
4. 在“我的技能”跟踪本次版本，按状态完成扫描、审核或私有确认发布。
5. 用另一位目标受众用户检查详情与下载权限，再在目标 Agent 安装并调用。注册中心发布成功不证明技能实际任务效果。

### 准备技能目录与版本

```text
my-skill/
├── SKILL.md
├── scripts/       # 仅在技能需要脚本时保留
└── references/    # 仅在说明引用资料时保留
```

`SKILL.md` 中引用的相对路径应能在包内找到。不要包含凭证、缓存或与技能无关的项目文件。`version` 可省略，服务器会生成时间版本；需要可预测版本时显式填写，例如 `1.0.0`，下一次更新填写 `1.0.1`。顶层 `version` 优先于 `metadata.version`，不要同时填写不同值。

### 更新已有技能

在“我的技能”选择更新，或对同一命名空间和技能名称重新执行发布命令。先核对目标坐标，避免修改 `name` 后创建了另一个技能。已经正式发布的版本不可覆盖，正常迭代应使用新版本号。上传待审新版期间，公开读者仍使用旧的已发布版本；审核通过后再检查详情显示的版本、名称和摘要。

本地已有安装不会因服务端发布新版而自动更新。使用者需要明确执行 CLI 升级；套装成员还受套装固定版本约束，不能以成员发布新版本代替发布新的套装版本。

### 发布失败与处理方法

| 现象 | 下一步 |
| --- | --- |
| `SKILL.md` frontmatter 无效 | 修正 YAML 与必需字段，重新上传预览 |
| 预检查提示疑似密钥或不允许的文件 | 从包中移除敏感内容或修正结构，不能通过确认警告绕过硬错误 |
| 版本已存在 | 先查看已有版本，正常更新使用新的版本号 |
| 扫描仍在进行 | 等待状态更新，不重复上传同一版本制造多次任务 |
| `SCAN_FAILED` | 查看失败原因，按页面提供的重试入口处理；基础设施故障由管理员排查 |
| 审核拒绝 | 阅读具体意见，修订包后通过页面支持的重新提交流程操作 |
| 命名空间冻结或已失去成员资格 | 先恢复合法写权限；持有旧安装包不赋予发布权限 |


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
- **版本解析**：按精确版本、标签或指纹解析；不支持 `^` / `~` 版本范围
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

准备一个根目录包含 `SKILL.md` 的技能目录：

- 在 `SKILL.md` 中同时编写 YAML frontmatter 和技能指令。
- frontmatter 必须包含 `name` 和 `description`；需要指定版本时，在其中设置 `version`。
- `package.json` 不会提供或替代这些技能元数据。
- 保持文件结构清晰，不包含敏感信息。

例如，将以下内容保存为 `my-skill/SKILL.md`：

```markdown
---
name: my-skill
description: Summarize a short text as numbered steps.
version: 1.0.0
---

Summarize the supplied text as numbered steps.
```

如需在列表、搜索结果和详情页显示中文或其他易读名称，可添加可选的 `metadata.display-name`：

```yaml
---
name: example-skill
description: 用于演示的示例技能。
metadata:
  display-name: 示例技能
  version: "1.0.0"
---
```

`name` 仍用于生成 slug、安装坐标和链接；`metadata.display-name` 仅用于展示。
展示名必须是字符串，去除首尾空白后最多 200 个字符；未填写、为 null 或空白时回退到 `name`。
展示名随技能版本发布：已有公开版本的标题在更新审核通过后才改变。
无需数据库迁移；已有技能可保持原 `name`，增加展示名后发布新版本。

打包已有技能时，应一并保留其所需的脚本、参考文件、`agents/` 元数据和许可证文件。
下一步发布命令的路径应指向该技能目录（本例为 `./my-skill`）。

网页发布或更新时，可在上传技能包后编辑“显示名称”。默认读取包内 `metadata.display-name`，缺省使用 `name`；你修改的值优先，换包不会覆盖已填写的名称，留空则采用包内名称。名称最多 200 个字符，随本次版本审核，不单独修改线上标题；只改头像时此字段不可用。

“我的技能”会显示待审新版的名称和摘要，仅保留一个主状态，并说明本次版本与当前公开版本。点击后可预览新版；公众继续看到已发布版本。详情中始终显示可复制的 `@命名空间/slug`，改名不改变链接或安装标识。

网页填写的名称保存在平台版本元数据中，包内 `SKILL.md` 不会被改写；下载 ZIP 沿用现有重新打包逻辑，压缩后的字节可能不同。CLI 发布继续使用包内名称规则。审核通过、撤下新版回退或提升所选版本时，展示名称和摘要跟随对应版本。

2. **使用 CLI 发布（推荐）**

```bash
# 配置 SkillHub 注册中心
export SKILLHUB_REGISTRY=http://localhost:8080
export SKILLHUB_TOKEN=YOUR_API_TOKEN

# 发布到默认命名空间
npx @astron-team/skillhub@latest publish ./my-skill

# 发布到指定命名空间
npx @astron-team/skillhub@latest publish ./my-skill --namespace my-team
```

> ClawHub CLI 的发布与同步协议与 SkillHub 不兼容。发布请使用上面的 SkillHub CLI。

3. **使用 Web UI 发布**

访问 `http://localhost:3000/dashboard/publish`，选择命名空间、上传 zip 文件、选择可见性后点击「发布」。

可选上传自定义技能头像：选择 PNG/JPEG、裁剪并等待私有上传成功，再提交技能。头像上传中或失败时不能提交；没有选择头像则正常提交并使用技能名首字母。更新技能时默认保留原头像，可选择替换或移除；移除仅在提交成功后生效，取消或提交失败不影响原头像。

技能包与头像同次提交时，共用一张技能审核申请，审核员一次批准或拒绝；通过前显示旧头像或首字母。文件上传成功不代表头像审核通过。仅需修改头像时，可从“我的技能”的“更新”入口操作，不需要发布新的技能版本；需要人工审核时仍进入统一技能审核队列。

机器判定头像违规时，新图保持私有。若开启人工审核，申请继续等待审核员决定，并非机器自动驳回；审核员可拒绝，认为误判时须填写理由才能批准。人工拒绝后，可在“我的技能”和更新页查看提交图片及拒绝原因。机器调用异常与违规不同，异常时须先重试，不能直接批准放行。

头像上传、审核状态及讯飞／阿里云接入步骤详见 [技能头像与图片审核接入](/guide/skill-avatars)。

4. **使用 REST API 发布**

```bash
POST /api/v1/skills/{namespace}/publish
Content-Type: multipart/form-data

file: skill-package.zip
visibility: PUBLIC
```

![公开与私有技能的普通发布路径](/diagrams/skill-publish-lifecycle-sketch.png)

图示覆盖普通上传；扫描失败、审核拒绝及特权发布的区别见下文。

5. **安全扫描**

发布后，[Skill Scanner](/guide/scanner) 会自动扫描技能包，检测潜在的安全风险。扫描结果会显示在技能包详情页。

6. **等待审核**（以返回状态为准）

团队管理员会收到审核通知，审核通过后技能包正式发布。

7. **发布成功**

技能包可以通过搜索发现，其他人可以通过 CLI 或 Web UI 下载使用。

## 提交成功不等于发布成功

上传后查看返回状态及“我的技能”中的本次版本。普通 PUBLIC 和 NAMESPACE_ONLY 上传要求扫描已启用，版本先进入 `SCANNING`，扫描完成后回到 `PENDING_REVIEW`，审核批准才发布。PRIVATE 上传在完成已配置的扫描后停留在 `UPLOADED`，还需通过确认发布操作发布。特权自动发布使用另一条路径。

`SCAN_FAILED` 表示扫描执行失败，不代表已经判定包有害；扫描完成也可能有风险发现。上传成功、HTTP 成功或 CLI 正常退出，都不能单独证明版本已 `PUBLISHED`。更新审核期间，公众继续看到已发布版本，有权维护者可以预览待审版本。

### 创作草稿的边界

创作 API 支持私有草稿、文件编辑和结构校验，目前不包含网页创作工作台或自动发布。校验通过不代表执行过技能，也不代表安全检查通过。创作草稿与上传后等待审核的技能版本是不同对象。

## 合规声明

技能作者可以在 `SKILL.md` frontmatter 中添加 `x-astron-compliance`，声明该技能版本与某些合规标准、控制项或安全知识库条目的映射关系。

```yaml
---
name: incident-response-helper
description: Helps analysts draft incident response steps.
x-astron-compliance:
  - standard: mitre-attack
    version: "v19.1"
    controlId: T1059
    title: Command and Scripting Interpreter
    evidence:
      - type: packaged-file
        path: references/mitre-t1059.md
      - type: external-url
        url: https://attack.mitre.org/techniques/T1059/
---
```

需要注意：

- 这是“作者声明”，不是 SkillHub 或第三方机构的合规认证。
- SkillHub 会校验字段结构、重复项、包内证据路径和外部 URL 格式。
- 发布成功后，声明会被固化为当前版本的 `complianceSnapshot`，并生成稳定摘要 `digest`。
- 后续版本如果新增、删除或修改合规声明，审核页会展示差异。
- 搜索 `mitre-attack`、`T1059` 或声明标题时，可以命中对应技能。

字段说明：

| 字段 | 必填 | 说明 |
|------|------|------|
| `standard` | 是 | 标准或框架标识，例如 `mitre-attack`、`nist-csf`、`soc2` |
| `version` | 是 | 标准版本 |
| `controlId` | 是 | 控制项、技术编号或条款 ID |
| `title` | 否 | 控制项名称，建议填写，便于审核和搜索 |
| `evidence` | 否 | 证据列表，支持包内文件和外部 URL |

## API 接口

**发布技能包**：
```bash
POST /api/v1/skills/{namespace}/publish
Content-Type: multipart/form-data

# 参数
file: MultipartFile (必需)
visibility: PUBLIC | PRIVATE | NAMESPACE_ONLY (可选，默认 PUBLIC)
```

**参数说明**：
| 参数 | 类型 | 说明 |
|------|------|------|
| namespace | string | 命名空间 slug（路径参数） |
| file | MultipartFile | 技能包 zip 文件 |
| visibility | enum | 可见性级别：PUBLIC（公开）、PRIVATE（私有）、NAMESPACE_ONLY（空间成员可见） |

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
GET /api/v1/skills/{namespace}/{slug}/resolve?version=1.2.0
```

## 注意事项

> **版本号规范**：建议显式使用 `major.minor.patch`，例如 `1.2.3`；省略版本时服务器生成时间版本。

- **首次发布**：版本号建议从 `0.1.0` 或 `1.0.0` 开始
- **标签管理**：`latest` 由发布状态及发布指针决定，不是客户端自动选择最高 SemVer
- **审核流程**：普通 PUBLIC / NAMESPACE_ONLY 上传需要扫描与审核；PRIVATE 上传后还需确认发布
- **文件大小限制**：单个技能包不超过 100MB（可配置）
- **命名规范**：Skill slug 支持小写字母、数字、连字符和 Unicode 字符
- **版本不可变**：已发布的版本不能修改，只能发布新版本
