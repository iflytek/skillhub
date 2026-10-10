# Runtime 集成契约

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
| `requestedVersion` | Runtime 请求 | 用户请求的版本、标签或版本范围 |
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

响应中的关键字段：

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

## 未来可能新增的 API

如果出现明确使用方，例如 Runtime 只需要合规快照而不需要完整技能详情，可以新增不可变版本维度的接口：

```text
GET /api/skill-versions/{skillVersionId}/compliance
```

当前阶段先复用版本详情响应，避免为尚未稳定的调用方提前设计多套 API。
