# Compliance Metadata 设计方案

状态：第一阶段已落地发布校验和版本级 snapshot 固化；详情展示、审核 diff、搜索 facet 和 Runtime trace 集成仍按本文后续阶段推进。

## 1. 背景

Issue #556 提出的方向是让 SkillHub 支持“可标准映射、可审计引用”的技能元数据。它参考了两个不同类型的开源仓库：

- `mukul975/Anthropic-Cybersecurity-Skills`：大量 `SKILL.md` 在 frontmatter 中声明 MITRE ATT&CK、NIST CSF 等标准映射，并通过 `references/standards.md` 等文件补充证据。
- `calesthio/OpenMontage`：通过 pipeline manifest、artifact schema、checkpoint 和 review gate 证明垂直工作流的可恢复、可审核和可追踪。

这两个仓库给 SkillHub 的启发不同：

- 标准映射应该进入 skill 协议和版本事实，而不是只作为 UI 标签。
- 运行时 trace 应由执行方记录，SkillHub 不应承担 Agent Runtime 的执行事实。

需要注意：`compliance` 不是当前已经被广泛应用的 `SKILL.md` 标准字段。SkillHub 现有协议文档已经约定 `x-astron-*` 作为平台私有扩展命名空间。因此第一阶段应使用 `x-astron-compliance`，先解决 SkillHub 自己的治理和审计需求；未来如果 OpenSkills / Agent Skills 生态形成公开字段，再通过兼容读取 `compliance` 或迁移工具对齐。

因此本方案采用职责分离：

> SkillHub 负责“这个技能版本声明了什么合规能力”；Agent Runtime 负责“这次执行实际用了哪个技能版本”。两者通过 `skillVersionId + complianceSnapshotDigest` 关联。

这里的 compliance 是作者随技能包提交的声明型元数据。SkillHub 第一阶段只验证字段结构、取值格式、
包内证据文件是否存在、外部证据 URL 是否是合法 HTTP(S) URL，并生成不可变快照摘要；它不验证外部标准内容是否真实适用，
也不代表第三方审计、认证通过或平台背书。

## 2. 职责边界

### 2.1 SkillHub 职责

SkillHub 是技能注册中心和元数据权威源，负责：

- 解析 `SKILL.md` frontmatter 中的 `x-astron-compliance` 字段。
- 发布时校验 compliance 元数据和证据引用。
- 将规范化结果固化为技能版本级 snapshot。
- 在已有技能详情、版本详情、审核和搜索能力中投影 compliance 信息。
- 记录 SkillHub 内部发生的发布、审核、compliance 变更审计。
- 为未来 Agent Runtime 引用提供稳定的 `skillVersionId` 和 `complianceSnapshotDigest`。

### 2.2 Agent Runtime 职责

Agent Runtime，例如 Astron、Claude Code、Codex、OpenClaw 或其他执行方，负责：

- 实际加载和执行技能。
- 生成 execution trace。
- 记录本次执行使用的 skill coordinate、skill version、`skillVersionId` 和 `complianceSnapshotDigest`。
- 记录运行时输入输出摘要、审批 gate、执行结果、错误和运行时策略。

SkillHub 不记录 Agent 每次执行，也不实现 Agent execution trace。

## 3. 非目标

第一阶段不做以下内容：

- 不新增独立 compliance 查询 API。
- 不实现 Astron execution trace。
- 不新增复杂 facet / 聚合搜索。
- 不引入外部审计系统集成。
- 不把 `compliance` 当作已经存在的上游通用标准字段。
- 不为了 compliance 过早新建复杂表结构，除非后续性能或查询需求明确。

## 4. 协议草案

建议在 `SKILL.md` frontmatter 中先支持 SkillHub/Astron 私有扩展字段 `x-astron-compliance`：

```yaml
---
name: incident-response-helper
description: Guide analysts through incident response triage and evidence collection.
version: "1.2.0"
x-astron-compliance:
  - standard: mitre-attack
    version: "v19.1"
    controlId: T1059
    title: Command and Scripting Interpreter
    evidence:
      - type: packaged-file
        path: references/standards.md
      - type: external-url
        url: https://attack.mitre.org/techniques/T1059/
---
```

字段含义：

| 字段 | 含义 |
|---|---|
| `standard` | 标准名称，例如 `mitre-attack`、`nist-csf`、`soc2`、`hipaa` |
| `version` | 标准版本，例如 `v19.1`、`2.0` |
| `controlId` | 标准控制项、技术编号或条款 ID |
| `title` | 人类可读名称 |
| `evidence` | 证据列表 |
| `evidence.type` | `packaged-file` 或 `external-url` |
| `evidence.path` | 技能包内证据文件路径，仅 `packaged-file` 使用 |
| `evidence.url` | 外部证据链接，仅 `external-url` 使用 |

未来兼容策略：

- 写入规范：第一阶段只推荐作者写 `x-astron-compliance`。
- 读取兼容：如果后续生态出现公开 `compliance` 字段，解析器可以同时读取 `compliance` 和 `x-astron-compliance`，但需要定义冲突优先级。
- 对外展示：UI 和审计报告仍统一展示为“Compliance Metadata”，不暴露内部字段名前缀给普通用户。

## 5. 版本级 Snapshot

发布时，SkillHub 将 compliance 规范化为版本级 snapshot，并写入版本元数据。

第一阶段优先复用：

```text
skill_version.parsed_metadata_json
```

建议结构：

```json
{
  "frontmatter": {
    "name": "incident-response-helper",
    "description": "Guide analysts through incident response triage and evidence collection.",
    "version": "1.2.0",
    "x-astron-compliance": []
  },
  "complianceSnapshot": {
    "schemaVersion": "1.0",
    "items": [
      {
        "standard": "mitre-attack",
        "version": "v19.1",
        "controlId": "T1059",
        "title": "Command and Scripting Interpreter",
        "evidence": [
          {
            "type": "packaged-file",
            "path": "references/standards.md",
            "sha256": "..."
          },
          {
            "type": "external-url",
            "url": "https://attack.mitre.org/techniques/T1059/"
          }
        ]
      }
    ],
    "digest": "sha256:..."
  }
}
```

`digest` 用于未来运行时 trace 或外部审计引用。第一阶段只生成并写入
`parsed_metadata_json`，不新增独立 endpoint；后续再通过既有详情或版本详情投影给前端。

## 6. 分步执行计划

### Phase 1：协议和领域模型

目标：先把 `x-astron-compliance` 字段定义清楚，并放在领域层。

建议新增位置：

```text
server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/metadata/
```

候选对象：

```text
ComplianceMapping
ComplianceEvidence
ComplianceEvidenceType
ComplianceMetadataService
ComplianceSnapshot
```

设计要求：

- `SkillMetadataParser` 继续只负责解析 frontmatter，不承担 compliance 业务校验。
- `ComplianceMetadataService` 负责提取、规范化、校验 compliance。
- 不在 controller 中做 compliance 校验。
- 使用已有 `x-astron-*` 私有扩展命名空间，不新增未验证的公开字段。

### Phase 2：发布时解析和校验

目标：技能发布时能识别并校验 compliance。

接入点：

```text
SkillPackageValidator
SkillPublishService
SkillVersion.parsedMetadataJson
```

基础校验规则：

- `x-astron-compliance` 缺失时兼容旧技能。
- `x-astron-compliance` 存在时必须是数组。
- 每个 mapping 必须是对象。
- `standard`、`version`、`controlId` 必填。
- `title` 可选，但应有长度限制。
- `evidence` 可选；提供时必须是数组。
- 同一版本内不允许重复 `standard + version + controlId`。
- mapping 数量、evidence 数量和字符串长度要有上限。

证据校验规则：

- `packaged-file.path` 必须存在于技能包。
- `packaged-file.path` 不允许 `../` 路径逃逸。
- `external-url.url` 只允许 `http` / `https`。
- 包内证据文件应计算 `sha256` 并写入 snapshot。

错误信息要求：

- 使用现有 i18n 机制。
- 不在领域服务中散落不可翻译的长英文错误字符串。

### Phase 3：固化版本级 Snapshot

目标：每个技能版本都有不可变 compliance snapshot。

实现要求：

- 发布成功后生成规范化 `complianceSnapshot`。
- snapshot 内容和 digest 与该 `SkillVersion` 绑定。
- 后续详情、审核、搜索均读取 snapshot，不重新解释最新源码。
- snapshot 为空时也要有确定行为，避免旧技能受影响。

第一阶段不强制新建表。后续出现结构化过滤、统计或性能瓶颈时，再考虑：

- `jsonb` GIN index；
- `skill_version_compliance_mapping` 表；
- 搜索 projection 表扩展。

### Phase 4：已有接口投影，不新增独立 API

目标：让前端和审核能看到 compliance，但不发布猜测性 public API。

建议：

- 在已有技能详情或版本详情 response 中增加 compliance projection。
- 审核详情中带出当前版本 compliance snapshot。
- 不新增以下 endpoint：

```text
GET /api/skills/{namespace}/{slug}/versions/{version}/compliance
GET /api/skills/{namespace}/{slug}/versions/{version}/metadata
```

后续只有出现明确使用方时再新增独立 API，例如：

- Agent Runtime 只需要拉 compliance snapshot，不需要完整技能详情。
- 企业审计系统按 `skillVersionId` 拉取合规声明。
- 前端需要单独比较两个版本的 compliance diff。
- 完整 detail payload 性能不可接受。

如果后续需要独立 API，优先考虑按不可变版本 ID 设计：

```text
GET /api/skill-versions/{skillVersionId}/compliance
```

### Phase 5：轻量搜索

目标：先提升可发现性，不直接做复杂 facet。

后续阶段：

- 在搜索文档重建时，将 snapshot 中的 `standard`、`controlId`、`title` 加入搜索文本。
- 用户搜索 `T1059`、`mitre-attack`、`nist-csf` 时能命中对应技能。

更后续再考虑：

- 按 standard filter。
- 按 controlId filter。
- compliance coverage 聚合。
- 独立索引或结构化 projection。

### Phase 6：审核和审计

目标：只记录 SkillHub 自己发生的事实。

审核展示：

- 当前版本 compliance snapshot。
- 与上一发布版本的 diff：
  - 新增 mapping；
  - 删除 mapping；
  - 修改 mapping；
  - evidence 变化；
  - digest 变化。

审计记录：

- 发布时记录 compliance digest。
- 审核通过 / 拒绝时记录 compliance diff 摘要。
- evidence 变化作为风险信息进入 audit detail。

不记录：

- Agent 执行输入输出。
- Astron trace。
- runtime 调用结果。

### Phase 7：文档

目标：让技能作者、平台维护者和 Agent Runtime 接入方都理解边界。

需要更新的文档：

- `docs/07-skill-protocol.md`：实现稳定后补充正式 `x-astron-compliance` 协议。
- 用户文档：说明如何在 `SKILL.md` 中声明 `x-astron-compliance`。
- 管理员文档：说明发布校验、审核 diff、审计记录。
- 集成文档：说明 Runtime 如何引用 `skillVersionId + complianceSnapshotDigest`。

文档必须明确：

> SkillHub 只提供版本级 compliance snapshot。运行时 trace 由 Agent Runtime 记录，并可引用 SkillHub 的 `skillVersionId` 和 `complianceSnapshotDigest`。

### Phase 8：测试

单元测试：

- 无 `x-astron-compliance` 的旧技能正常发布。
- 合法 `x-astron-compliance` 正常解析。
- `standard` 缺失失败。
- `version` 缺失失败。
- `controlId` 缺失失败。
- 重复 `standard + version + controlId` 失败。
- `packaged-file.path` 不存在失败。
- `packaged-file.path` 路径逃逸失败。
- `external-url.url` scheme 非法失败。
- digest 稳定生成。

发布链路测试：

- 上传含 `x-astron-compliance` 的技能包成功。
- 上传非法 `x-astron-compliance` 的技能包失败。
- 发布后 `parsedMetadataJson` 包含 `complianceSnapshot`。
- snapshot digest 与内容一致。

搜索测试：

- 搜标准名能命中。
- 搜 controlId 能命中。
- 无 compliance 的旧技能不受影响。

审核测试：

- 新版本新增 compliance。
- 新版本删除 compliance。
- 新版本修改 evidence。
- 审核详情能看到 diff。

## 7. 推荐 PR 拆分

### PR 1：协议、解析、校验、快照

范围：

- domain metadata service；
- package validator；
- publish snapshot；
- `parsedMetadataJson` 结构；
- 单元测试和发布链路测试。

不包含：

- UI；
- 搜索 facet；
- 独立 API；
- Agent trace。

### PR 2：详情页和审核展示

范围：

- 既有 response 增加 compliance projection；
- 技能详情展示；
- 审核 diff 展示；
- 前端测试。

### PR 3：轻量搜索

范围：

- 搜索文档增加 compliance keywords；
- 搜索测试。

不做复杂 facet。

### PR 4：文档和 Runtime 集成契约

范围：

- 用户文档；
- 管理员文档；
- Runtime 引用方式；
- `skillVersionId + complianceSnapshotDigest` 契约说明。

不实现 Astron trace。

## 8. 最终架构原则

1. SkillHub 不执行技能，因此不记录执行 trace。
2. SkillHub 是 skill metadata 和 version snapshot 的权威源。
3. Agent Runtime 是 execution trace 的权威源。
4. 合规审计通过 `skillVersionId + complianceSnapshotDigest` 把两边事实关联起来。
5. 第一阶段不发布猜测性 API；先通过已有详情和版本投影满足内部使用。
6. 先做稳定协议和可验证快照，再做 UI、搜索和外部集成。
