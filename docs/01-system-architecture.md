# skillhub 系统架构设计

## 1. 技术基线

- JDK: 21
- Framework: Spring Boot 3.x（最新稳定版）
- Security: Spring Security + spring-boot-starter-oauth2-client
- Database: PostgreSQL 16.x
- Cache/Session: Redis 7.x（一期必须依赖，用于 Session 存储 + 分布式锁 + 幂等去重）
- Object Storage: `LocalFile` + S3 协议兼容对象存储双实现
- Search: PostgreSQL Full-Text Search（一期）
- Future Search: Elasticsearch / OpenSearch / Vector Search

## 2. 总体架构

采用单体优先、模块化单体设计。业务域清晰，一期规模不需要拆分微服务。

## 3. 后端模块结构

```
server/
├── skillhub-app                 # 启动、配置装配、Controller 聚合
├── skillhub-domain              # 领域模型 + 领域服务 + 应用服务
├── skillhub-auth                # OAuth2 认证 + RBAC + 授权判定
├── skillhub-search              # 搜索 SPI + PostgreSQL 全文实现
├── skillhub-storage             # 对象存储抽象 + LocalFile/S3 双实现
└── skillhub-infra               # JPA、通用工具、配置基础
```

## 4. 模块依赖方向（依赖倒置，禁止领域层依赖基础设施）

```
app → domain, auth, search, storage, infra
infra → domain          # infra 实现 domain 定义的 Repository 接口
auth → domain           # auth 引用 UserAccount 等领域实体
search → domain         # search 引用 SkillSearchDocument 等领域模型
storage → (独立抽象)     # 纯 SPI，不依赖 domain
```

核心原则：
- domain 是最内层，不依赖任何其他模块，只定义接口和实体
- infra 实现 domain 中定义的 Repository 接口（Spring Data JPA）
- app 负责装配所有模块，通过 Spring 依赖注入将 infra 实现注入 domain 接口
- 禁止 domain → infra 方向的依赖，避免领域层与 JPA、事件实现绑死

## 5. 各模块职责

### skillhub-app
- Spring Boot 启动类
- Controller 聚合：公开查询、认证后写接口、CLI API、兼容层、管理后台
- 全局异常处理、请求日志、OpenAPI 配置
- 配置文件与环境 profile
- 应用层 boundary 约定：
  - Controller 只负责 transport：鉴权上下文提取、请求参数绑定、响应包装
  - App Service 负责 workflow orchestration：跨 domain service 协调、分页入口、审计字段传递、调用 dedicated query repository
  - App Service 不直接承担复杂 read-model 拼装；当一个响应需要 join 多个聚合、快照字段、JSON 解析、展示态投影时，应优先抽成 query repository
  - `skillhub-app/repository` 包中的 query repository 只服务应用层读模型，不承载领域写规则

### skillhub-domain
- 核心实体：Skill, SkillVersion, SkillFile, SkillTag, Namespace, NamespaceMember, ReviewTask, PromotionRequest, AuditLog, SkillStar, SkillRating, IdempotencyRecord
- 领域服务：发布流程编排、审核状态机、命名空间管理、标签管理
- 应用服务：聚焦领域规则与用例编排
- Repository 接口定义（实现在 infra）

### skillhub-auth
- Spring Security OAuth2 Client 配置（一期 GitHub，可扩展多 Provider）
- `CustomOAuth2UserService`：OAuth2 用户 → 平台用户映射
- `IdentityBindingService`：外部身份 → 平台用户绑定
- Spring Session (Redis) 管理
- CLI Device Flow 授权、轮询与凭证签发
- API Token 签发、校验、吊销
- RBAC：角色定义、权限点、资源级授权判定
- 用户实体：UserAccount, IdentityBinding, ApiToken, Role, Permission, UserRoleBinding

### skillhub-search
- SPI 接口：`SearchIndexService`, `SearchQueryService`, `SearchRebuildService`
- 一期实现：`PostgresFullTextIndexService`, `PostgresFullTextQueryService`
- 独立搜索文档表 `skill_search_document`
- 未来扩展点：ES / 向量检索实现

### skillhub-storage
- SPI 接口：`ObjectStorageService`
- 一期实现：`LocalFileStorageService`（本地开发/零依赖）+ `S3StorageService`（集成测试/生产）
- 文件哈希校验、打包下载
- 对象 key 规则（使用不可变 ID，避免命名空间变更导致 key 失效）：
  - 正式路径：`skills/{skillId}/{versionId}/{filePath}`
  - 打包路径：`packages/{skillId}/{versionId}/bundle.zip`

### skillhub-infra
- Spring Data JPA Repository 实现
- Repository 实现
- 通用工具（ID 生成、时间、JSON 等）
- Spring Events 异步事件基础设施

## 6. 前端工程结构

```
web/
├── src/
│   ├── app/              # 路由、全局 Provider、布局
│   ├── pages/            # 页面入口
│   ├── features/         # 搜索、上传、版本管理、审核等业务功能
│   ├── entities/         # skill、user、namespace 等领域展示逻辑
│   ├── shared/           # 通用组件、hooks、工具
│   └── api/              # openapi-typescript 生成的类型 + openapi-fetch 客户端
├── package.json
└── vite.config.ts
```

技术栈：React 19 + TypeScript + Vite + shadcn/ui + Tailwind CSS + TanStack Query + TanStack Router + openapi-fetch

## 7. Monorepo 顶层结构

```
skillhub/
├── server/               # Maven 多模块 Java 后端
│   └── Dockerfile        # 后端多阶段构建
├── web/                  # React 前端
│   ├── Dockerfile        # 前端多阶段构建
│   ├── nginx.conf.template        # Nginx 运行时模板
│   └── runtime-config.js.template # 前端运行时环境变量模板
├── docker-compose.yml    # 本地开发依赖服务（PostgreSQL/Redis/MinIO）
├── compose.release.yml   # 单机运行时编排（发布镜像 + PostgreSQL + Redis）
├── .env.release.example  # 单机运行时环境变量模板
├── .github/workflows/    # GitHub Actions 镜像发布流程
├── Makefile              # 顶层开发编排（dev / dev-all / build）
├── docs/                 # 设计文档
└── README.md
```

简单分目录，各自独立构建，Makefile 串联。

## 8. 部署架构

部署模型收敛为两条路径：

- 开发路径：`make dev-all`。前后端在宿主机运行，`docker-compose.yml` 只负责 PostgreSQL、Redis、MinIO。
- 交付路径：GitHub Actions 构建并发布 `server` / `web` 镜像；用户通过 `compose.release.yml` 在本地一键拉起前后端容器和基础服务。
- 发布镜像为多架构 manifest，至少覆盖 `linux/amd64` 与 `linux/arm64`。

单机运行时统一入口：
- `http://localhost/` → Web 容器（Nginx）
- `http://localhost/api/*` → Web 容器反向代理到 Spring Boot
- `http://localhost:8080/actuator/health` → 后端健康检查

单机运行时默认使用 `docker` profile：
- `docker` 负责容器运行时初始化，例如首个管理员账户
- 数据库、Redis、对象存储、站点公网地址都通过环境变量注入
- 生产环境不启用 `local` profile，因此不会暴露 mock 登录旁路

## 9. 分布式环境要求

本服务在 K8s 中部署多个 Pod，所有组件必须无状态设计。

| 组件 | 一期要求 | 职责 |
|------|---------|------|
| PostgreSQL 16.x | 主从 | 主存储 |
| Redis 7.x | Sentinel 或 Cluster | Session 存储 + 分布式锁 + 幂等去重 |
| 对象存储 | LocalFile（开发）/ MinIO / 云厂商 S3 | 技能包文件 + 预打包 zip |
| Ingress | Nginx Ingress Controller | 路由分发 + TLS 终止 |

## 10. 推荐的一期技术决策

- ORM：Spring Data JPA (Hibernate)
- API 文档：Springdoc OpenAPI
- 对象存储：开发默认 LocalFile，集成测试/生产使用 MinIO / AWS S3 兼容接口
- 异步任务：Spring Events + 异步线程池，后续视复杂度引入 MQ
- 缓存/Session：Spring Session + Redis
- 数据库迁移：Flyway
- 认证：Spring Security OAuth2 Client（一期 GitHub）
- 镜像发布：GitHub Actions 推送至 GHCR，默认维护 `edge` 与语义化版本标签
- 运行时兼容：发布镜像默认输出 `linux/amd64` + `linux/arm64` 多架构 manifest

## 11. Repository / Query Boundary 约定

为了减少“应用层直接拼读模型”和“repository 风格混用”带来的认知成本，后端按下面的规则收敛：

### 11.1 Domain Repository Port

- 放在 `skillhub-domain`
- 服务于聚合读写、状态迁移、规则判断
- 可以被 domain service 直接依赖
- 返回值以领域对象和领域查询语义为主；当前代码里允许继续使用 Spring Data 的 `Page` / `Pageable`，但这是现阶段接受的折中，不代表所有新读模型都应继续扩大这一模式

适用场景：

- `SkillRepository`、`ReviewTaskRepository`、`PromotionRequestRepository`
- 领域规则需要读取或持久化聚合本身
- 一个用例的核心价值在“改变状态”而不是“拼响应”

### 11.2 App Query Repository

- 放在 `skillhub-app/repository`
- 服务于 controller / app service 需要的 read model，而不是领域写规则
- 输入通常是领域对象列表、分页结果内容或稳定 ID 集合
- 输出通常是 DTO、summary card、inbox item、admin list row 之类的展示态模型

适用场景：

- 需要 join 多个 repository / service 结果
- 需要做展示态投影、兼容层映射、旧字段快照回填、JSON 提取
- 同一类 read-model 组装逻辑会被多个 app service / controller 复用

当前样例：

- `GovernanceQueryRepository`
- `MySkillQueryRepository`
- `ProfileReviewQueryRepository`

### 11.3 App Service

- 放在 `skillhub-app/service`
- 负责 workflow owner 语义，而不是底层数据拼接细节
- 可以同时调用 domain service、domain repository port、app query repository
- 应优先表达“这个入口做什么”，而不是“这个入口怎样拼 DTO”

允许：

- 解析筛选条件、分页参数、平台角色
- 选择调用哪条 domain workflow
- 调用 query repository 组装最终 read model

不鼓励：

- 在 app service 里重复写批量 user lookup、namespace join、version projection、JSON 字段提取
- 让多个 app service 各自复制同类 summary/inbox/list row 组装代码

### 11.4 直接 Persistence Access

- 仅在少数场景允许，例如高度专用的搜索 SQL、管理端特殊检索、兼容层过渡适配
- 这类入口应尽量集中，并通过命名或 package docs 明确“它为什么没有走 domain repository port 或 app query repository”

### 11.5 选择规则

面对一个新读用例时，按下面顺序判断：

1. 如果它主要服务状态迁移或领域规则判断，优先放在 domain repository port / domain service。
2. 如果它主要服务页面、列表、详情响应组装，而且需要 join 多个来源，优先建 app query repository。
3. 如果它只是一个很薄的单聚合读取，不需要额外投影或 join，可以直接由 app service 调用现有 domain repository/query service。
4. 如果必须直接写 SQL 或 `EntityManager`，需要在类注释里说明原因和边界，避免它演变成默认模式。
