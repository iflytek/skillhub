# Runtime 集成：解析版本、下载与合规快照

## 职责边界

SkillHub 和 Agent Runtime 的职责分开：

| 系统 | 权威负责内容 |
|------|--------------|
| SkillHub | 技能包、版本、元数据、合规声明快照、下载与审核记录 |
| Agent Runtime | 技能实际执行、输入输出、模型调用、工具调用、执行 trace |

SkillHub 不执行技能，因此不记录 Runtime trace，也不判断一次真实执行是否合规。SkillHub 提供的是版本级事实：某个不可变技能版本在发布时包含了什么合规声明，以及该声明快照的稳定摘要。

## Runtime 应记录什么

Runtime 在执行技能时，如果需要把执行链路与 SkillHub 的合规声明关联起来，建议记录以下字段：

| 字段 | 来源 | 说明 |
|------|------|------|
| `registryUrl` | Runtime 配置 | 使用的 SkillHub 注册中心地址 |
| `namespace` | SkillHub 坐标 | 技能命名空间，例如 `global` 或团队 slug |
| `skillSlug` | SkillHub 坐标 | 技能 slug |
| `requestedVersion` | Runtime 请求 | 用户请求的精确版本或标签 |
| `resolvedVersion` | SkillHub 响应 | 实际解析到的版本号 |
| `skillVersionId` | SkillHub 响应里的版本 `id` | 不可变版本 ID，审计关联的主键 |
| `complianceSnapshotDigest` | `complianceSnapshot.digest` | 该版本合规声明快照的稳定摘要 |
| `packageDigest` | 下载或安装流程 | 技能包内容摘要，便于确认执行内容 |
| `runtimeTraceId` | Runtime | Runtime 自己生成的执行链路 ID |

如果 Runtime 使用 Astron 自有 trace schema，可以把这些字段映射成 `x-astron-*` 键；这属于 Runtime 的 trace 约定，不是 SkillHub 服务端必须写入或解析的字段。

## 获取版本级合规快照

第一阶段不提供独立的 compliance API。Runtime 可以通过既有版本详情接口读取版本 ID 和快照：

```bash
GET /api/v1/skills/{namespace}/{slug}/versions/{version}
```

下面仅展示成功响应 `data` 内的关键字段，不是完整 HTTP 响应。先检查外层业务 `code`，再读取 `data.id` 和 `data.complianceSnapshot`；没有声明的版本可能没有快照，不应据此推断为已认证或执行违规。

```json
{
  "id": 123,
  "version": "1.2.0",
  "complianceSnapshot": {
    "schemaVersion": "1.0",
    "digest": "sha256:8d8c...",
    "items": [
      {
        "standard": "mitre-attack",
        "version": "v19.1",
        "controlId": "T1059",
        "title": "Command and Scripting Interpreter",
        "evidence": [
          {
            "type": "packaged-file",
            "path": "references/mitre-t1059.md",
            "sha256": "sha256:..."
          }
        ]
      }
    ]
  }
}
```

Runtime 应把 `id` 和 `complianceSnapshot.digest` 一起写入执行 trace。只记录 digest 不够，因为不同注册中心或未来迁移场景下需要版本 ID 来定位完整快照。

## 推荐执行链路

1. Runtime 根据用户请求解析技能坐标和版本。
2. Runtime 从 SkillHub 获取精确版本详情。
3. Runtime 下载并校验技能包。
4. Runtime 执行技能。
5. Runtime 在自己的 trace 中记录：
   - SkillHub 注册中心；
   - 技能坐标；
   - 实际版本号；
   - `skillVersionId`；
   - `complianceSnapshotDigest`；
   - Runtime 自己的执行证据。

这样审计系统可以先通过 Runtime trace 找到实际执行，再回到 SkillHub 查询该版本发布时的合规声明快照。

## 不建议的做法

- 不要把 `x-astron-compliance` 原文复制到 trace 后再由 Runtime 修改。
- 不要只记录技能 slug，不记录版本 ID；slug 指向的是技能容器，不是不可变版本。
- 不要把 SkillHub 的合规声明当成第三方认证结果。
- 不要要求 SkillHub 记录模型输入输出；这是 Runtime 的审计边界。

## 从解析到下载的接口顺序

集成方先确定 Registry 地址、技能坐标和调用身份。下面是 HTTP 请求格式，不是 Shell 命令；示例坐标必须替换为实例内真实技能。

```http
GET /api/v1/skills/global/example-skill/resolve?version=1.2.0
GET /api/v1/skills/global/example-skill/versions/1.2.0
GET /api/v1/skills/global/example-skill/versions/1.2.0/download
```

`resolve` 的 `version` 是精确版本；标签使用 `tag`，不能同时提供 `version` 与 `tag`。当前此接口不解析 `^1.2.0` 等版本范围。解析响应提供 `skillId`、`namespace`、`slug`、`version`、`versionId`、`fingerprint`、`matched` 和 `downloadUrl`。后续使用解析出的精确版本，不要在执行前再次解析 `latest`，否则可能得到另一版本。

版本详情使用统一响应包装，以下示例省略了部分详情字段，数值和时间仅为说明：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 123,
    "version": "1.2.0",
    "status": "PUBLISHED",
    "fileCount": 2,
    "totalSize": 2048,
    "complianceSnapshot": null
  },
  "timestamp": "2026-01-01T00:00:00Z",
  "requestId": "example-request-id"
}
```

`complianceSnapshot: null` 可能表示未提供声明或历史元数据无法投影；不应伪造一个空摘要。`fingerprint` 是 Registry 的包内容指纹，`complianceSnapshot.digest` 是声明快照摘要，两者用途不同，不能互换。若自行对下载 ZIP 计算摘要，应单独记录其算法与对象，不假定它等于 Registry 指纹。

下载接口可能直接返回 ZIP，也可能返回 `302` 和对象存储 `Location`。处理重定向时不要把 Registry 凭证转发给对象存储主机；使用 CLI 可复用现有安装校验流程。返回的短期下载地址不能替代技能坐标作为长期标识。

## 权限与失败处理

- 匿名请求可读取可见的公开已发布资源；命名空间成员可见和私有资源仍检查调用者身份、成员关系及所有权。路径可匿名访问不代表每个资源都公开。
- 版本详情包含维护者预览能力。能读取详情不等于版本可安装；解析流程要求可安装版本，Runtime 应记录并检查 `status`，不要把待审预览内容当成公开发布物。
- 资源不存在、无权访问、版本不可安装时停止执行；不要自动回退到同名技能、其他 Registry 或最新版本。
- 保存失败响应的 `requestId` 和脱敏错误用于排障，不记录 token、短期签名 URL 或技能运行输入中的秘密。

集成验收至少覆盖：公开版本成功解析与下载、受限资源拒绝匿名访问、指定版本不可用时明确失败、无合规声明版本正常表示为空、下载重定向不泄露认证信息。执行 trace 保存在 Runtime 自己的系统；这些接口不接收执行结果。
