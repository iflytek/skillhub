# skillhub 核心业务流

## 1 发布流程

一期采用同步发布模型：上传、校验、存储、持久化在一次请求中同步完成。前端通过异步上传（带进度条）提升用户体验，但后端处理是同步的。

> **设计决策**：一期暂不考虑异步发布（uploadId、publishId、状态轮询、异步转正等）。一期技能包为文本资源包，体积有限（上限 10MB），同步处理足以满足需求。如后续引入大文件或复杂校验流程，再考虑异步模型。

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
   - 创建 skill_version（普通用户进入 `PENDING_REVIEW`，`SUPER_ADMIN` 直达 `PUBLISHED`）
   - 创建 skill_file 记录
   - 解析 SKILL.md frontmatter → parsed_metadata_json
   - 生成 manifest_json
   - 直发场景更新 skill.latest_version_id
    │
    ▼
⑤ 同步写入审计日志
    │
    ▼
⑥ 异步触发搜索索引写入
```

当前版本采用审核流，不再区分“Phase 2 直发”与“Phase 3 恢复审核”两套现实实现：

- 普通用户发布请求创建 `skill_version(status=PENDING_REVIEW)`
- 同步创建 `review_task(status=PENDING)`
- 审核通过后转为 `PUBLISHED`
- 审核拒绝后转为 `REJECTED`
- 例外：提交人持有 `SUPER_ADMIN` 平台角色时，发布入口直接创建 `skill_version(status=PUBLISHED)`，跳过 `review_task` 创建，同时不再要求其必须是目标 namespace 成员
- 上述例外必须对 Web、`/api/v1/publish`、`/api/compat/v1/publish` 保持一致

### 对象存储写入策略

一期同步写入正式路径，不使用临时区：
- 文件直接写入 `skills/{skillId}/{versionId}/{filePath}`
- 如果数据库事务失败，对象存储中的文件成为孤儿对象
- 定时 GC 任务：每天扫描对象存储中存在但数据库中无对应 `skill_file` 记录的文件，清理孤儿对象
- 删除 DRAFT/REJECTED 版本时，同步清理对应的对象存储文件

### CLI publish 请求规范

```
POST /api/v1/publish
Content-Type: multipart/form-data
Parts:
  - file: zip 包（必需）
  - namespace: 目标命名空间 slug（必需）
```

一期同步响应：服务端同步完成上传、校验、存储、持久化，返回 `200 OK` + skill_version 信息。

当前 CLI 默认行为：上传 → 进入审核。
如果调用方持有 `SUPER_ADMIN`，则直接发布为 `PUBLISHED`。
Web 端与 CLI 保持同一发布语义，只是在交互上可提供更明确的审核提示。

`/api/v1/publish` 响应：

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
- 两者版本不自动同步，如需同步由 owner 手动操作

## 3 下载流程

```
下载请求
    │
    ▼
① 校验技能状态（ACTIVE）、版本状态（PUBLISHED）
    │
    ▼
② 可见性检查
   - PUBLIC: 任何人（包括匿名用户）
   - NAMESPACE_ONLY: 该 namespace 的成员（需登录）
   - PRIVATE: owner 本人 + 该 namespace 的 ADMIN 以上（需登录）
    │
    ▼
③ 返回预生成包或按文件清单打包
    │
    ▼
④ 审计与统计
   - audit_log 同步写入（记录下载人/IP/版本）
   - download_count 异步更新（原子 SQL: download_count = download_count + 1）
   - 匿名下载：审计记录 IP + User-Agent，不关联用户
   - 已登录下载：审计记录用户 ID
```

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

## 7 异步事件汇总

| 事件 | 触发时机 | 消费方 |
|------|---------|--------|
| `SkillPublishedEvent` | 审核通过 | 搜索索引写入 |
| `SkillYankedEvent` | 版本撤回 | 搜索索引移除 |
| `SkillDownloadedEvent` | 下载完成 | 下载计数 |
| `SkillStarredEvent` | 收藏/取消 | 收藏计数 |
| `SkillRatedEvent` | 评分提交 | 评分重算 |
| `ReviewCompletedEvent` | 审核完成 | 预留给后续通知能力（当前可不消费） |
| `SkillPromotedEvent` | 提升到全局 | 搜索索引写入（新 skill） |

一期用 Spring ApplicationEvent + `@Async` 实现，后续可替换为消息队列。

### 审计日志写入策略

审计日志统一同步落库，与业务操作在同一请求内同步写入，不走异步事件。审计是企业内部平台的刚性需求，不可容忍丢失。

异步事件仅用于搜索索引、计数器等可容忍延迟的场景。如果后续需要更强一致性，引入 outbox 模式，不依赖 ApplicationEvent + @Async 承担可靠性。

### 异步事件可靠性保障

Spring ApplicationEvent + @Async 存在 Pod 被杀时事件丢失的风险。补充以下兜底机制：

- 搜索索引：定时任务每小时检查 `skill_version.status = PUBLISHED` 但 `skill_search_document` 中无对应记录的版本，补建索引
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
