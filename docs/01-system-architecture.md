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
│   └── nginx.conf        # Nginx 配置（SPA 路由 + API 反向代理）
├── docker-compose.yml    # 本地开发依赖服务（PostgreSQL/Redis/MinIO）
├── Makefile              # 顶层开发编排（dev / dev-all / build）
├── docs/                 # 设计文档
└── README.md
```

简单分目录，各自独立构建，Makefile 串联。

## 8. 部署架构

同域部署，统一入口：
- `https://skills.example.com/` → 前端静态资源
- `https://skills.example.com/api/*` → 反向代理到 Spring Boot
- 生产环境通过 Nginx 或网关统一接入

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
