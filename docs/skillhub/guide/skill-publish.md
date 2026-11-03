# 发布与更新技能

## 从文件到可安装版本

单技能根目录只放一个 `SKILL.md`；多技能创作与导入使用[套装导入](./suite-bundle.html)，不要混入单技能 ZIP。

1. 在本地 Agent 中验证技能指令、脚本和引用路径，整理成独立目录。
2. 确认目标命名空间可写，选择 PUBLIC、NAMESPACE_ONLY 或 PRIVATE。依赖工具及配置须写入技能说明，发布不共享本地环境。
3. 上传预览后核对名称、版本、摘要与警告；阻断必须修正，确认警告不能替代校验。
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

版本可按精确版本、标签或指纹解析，不支持 `^` / `~` 范围。`latest`、`beta`、`stable` 等标签用于版本定位；`latest` 遵循发布状态和发布指针，不按最高 SemVer 自动选版。详情可浏览包内文件，并按版本或标签下载。

### 更新已有技能

在“我的技能”选择更新，或对原坐标重新发布。修改 `name` 可能创建另一技能；已发布版本不可覆盖，迭代使用新版本号。待审期间公众仍见旧版本；批准后核对详情版本、名称和摘要。

本地已有安装不会因服务端发布新版而自动更新。使用者需要明确执行 CLI 升级；套装成员还受套装固定版本约束，不能以成员发布新版本代替发布新的套装版本。

### 发布失败与处理方法

| 现象 | 下一步 |
| --- | --- |
| `SKILL.md` frontmatter 无效 | 修正 YAML 与必需字段，重新上传预览 |
| 预检查提示疑似密钥或不允许的文件 | 从包中移除敏感内容或修正结构，不能通过确认警告绕过硬错误 |
| 版本已存在 | 查看已有版本，更新使用新版本号 |
| 扫描仍在进行 | 等待状态更新，不重复上传同一版本 |
| `SCAN_FAILED` | 查看失败原因，按页面提供的重试入口处理；基础设施故障由部署方排查 |
| 审核拒绝 | 阅读具体意见，修订包后通过页面支持的重新提交流程操作 |
| 命名空间冻结或已失去成员资格 | 先恢复合法写权限；持有旧安装包不赋予发布权限 |


## 准备元数据与发布入口

### 准备元数据

frontmatter 必须含 `name`、`description`；`package.json` 不能替代技能元数据。将示例保存为 `my-skill/SKILL.md`：

```markdown
---
name: my-skill
description: Summarize a short text as numbered steps.
version: 1.0.0
---

Summarize the supplied text as numbered steps.
```

显示中文等易读名称，可选填 `metadata.display-name`：

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
已有技能增加展示名无需数据库迁移，须发布新版本。

打包已有技能时，应一并保留其所需的脚本、参考文件、`agents/` 元数据和许可证文件。
下一步发布命令的路径应指向该技能目录（本例为 `./my-skill`）。

网页上传后可编辑“显示名称”：包内 `metadata.display-name` 优先于 `name`，网页手填值优先于包且换包不覆盖，留空回退包内名称。上限 200 个字符，随版本审核；仅改头像时不可改名。

“我的技能”展示待审名称、摘要、一个主状态及与公开版的比较，可预览新版；公众仍见已发布版。详情可复制 `@命名空间/slug`，改名不改坐标。

`skillhub.example.com`、`YOUR_API_TOKEN` 与 `my-team` 是占位值；必须替换为实例地址、本人凭据和目标命名空间，不要提交真实 Token。

网页名称存于版本元数据，不改包内 `SKILL.md`；下载会重新打包，ZIP 字节可能不同。CLI 仍读包内名称。批准、撤下回退和提升时，名称与摘要随对应版本。

### 使用 CLI 上传

```bash
# 配置 SkillHub 注册中心
export SKILLHUB_REGISTRY=https://skillhub.example.com
export SKILLHUB_TOKEN=YOUR_API_TOKEN

# 发布到默认命名空间
skillhub publish ./my-skill

# 发布到指定命名空间
skillhub publish ./my-skill --namespace my-team
```

> ClawHub CLI 的发布与同步协议与 SkillHub 不兼容。发布请使用上面的 SkillHub CLI。

### 使用网页上传

访问实例 Web 入口的 `/dashboard/publish`，选择命名空间、上传 zip 文件、选择可见性后点击「发布」。

可选上传自定义技能头像：选择 PNG/JPEG、裁剪并等待私有上传成功，再提交技能。头像上传中或失败时不能提交；没有选择头像则正常提交并使用技能名首字母。更新技能时默认保留原头像，可选择替换或移除；移除仅在提交成功后生效，取消或提交失败不影响原头像。

技能包与头像同次提交时，共用一张技能审核申请，审核员一次批准或拒绝；通过前显示旧头像或首字母。文件上传成功不代表头像审核通过。仅需修改头像时，可从“我的技能”的“更新”入口操作，不需要发布新的技能版本；需要人工审核时仍进入统一技能审核队列。

机器判定头像违规时，新图保持私有。若开启人工审核，申请继续等待审核员决定，并非机器自动驳回；审核员可拒绝，认为误判时须填写理由才能批准。人工拒绝后，可在“我的技能”和更新页查看提交图片及拒绝原因。机器调用异常与违规不同，异常时须先重试，不能直接批准放行。

头像上传、审核状态及讯飞／阿里云接入步骤详见 [技能头像与图片审核接入](./skill-avatars.html)。

### 使用发布接口

```http
POST /api/v1/skills/{namespace}/publish
Content-Type: multipart/form-data

file: skill-package.zip
visibility: PUBLIC
```

![公开与私有技能的普通发布路径](/diagrams/skill-publish-lifecycle-sketch.png)

图示仅覆盖普通上传。

## 提交成功不等于发布成功

上传后查看返回状态及“我的技能”中的本次版本。普通 PUBLIC 和 NAMESPACE_ONLY 上传要求扫描已启用，版本先进入 `SCANNING`，扫描完成后回到 `PENDING_REVIEW`，审核批准才发布。PRIVATE 上传在完成已配置的扫描后停留在 `UPLOADED`，还需通过确认发布操作发布。特权自动发布使用另一条路径。

`SCAN_FAILED` 表示扫描执行失败，不代表已经判定包有害；扫描完成也可能有风险发现。上传成功、HTTP 成功或 CLI 正常退出，都不能单独证明版本已 `PUBLISHED`。更新审核期间，公众继续看到已发布版本，有权维护者可以预览待审版本。

### 创作草稿：创建、编辑与结构校验

本地目录走 ZIP／CLI 上传；逐文件接口编辑使用创作草稿。无网页工作台、草稿导出或直接提审／发布端点；草稿不是 `UPLOADED` 或待审版本。

创建要求命名空间可写，且账号为成员；超级管理员（`SUPER_ADMIN`）仅免成员检查，不免冻结／归档限制。已有草稿的读写及校验仅检查所有者或超级管理员，不重检成员资格与命名空间状态。使用浏览器会话，写请求携带实例 CSRF 令牌。API Token 白名单未开放创作端点，不能使用 CLI Token。无独立启用开关，旧实例先在 `/v3/api-docs` 查接口。

HTTP 请求用同源 API 客户端发送，不能贴进 Shell；子路径加实例前缀。`team`、`example-skill`、`{draftId}`、`{runId}` 是占位值，分别替换为有权访问的命名空间、名称和响应 ID。

| 操作 | 请求 | 完成标志 | 说明 |
| --- | --- | --- | --- |
| 创建草稿 | `POST /api/v1/authoring/drafts` | 响应 `data.id`、`data.revision` 存在 | `namespaceSlug`、`name` 必填，`requirement` 可选；创建 `SKILL.md` 骨架 |
| 核对当前内容 | `GET /api/v1/authoring/drafts/{draftId}`；`GET /api/v1/authoring/drafts/{draftId}/files` | 可读修订号与文件清单 | 文件正文通过 `GET /api/v1/authoring/drafts/{draftId}/files/content?path=SKILL.md` 读取 |
| 保存文件 | `PUT /api/v1/authoring/drafts/{draftId}/files` | 读取响应 `data.draft.revision` | `expectedRevision` 是写前并发前置；内容改变推进修订，未改变可保持原号，旧校验不能证明新内容 |
| 启动校验 | `POST /api/v1/authoring/drafts/{draftId}/runs` | 返回运行的 `data.id` 和 `data.draftRevision` | 运行固定修订快照，不提审 |
| 跟踪结果 | `GET /api/v1/authoring/runs/{runId}`；`GET /api/v1/authoring/runs/{runId}/findings`；重新 `GET /api/v1/authoring/drafts/{draftId}` | 运行 `status=SUCCEEDED`、`errorCount=0`，且草稿 `validated=true` | 比较运行 `draftRevision` 与草稿 `revision`；旧结果不证明新内容 |

创建请求体：

```json
{"namespaceSlug":"team","name":"example-skill","requirement":"整理会议材料并生成待办清单"}
```

保存请求中，`expectedRevision` 替换为刚读取的修订号，`content` 替换为完整正文；二进制文件可用 `encoding=base64`，不要复用示例修订号。

```json
{"path":"SKILL.md","content":"---\nname: example-skill\ndescription: 整理会议材料\n---\n\n# 使用说明\n按输入材料提取待办。","encoding":"text","contentType":"text/markdown","expectedRevision":1}
```

校验覆盖路径、重复项、数量／大小、frontmatter、内容／扩展名和合规声明结构；不执行技能／模型／Agent，不替代扫描或人工审核，不证明合规。发布须整理本地文件后上传，预留提交字段不是入口。

| 现象 | 最可能原因 | 定位手段 | 处置 |
| --- | --- | --- | --- |
| 接口不存在 | 实例版本未提供该接口 | 在实例 `/v3/api-docs` 查找创作路径 | 改用本地目录与 ZIP 上传，不要猜测提交端点 |
| 未登录或写请求被拒绝 | 会话、CSRF 或凭据类型不符 | 核对登录后的身份请求及请求携带的 CSRF 信息 | 重新完成实例登录，不要公开复制 Cookie 或令牌 |
| 创建被拒绝 | 非成员或命名空间不可写 | 查成员资格、命名空间状态 | 恢复合法条件，不要用超管绕过冻结／归档 |
| 已有草稿被拒绝 | 非所有者且非超级管理员 | 比较当前账号与草稿归属 | 联系所有者，不要借用凭据 |
| 保存发生修订冲突 | 其他编辑已推进修订 | 重新读取草稿 `revision` 和文件正文 | 比较并合并后再保存，不要省略修订校验强行覆盖 |
| 校验有错误或结果已过期 | 文件结构问题或校验后又修改 | 查看 findings 及运行、草稿的修订号 | 修复并重新校验，不要把旧运行的成功当作当前版本已发布 |

接口以 [创作控制器](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/authoring/SkillAuthoringController.java)、[草稿权限与修订规则](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/authoring/service/SkillDraftService.java)、[结构校验器](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/authoring/service/DraftStructureValidator.java)及 [Token 路由策略](https://github.com/iflytek/skillhub/blob/main/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistry.java)为准。

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

声明边界：

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
| `title` | 否 | 控制项名称，用于审核和搜索 |
| `evidence` | 否 | 证据列表，支持包内文件和外部 URL |

## 发布与版本查询契约

**发布技能包**：
```http
POST /api/v1/skills/{namespace}/publish
Content-Type: multipart/form-data

# 参数
file: MultipartFile (必需)
visibility: PUBLIC | PRIVATE | NAMESPACE_ONLY (必需)
```

参数说明：
| 参数 | 类型 | 说明 |
|------|------|------|
| namespace | string | 命名空间 slug（路径参数） |
| file | MultipartFile | 技能包 zip 文件 |
| visibility | enum | 可见性级别：PUBLIC（公开）、PRIVATE（私有）、NAMESPACE_ONLY（空间成员可见） |

**获取 Skill 详情**：
```http
GET /api/v1/skills/{namespace}/{slug}
```

**列出版本**：
```http
GET /api/v1/skills/{namespace}/{slug}/versions?page=0&size=20
```

**获取版本详情**：
```http
GET /api/v1/skills/{namespace}/{slug}/versions/{version}
```

**下载特定版本**：
```http
GET /api/v1/skills/{namespace}/{slug}/versions/{version}/download
```

**按标签下载**：
```http
GET /api/v1/skills/{namespace}/{slug}/tags/{tagName}/download
```

**版本解析**：
```http
GET /api/v1/skills/{namespace}/{slug}/resolve?version=1.2.0
```

## 包与标识限制

- 单个技能包不超过 100MB（可配置）；不要把大小限制与文件数量限制混同。
- 技能 slug 支持小写字母、数字、连字符和 Unicode 字符；发布前核对最终坐标。
