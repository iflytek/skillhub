# skillhub 交付路线

## Phase 0：设计定稿（当前阶段）

产出：架构设计文档、数据库 DDL、API OpenAPI spec 草案、前端线框图

已冻结决策：
- 技能坐标体系：`@{namespace_slug}/{skill_slug}`，兼容层使用 `--` 双连字符映射（详见 `00-product-direction.md` 1.1 节）
- 一期同步发布模型，暂不考虑异步发布
- API Token 一期继承用户全部权限（非最小权限），后续版本细化
- CLI 主认证切换为 OAuth Device Flow，Bearer Token 统一用于 CLI API 和兼容层
- ClawHub CLI 兼容层基地址 `/api/compat/v1`，通过 `/.well-known/clawhub.json` 发现

## Phase 1：工程骨架 + 认证打通

### 后端

- Maven 多模块初始化（6 个模块）
- Spring Boot 启动、配置、Profile 分层
- Flyway + 数据库初始化
- Redis 集成（Session + 分布式锁）
- Spring Security OAuth2 Client 配置（GitHub OAuth 登录）
- CustomOAuth2UserService + IdentityBindingService（自动注册/绑定）
- Spring Session (Redis) 管理、API Token 签发校验
- RBAC 基础（SUPER_ADMIN / SKILL_ADMIN / USER_ADMIN / AUDITOR + 命名空间角色）
- 全局异常处理、requestId 透传、日志格式
- Springdoc OpenAPI、健康检查
- CSRF 防护（Cookie-to-Header 模式，CLI API 豁免）
- 本地开发 MockAuthFilter（`local` profile）
- 基础限流：Nginx Ingress `limit-req` 按 IP 限流（认证/搜索/下载接口）

### 前端

- Vite + React + TypeScript 初始化
- shadcn/ui + Tailwind 配置
- TanStack Router 路由骨架、TanStack Query 配置
- openapi-fetch 客户端生成管线
- 布局组件、OAuth 登录流程（调用 `/api/v1/auth/providers` → 跳转）
- 登录态检测（`/api/v1/auth/me`）+ 路由守卫
- Makefile 顶层编排

### 验收

前后端能跑，GitHub OAuth 登录可用，AccessPolicy 准入策略生效，`/api/v1/auth/me` 可用，Token 可用，OpenAPI spec 可访问，Ingress 基础限流生效

## Phase 2：命名空间 + Skill 核心链路

### 后端

- 命名空间 CRUD + 成员管理
- 对象存储集成（LocalFile + S3 双实现）
- 技能发布（上传 → 校验 → 存储 → 审核 / 上线，一期同步处理）
- 技能查询（详情、版本、文件）、下载（打包 + 可见性检查，PUBLIC 匿名可下载）
- 标签管理、搜索（PostgreSQL Full-Text，匿名搜索限 PUBLIC）
- 异步事件基础设施
- Rate Limiting 升级（应用层精细限流：按用户/端点分类，基于 Redis 滑动窗口）

### 前端

- 首页、搜索页、命名空间主页（匿名可访问）
- 技能详情页、版本历史页（PUBLIC 匿名可浏览/下载）
- 发布页、我的技能列表
- 命名空间管理页

### 验收

完整发布 → 存储 → 审核 → 查询 → 下载链路可用，搜索可用，命名空间隔离生效，匿名用户可浏览/下载公共技能

## Phase 3：审核流程 + 评分收藏 + CLI API / ClawHub 兼容层

### 后端

- 审核流程（提交 → 审核 → 发布，含乐观锁）
- 团队技能提升到全局（promotion_request 流程）
- 评分 + 收藏 + 计数器（原子更新）
- OAuth Device Flow（device code、授权确认、轮询换取 Bearer Token）
- CLI API（whoami、publish、resolve、check）
- ClawHub CLI 协议兼容层（`/api/compat/v1` 端点：search、resolve、download、publish、whoami）
- 兼容层 canonical slug 映射（`--` 双连字符规则）
- `/.well-known/clawhub.json` 发现端点
- 协议适配器与兼容性测试（针对 ClawHub CLI 的真实请求/响应样例）
- 审计日志（同步落库）、幂等去重（idempotency_record + Redis）

### 前端

- 审核中心、命名空间审核页、提升审核页
- 评分组件 + 收藏按钮（匿名用户点击提示登录）、我的收藏页
- Token 管理页
- 管理后台（用户管理、角色分配、准入审批、封禁/解封）
- 前端 API 层收口：统一迁移到 OpenAPI 生成类型 + `openapi-fetch` 客户端，淘汰业务页面里的手写 `fetch`
- 建立 API 变更后的前端同步机制：后端 OpenAPI 更新后执行 `generate-api`，禁止生成类型与真实返回长期漂移

### 验收

团队空间自治审核与全局空间平台审核生效，skillhub CLI Device Flow 可用，ClawHub CLI 通过兼容层可完成核心 registry 操作，评分收藏可用

## Phase 4：运维增强 + 打磨 + 开源就绪

### 后端

- 本地认证体系（用户名密码注册/登录 + BCrypt + 密码策略 + 账号锁定）
- 多账号合并流程（发起 → 验证 → 确认 → 数据迁移）
- 技能治理（隐藏/恢复 + 版本撤回 YANKED）
- 审计日志查询 API（多条件筛选 + 分页）
- Prometheus 指标暴露（Actuator + Micrometer 自定义业务指标）
- 性能优化（数据库索引 + S3 预签名 URL + 连接池调优）
- 安全加固（Session Cookie 安全 + 安全响应头 + XSS 防护）

### 前端

- 注册页、登录页扩展（用户名密码 + OAuth 双模式）
- 密码修改页、账号合并页
- 审计日志查询页
- 技能隐藏/撤回操作（管理员可见）
- 前端代码分割（TanStack Router lazy routes）
- rehype-sanitize XSS 防护
- OpenAPI SDK 工程化：生成文件纳入 CI 校验，避免新增接口回退到手写调用

### 部署 & 开源

- Docker 一键启动（多阶段 Dockerfile + docker-compose + 种子数据）
- K8s 基础部署清单（Deployment + Service + Ingress）
- README.md、CONTRIBUTING.md、GitHub Issue/PR 模板
- LICENSE（Apache 2.0）、CODE_OF_CONDUCT.md

### 验收

本地认证可用，多账号合并可用，技能隐藏/撤回可用，审计日志可查询，Prometheus 指标可拉取，`docker compose up` 一键启动，K8s 清单可部署，开源基础设施齐全

## Phase 5：治理闭环 + 社交

- 评论功能
- 举报/标记机制（用户举报 → 管理员处理 → 隐藏/撤回）
- 自动安全预检（`PrePublishValidator` 从当前 `NoOp` 扩展为真实校验链）
- Webhook/事件通知（发布通知、审核结果通知）
- 后续 OAuth Provider 扩展（GitLab、Google 等）
- 向量搜索第二阶段增强（当前第一阶段仅做搜索增强，不做推荐）

## 主要风险与应对

| 风险 | 应对 |
|------|------|
| GitHub OAuth 回调配置复杂 | 本地用 MockAuthFilter 解耦，OAuth 联调可并行 |
| 审核流程需求变更 | skill_version.status 已预留审核状态 |
| 搜索效果不佳 | SPI 架构允许随时切换实现 |
| 前后端接口频繁变更 | OpenAPI spec 先行，类型自动生成 |
| 新增 OAuth Provider | Spring Security OAuth2 原生多 Provider 支持，只需配置 + 属性映射 |
| ClawHub CLI 协议细节与现有模型不完全一致 | 兼容层使用 `--` 双连字符 canonical slug 映射，独立 Controller 层适配，协议回归测试覆盖 |

## 测试演进策略

- 当前阶段（Phase 2 验证优先）：本地通过 `docker-compose.yml` 启动 PostgreSQL、Redis、MinIO，后端与集成测试直接连接真实依赖，优先验证发布、搜索、下载、限流等基础设施相关链路
- 后续阶段（工程化收口）：逐步把后端集成测试迁移到 Testcontainers，由测试代码按需拉起 PostgreSQL、Redis、MinIO，减少对手工启动本地依赖的要求，并纳入 CI
- 前端阶段性要求：后端 API 契约稳定后，前端必须同步刷新 OpenAPI 生成类型并校验关键页面；统一响应结构变更不允许只改后端不改前端
- 原则：单元测试可继续使用 mock/in-memory 替身，但 Phase 2/3 的核心验收必须保留一组基于真实中间件的集成测试，避免 Redis Lua、对象存储、Flyway、搜索 SQL 等问题被假实现掩盖
