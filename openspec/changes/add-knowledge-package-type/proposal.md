## Status

**Proposed / spec-only**。本文所有新增策略均为待维护者确认的推荐方案；`SHALL` 表示候选契约，不表示已获同意、已实现或承诺发布。实现与发行需另行决定。

## Why

[Issue #718](https://github.com/iflytek/skillhub/issues/718) 希望区分以资料交付为主的包，同时复用 SkillHub 的版本、治理、发现与安装能力。需要先明确分类属于哪个版本、旧客户端如何继续工作，以及同坐标和历史元数据如何处理。

维护者要求先提交可审阅的规范，而非实现；[原文](https://github.com/iflytek/skillhub/issues/718#issuecomment-6335397069)如下：

> Thanks for the concrete contract proposal and the revised legacy-search default. A spec-only artifact would make these choices reviewable without committing to implementation. Please include scenarios for mixed historical version types and latest-version changes, pinned-version resolution, unknown frontmatter values, existing coordinate collisions, additive packageType responses and unmodified clients continuing to search/install Knowledge as a Skill. Keep Skill/Suite resource identity unchanged in that draft. The exact metadata key and lifecycle rules still need maintainer agreement; implementation and release remain separate decisions.

## What Changes

- 推荐 Knowledge 为 `SKILL` 资源的版本级包分类，不新增与 `SKILL` / `SUITE` 并列的资源身份。
- 推荐 `SKILL.md` frontmatter 的嵌套 `metadata.package-type: skill|knowledge`；新提交缺省为 `skill`，声明无效则拒绝。
- 推荐公共分类跟随既有 latest published 候选，固定版本使用实际解析版本的类型；历史版本不随新版本重分类。
- 推荐旧搜索默认包含 Knowledge，旧安装仍按 Skill 工作；新过滤和可选响应字段采用显式接入，不要求严格旧消费者接受额外属性。
- 推荐保留启用前已接收版本的普通 Skill 语义，包括偶然使用同名键的历史数据；不追溯拒绝或自动重分类。

## Decision Relative to Issue #718

本稿延续[后续提案](https://github.com/iflytek/skillhub/issues/718#issuecomment-6209958184)修订后的默认搜索：**不隐藏 Knowledge**。原 Issue 的排除式默认不作为本稿目标；这项修订不等于维护者已批准整份契约。

## Capabilities

### New Capabilities

- `knowledge-package-type`：定义声明、版本投影、碰撞、发现、兼容与治理边界，见[候选需求](./specs/knowledge-package-type/spec.md)。

### Modified Capabilities

无其他 OpenSpec capability 的修改；既有 Suite 正文及 Skill/Suite 身份契约保持不变。

## Maintainer Gates

| Gate | 本稿具体推荐，仍待维护者同意 |
| --- | --- |
| G1 — Declaration | 采用嵌套键、严格小写值与历史版本保守处理；确认启用边界及可信分类来源。 |
| G2 — Lifecycle | 类型逐版本决定、缺省不继承；沿用发布、指针、yank、预览及 Promotion 语义。 |
| G3 — Exposure | 复用 `include=packageType` 显式响应扩展，类型过滤先限于资源发现接口；确认端点范围与缓存隔离。 |

细节、备选与依据见[设计](./design.md)；未通过这些 gate 不启动实现。

## Impact

本次只新增五份 OpenSpec 文件，无产品代码、数据库迁移、生成类型、配置或运行时改动。未来实现若获准，才评估解析/校验、版本分类存储、查询/索引、API schema 与客户端展示；继续使用原 Skill 包入口、安装目录和命令。不新增外部描述符、依赖求解、托管 RAG/向量/图服务、任意端点抓取/查询、凭据管理或服务端执行，也不改变可执行文件政策。
