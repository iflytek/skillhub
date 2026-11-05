# skillhub 核心业务流

## 1 发布流程

上传、包校验、对象存储写入和数据库持久化在一次请求中同步完成；接收成功不等于安全扫描和人工审核已经完成。扫描启用时，上传会触发后续扫描，包含直发版本；`PUBLIC` / `NAMESPACE_ONLY` 上传要求扫描器启用。具体状态及分发条件见[生命周期文档](./14-skill-lifecycle.md)。

### 1.1 当前发布流程基线

```
用户提交发布
    │
    ▼
① 身份与权限校验（用户是否为该 namespace 的 MEMBER 以上）
    │
    ▼
② 技能包校验
   - SKILL.md 存在性、frontmatter 格式
   - 文件类型白名单、单文件大小限制、总包大小限制
   - 版本号 semver 合法性、不与已有版本冲突
   - [扩展点] PrePublishValidator 链（一期空实现）
    │
    ▼
③ 同步写入对象存储
   - 文件逐个上传到正式路径 `skills/{skillId}/{versionId}/{filePath}`，记录 SHA-256
   - 生成预打包 zip 到 `packages/{skillId}/{versionId}/bundle.zip`
    │
    ▼
④ 持久化数据
   - 创建或关联 skill 记录（首次发布时创建 skill）
   - 创建 skill_version（普通 PRIVATE 为 `UPLOADED`；普通 PUBLIC/NAMESPACE_ONLY 为 `PENDING_REVIEW`；直发为 `PUBLISHED`）
   - 创建 skill_file 记录
   - 解析 SKILL.md frontmatter → parsed_metadata_json
   - 生成 manifest_json
   - 直发及 PRIVATE 上传更新 skill.latest_version_id（指针非空不代表可公开分发）
    │
    ▼
⑤ 同步写入审计日志
    │
    ▼
⑥ 按发布分支触发后续处理：扫描启用时触发安全扫描；直发产生发布事件，待审版本等待审核
```

以下是版本创建时的初始分支；扫描启用时，返回前触发扫描会将非发布版本设为 `SCANNING`，扫描完成后才回到对应的 `UPLOADED` 或 `PENDING_REVIEW`。初始状态不等于响应状态：

- 普通 `PRIVATE` 上传创建 `UPLOADED` 版本，不创建审核任务；保存私有版本不等于公开发布。
- 普通 `PUBLIC` / `NAMESPACE_ONLY` 上传创建 `PENDING_REVIEW` 版本及 `PENDING` 审核任务。
- 审核通过转为 `PUBLISHED`，审核拒绝转为 `REJECTED`；扫描与可分发条件仍需分别检查。
- 撤回审核将 `PENDING_REVIEW` 版本变为 `UPLOADED`，删除关联待审任务。
- 提交人持有 `SUPER_ADMIN` 时，上传直接创建 `PUBLISHED`，跳过审核任务及目标 namespace 成员检查；内部 `forceAutoPublish` 路径也可直发。直发不等于跳过所有安全扫描与下载就绪检查。
- Web 与 CLI 发布入口共用上述发布语义。
- 普通重传遇到旧 `PENDING_REVIEW` 版本时，先撤回旧审核并将其改为 `UPLOADED`，再按新请求的可见性及直发条件创建版本。

### 1.2 生命周期读模型

当前代码中的 skill 生命周期展示与操作判断，不再依赖旧的 `latestVersionStatus`、`viewingVersionStatus` 一类拼装字段，而统一基于以下 projection：

- `headlineVersion`：projection 的默认主展示候选；前端可按选中版本或管理预览选择展示内容，不保证每个页面都直接显示该字段
- `publishedVersion`：当前已发布版本投影；详情投影与公开列表的就绪过滤存在区别，不能只凭字段非空判定可下载
- `ownerPreviewVersion`：详情 projection 中暴露给 owner / namespace 管理者的新一轮非公开版本；排除 `PUBLISHED`、`YANKED`，按创建时间及 ID 选择最新，且须比已有已发布版本更新
- `resolutionMode`：`PUBLISHED` / `OWNER_PREVIEW` / `NONE`

业务规则：

- 公开入口以已发布版本为分发候选；`publishedVersion` 不替代各入口的授权与就绪校验
- owner 进入详情页时，如果没有可用 `publishedVersion`，才允许 `headlineVersion = ownerPreviewVersion`
- 推广、安装及公开分发不能使用未发布预览版本；具体候选版本与下载就绪条件见[生命周期文档](./14-skill-lifecycle.md)
- `hidden` 是独立治理覆盖层，不属于 skill 生命周期状态机

### 1.3 读取与操作边界

完整状态、角色与端点差异以[生命周期文档](./14-skill-lifecycle.md)为准，不能把详情、文件预览、下载、审核与公开解析合并为一张通用权限矩阵：

| 场景 | 需要分别检查的边界 |
|------|--------------------|
| Skill 容器读取 | 可见性、hidden、容器状态及读取入口授权。`latest_version_id` 为空时的限制不能等同于“无 publishedVersion”；PRIVATE 上传也可能将指针设为 `UPLOADED` |
| 版本列表与详情预览 | 先通过容器授权；管理视角可访问非公开版本，不限于 `PENDING_REVIEW`；详情可读不表示可下载 |
| 常规下载 | 要求容器 ACTIVE、通过可见性与 namespace 检查；`PUBLISHED` 还须 downloadReady 且未 yank；`UPLOADED` / `PENDING_REVIEW` 仅允许 owner 或 namespace ADMIN/OWNER 下载 |
| 公开解析与安装 | 使用已发布且符合分发条件的版本，不把 ownerPreviewVersion 当作公开候选 |
| 审核详情与审核包 | 独立审核权限入口；审核绑定下载不计入公开下载量，不能套用普通详情或普通下载的权限结论 |
| 治理与提审 | 提审、撤回审核、归档、隐藏、yank 与删除是不同动作；平台角色是否穿透需按实际入口判断，不能从一个入口推广到所有读取路径 |

`YANKED` 可保留在管理版本列表中，但不允许常规下载。yank 命中当前指针时，重算其余已发布版本；不存在时置空。

### 对象存储写入策略

对象直接写入正式单文件路径 `skills/{skillId}/{versionId}/{filePath}` 与整包路径 `packages/{skillId}/{versionId}/bundle.zip`。数据库事务不能自动回滚对象存储：

- 发布事务注册完成回调；事务未提交时尝试删除本次登记的对象键。清理失败时通过独立事务入口记录补偿；补偿记录本身失败仍可能遗留对象。
- 版本删除允许 `DRAFT`、`REJECTED`、`SCAN_FAILED`、`UPLOADED`，但不允许通过该接口删除最后一个版本。正常事务路径在数据库提交后删除对象；整条技能硬删除也采用提交后清理，并保护共用对象。
- 对象删除失败时调用补偿记录入口。当前提交后回调中的普通补偿记录采用默认事务传播；真实事务下的持久化仍需单独验证，不能仅凭回调或 mock 测试宣称补偿可靠落库。
- 补偿任务针对已记录的失败，每轮完成后间隔 5 分钟重试，单轮最多 100 条；成功标记完成，失败记录尝试。
- 当前未发现每日扫描整个正式对象存储并与 `skill_file` 对账的通用 GC。进程崩溃或补偿记录失败等情况仍可能遗留对象，不能承诺自动回收所有孤儿。

实现依据：`SkillPublishService`、`SkillGovernanceService`、`SkillHardDeleteService`、`SkillStorageDeletionCompensationService` 和 `SkillStorageDeletionCompensationTask`。事务验证要求见[事务与外部副作用](../.trellis/spec/backend/transactions-and-concurrency.md)。

### CLI publish 请求规范

```
POST /api/v1/publish
Content-Type: multipart/form-data
Parts:
  - file: zip 包（必需）
  - namespace: 目标命名空间 slug（必需）
```

一期同步响应：服务端同步完成上传、校验、存储、持久化，返回 `200 OK` + skill_version 信息。

普通 CLI 上传启用扫描时，响应状态为 `SCANNING`；扫描完成后 PUBLIC/NAMESPACE_ONLY 进入 `PENDING_REVIEW`，PRIVATE 进入 `UPLOADED`。PRIVATE 关闭扫描时直接返回 `UPLOADED`。
如果调用方持有 `SUPER_ADMIN`，则直接创建 `PUBLISHED` 版本，分发仍受就绪检查约束。
Web 端与 CLI 保持同一发布语义，只是在交互上可提供更明确的审核提示。

`/api/v1/publish` 直发响应示例（普通上传的 status 按上述扫描与可见性分支返回）：

```json
{
  "data": {
    "skillId": 456,
    "skillVersionId": 123,
    "version": "1.2.0",
    "status": "PUBLISHED",
    "namespace": "team-name",
    "slug": "my-skill"
  }
}
```

## 2 团队技能提升到全局空间（派生发布）

不直接修改原 skill 的 `namespace_id`，而是在全局空间创建新的 skill，保留来源追溯。原团队 skill 继续存在，安装坐标 `@team/skill` 不受影响。

```
团队空间技能（已发布）
    │
    ▼
① 技能 owner 或 namespace admin 发起"提升到全局"申请
    │
    ▼
② 创建 promotion_request (source_skill_id, source_version_id, target_namespace_id, status=PENDING)
    │
    ▼
③ 平台管理员审核
   ├── 通过 →
   │   ① 在全局空间创建新 skill（source_skill_id = 原 skill ID）
   │   ② 复制 source_version_id 对应版本的文件和元数据到新 skill（严格使用申请时指定的版本，不取最新）
   │   ③ 新 skill.visibility = PUBLIC
   │   ④ promotion_request.target_skill_id = 新 skill ID，status → APPROVED
   │   ⑤ 搜索索引写入新 skill，同步写入审计日志
   │   （提升关系唯一事实来源是 promotion_request，UI 查询"是否已提升"通过该表判定）
   │
   └── 拒绝 → 记录原因，原技能不受影响
```

后续版本更新：
- 全局空间的新 skill 由其 owner 独立管理版本
- 原团队 skill 可继续独立迭代
- 两者版本不自动同步。来源 owner 或 namespace 管理员可在团队详情选择已发布版本，再次提交平台提升审核；审核通过后在原全局 skill 上新增版本，不覆盖原有版本
- 首次或再次提升通过时，全局 skill 的展示名和摘要优先取所选来源版本的 `parsedMetadataJson`（`displayName` / `description`）；该字段为空时回退到审核时来源 skill 的展示信息。非空元数据解析失败会抛错，不会静默回退
- 申请和审批时均检查目标身份、来源版本状态及全局同号冲突。全局独立上传可以同时待审；不同号版本分别发布，后通过者成为 latest，同号后通过的申请失败
- 版本号不比较数值高低；例如全局当前 v1.3、团队提交尚未占用的 v1.1，确认提示后仍可提交，审批通过后 v1.1 成为 latest，v1.3 保留在历史

撤销提升：
- 来源 owner 或 namespace 管理员提交撤销申请，平台管理员审核；平台管理员也可直接执行并留审计
- 撤销的是关联的整条全局派生 skill，不移动原团队 skill。通过后公众无法访问全局详情、搜索结果和下载，旧全局坐标释放；后台保留提升、撤销及操作审计
- 共用的对象存储文件继续供团队原技能使用。全局独立发布形成的版本也随该全局 skill 删除；已下载到客户端的文件无法收回
- 重新提升须重新申请、审核并通过同名冲突检查；原地址可能已被其他技能占用

提升流程当前严格绑定已发布版本：

- promotion request 的 `source_version_id` 必须属于来源 skill 且状态为 `PUBLISHED`；可以选择历史已发布版本，不要求等于 projection 的 `publishedVersion.id`
- 不允许直接提升 `ownerPreviewVersion`

## 3 下载流程

```
下载请求
    │
    ▼
① 校验技能状态（ACTIVE）与目标版本
   - PUBLISHED：须 downloadReady 且未 yank
   - UPLOADED/PENDING_REVIEW：仅 owner 或 namespace ADMIN/OWNER
    │
    ▼
② 可见性及 namespace 状态检查
   - PUBLIC、NAMESPACE_ONLY、PRIVATE、hidden 按对应授权规则处理
   - 不能因版本可读就跳过容器授权
    │
    ▼
③ 构建下载结果：优先预生成整包，缺失时按可读取文件重新打包
    │
    ▼
④ 对 PUBLISHED 同步更新技能和版本下载计数，再发布 SkillDownloadedEvent
   - 授权的未发布版本下载不计入公开下载量
    │
    ▼
⑤ 控制器返回预签名跳转或流式响应
```

计数表示请求通过授权并成功构建下载结果，不证明客户端已完整接收。审核绑定下载是独立授权入口，不计入上述公开下载量。缺失 bundle 时的回退会过滤不存在的单文件，因此“生成 ZIP 成功”也不证明历史包完整；完整性保障需要单独验证。

下载审计仍是业务要求：登录用户记录身份，匿名用户记录 IP / User-Agent 等必要请求信息。当前已核对的普通 Web / CLI 下载链尚未找到对应的同步审计落库；`SkillDownloadedEvent` 仅携带技能与版本 ID，不能替代该审计。覆盖入口、记录时机与失败行为需要独立确认和验证，本次文档对齐不取消此要求。

### download_count 热点行优化预案

一期使用原子 SQL 直接更新，可接受。如出现热点行瓶颈，切换为：
1. Redis `INCR` 做实时计数（key: `skill:downloads:{skillId}`）
2. 定时任务每 5 分钟批量回写 PostgreSQL
3. 查询时合并 PostgreSQL 存量 + Redis 增量

## 4 搜索流程

```
搜索请求 (keyword, namespaceSlug?, sortBy)
    │
    ▼
① 构建 SearchQuery
   - 匿名用户：visibility 限定为 PUBLIC
   - 已登录用户：根据命名空间成员关系计算可见范围
    │
    ▼
② SearchQueryService.search(query)
    │
    ▼
③ 返回分页结果（技能摘要 + 命名空间信息 + 评分 + 下载量）
```

## 5 收藏流程

```
收藏/取消收藏（需登录）→ 校验权限 → 写入/删除 skill_star
→ 异步更新 skill.star_count（原子 SQL）
```

## 6 评分流程

```
提交评分 (score: 1-5)（需登录）→ 校验权限 → 写入/更新 skill_rating
→ 异步重算 skill.rating_avg 和 rating_count（SELECT AVG + Redis 分布式锁防重复重算）
```

文字评价复用同一条 `skill_rating` 记录：用户提交 `score + review_text` 时同步更新评分并触发
`SkillRatedEvent`；删除评价只清空文字，保留星级评分。公开列表仅返回 `VISIBLE` 评价；
`SKILL_ADMIN` / `SUPER_ADMIN` 可隐藏或恢复评价，管理动作写入审计日志且不改变评分聚合。

## 7 异步事件汇总

| 事件 | 触发时机 | 消费方 |
|------|---------|--------|
| `SkillPublishedEvent` | 审核通过或直发等产生已发布版本的路径 | 搜索索引写入 |
| `SkillVersionYankedEvent` | 已发布版本撤回 | 提交后异步重建该 skill 的搜索文档；无剩余发布版本不代表物理移除索引行，见[索引写入时机](./04-search-architecture.md#4-索引写入时机) |
| `SkillStatusChangedEvent` | 归档 / 取消归档 | 归档移除搜索文档，取消归档重建；hide/unhide 不走此事件 |
| `SkillDownloadedEvent` | 已发布版本下载结果构建成功并完成同步计数后 | 当前未发现生产消费者；下载计数由下载服务同步完成 |
| `SkillStarredEvent` | 收藏/取消 | 收藏计数 |
| `SkillRatedEvent` | 评分提交 | 评分重算 |
| `SkillSuiteReviewSubmittedEvent` | PUBLIC 或 NAMESPACE_ONLY 套件版本进入待审核 | 事务提交后异步创建 `REVIEW` 类站内通知；团队空间通知其 Owner/Admin，全局空间通知平台技能管理员，通知目标为对应审核任务 |
| `ReviewCompletedEvent` | 审核完成 | 预留给后续通知能力（当前可不消费） |
| `SkillPublishedEvent`（提升路径） | 首次提升或提升更新审核通过 | 重建目标全局 skill 的搜索文档；不是独立的 `SkillPromotedEvent` |

一期用 Spring ApplicationEvent + `@Async` 实现，后续可替换为消息队列。

套件提交审核通知沿用用户的“审核通知”偏好，关闭该类别后不创建站内通知。通知从本次提交的套件版本读取名称和版本号，点击后进入统一审核中心的对应任务；进入详情和批准权限仍由审核接口校验，收到通知不等于获得审核权限。通知为提交后的 best-effort 副作用，不保证进程终止后的补发，也不新增邮件投递。

### 审计日志写入策略

审计要求是同步落库，与业务操作在同一请求内记录，不以异步事件替代。审计是企业内部平台的刚性需求；普通 Web / CLI 下载链的已知实现缺口见第 3 节，不能将本要求视为所有入口均已实现的证明。

异步事件仅用于搜索索引、计数器等可容忍延迟的场景。如果后续需要更强一致性，引入 outbox 模式，不依赖 ApplicationEvent + @Async 承担可靠性。

### 异步事件可靠性保障

Spring ApplicationEvent + @Async 存在 Pod 被杀时事件丢失的风险。补充以下兜底机制：

- 搜索索引：每小时检查已发布 skill 的缺失文档并补建，是待实现的兜底目标；当前已具备重建接口，但未发现对应定时调度，不能作为事件丢失后的自动恢复保证
- 计数器：可接受少量丢失，定时任务每天凌晨从 `skill_star` / `skill_rating` 表重算修正
- 优雅停机：`@Async` 线程池配置 `awaitTerminationSeconds=25`，配合 30s shutdown timeout

## 8 分布式并发安全措施

| 操作 | 并发控制方式 |
|------|-------------|
| 审核通过/拒绝 | 乐观锁：`UPDATE review_task SET status=? WHERE id=? AND version=?` |
| 版本发布 | 唯一约束：`(skill_id, version)` |
| 计数器更新 | 原子 SQL：`SET count = count + 1` |
| 评分重算 | 异步 + Redis 分布式锁防重复重算 |
| 写操作幂等 | Redis 存储 `X-Request-Id`，TTL 24h |

### 幂等去重规范

基于 `idempotency_record` 表实现完整幂等：

- `X-Request-Id` 由客户端生成（UUID v4 格式）
- 客户端不传时，服务端自动生成但不做幂等去重

去重流程：
1. Redis `SETNX` key=`idempotent:{requestId}`（快速去重缓存，TTL=24h）
   - key 已存在：查询 `idempotency_record` 表返回原始结果
2. key 不存在：插入 `idempotency_record`（status=`PROCESSING`）
3. 执行业务逻辑
4. 成功：更新 record 为 `COMPLETED`，填充 `resource_type` + `resource_id` + `response_status_code`
5. 失败：更新 record 为 `FAILED`
6. 重复请求时：查 record，COMPLETED 返回原始资源 ID，PROCESSING 返回 `409 Conflict`，FAILED 允许重试

适用范围：所有 POST/PUT/DELETE 写操作（发布、提审、创建 Token 等）

异常恢复策略：
- Redis key 存在但 `idempotency_record` 无记录（进程在两步之间崩溃）：视为脏状态，删除 Redis key，允许请求正常重入
- `idempotency_record.status = FAILED`：删除对应 Redis key，允许客户端用相同 `request_id` 重试
- `idempotency_record.status = PROCESSING` 超过 5 分钟未更新：视为僵死，标记为 FAILED，删除 Redis key，允许重试
