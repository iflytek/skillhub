# 部署与运行规范

正文读取与换会话接续遵循[有界读取规则](../workflow/context-reading.md)，按本页触发条件选资料，不批量加载整个目录。

Bootstrap 管理员用于首次启动引导，不作为共享交付凭证；交付检查初始化开关、凭证来源与首次初始化后的访问控制，
配置字段及行为核对[认证设计](../../../docs/03-authentication-design.md)和当前启动实现，不复制默认口令。

## 适用范围

修改容器、Compose、Kubernetes、镜像发布、运行配置、健康检查或运维接入时读取。
这是工程规则入口，不是执行部署的授权；只读分析不启动服务、不发布镜像。

## 通用原则

- 区分开发、回归、交付环境，先识别目标、现有服务和数据卷，避免影响无关环境。
- 配置由环境或现有配置机制注入；示例与真实凭证分离，日志和报告不输出密钥。
- 发布产物应可追溯到版本或提交；配置、数据迁移和回退能力需要分别检查。
- 构建成功、配置校验、服务健康、完整业务验收是不同证据，不能相互替代。
- 运行前读实际脚本与变量来源；不凭文档示例猜测命令参数、默认地址或当前环境状态。

## SkillHub 约定

- 开发入口是 `make dev-all`；正式单机交付参考 `compose.release.yml` 和部署手册。
  开发能力与交付能力分开，交付环境不得误开启本地 mock 认证旁路。
- 发布配置检查入口为 `make validate-release-config`，读取 `.env.release`；缺少配置时报告缺项，
  不为了让检查通过写入真实凭证或输出配置文件内容。校验脚本部分错误可能带出配置值，
  保存和分享输出前先脱敏，不能因命令名含 validate 就认为输出安全。
- 镜像与 Helm 发布行为查 `.github/workflows/publish-images.yml`、`publish-chart.yml`；
  标签、架构与触发条件以当前工作流为准。发布和部署仍需要任务已有的相应授权。
- Kubernetes 配置、存储和 Secret 的完整要求读对应手册，不在此复制参数表。
- 配置变更检查应用绑定、默认值、环境透传和实际生效路径；文件中存在某个键不代表环境已经采用。
- 数据迁移需说明备份、兼容性和回退限制；回退应用镜像不等于回退数据库。

## 条件性深读

| 条件 | 来源 |
|---|---|
| Compose、Profile、镜像标签、配置、上线与迁移 | [部署架构与运维](../../../docs/09-deployment.md) |
| 开发或 Staging 回归环境 | [开发工作流](../../../docs/dev-workflow.md)、[Makefile](../../../Makefile) |
| Kubernetes | [用户部署指南](../../../docs/skillhub/guide/kubernetes.md)、[清单说明](../../../deploy/k8s/README.md) |
| 请求关联、日志、Trace 接入 | [可观测性开发者指南](../../../docs/observability-developer-guide.md) |
| 扫描服务运行与失败语义 | [扫描运行指南](../../../docs/security-scanning.md) |
| 真实部署验证与报告 | [测试与质量](../testing/index.md) |

## 需要核对的边界

部署文档对支持的运行方式有收敛描述，而 Makefile 与开发文档仍保留 `make staging`。
先区分正式交付与回归验证，不据此自行删除 Staging，也不把它宣称为生产部署标准。

验证配置改动时检查配置校验、构建和相关 Smoke；操作实际环境前确认授权与影响范围。
验证报告写明环境、版本、命令及实际观察结果；不能继承其他 worktree 或历史发布的通过结论。

## 配置与发布的验收契约

配置修改应追踪“声明 → 应用绑定/默认值 → Compose 或 Helm 透传 → 进程实际使用”。
区分构建时与运行时变量；仅修改部署清单或模板不能证明浏览器和服务已经读取新值。
缺省、空值、非法值、旧配置兼容按本次变化验证，敏感值只证明是否存在/生效，不打印内容。

发布前为受影响范围回答以下问题，并将操作步骤留在对应运行手册：

| 变化 | 需要明确的兼容与失败行为 | 验收证据 |
|---|---|---|
| 应用或镜像 | 新旧 API/CLI/前端能否共存；镜像与提交对应关系 | 实际运行版本与关键请求结果 |
| 配置或 Secret | 默认值是否改变、是否需重启、旧配置是否仍可用 | 绑定和透传检查，启动后有效行为 |
| 数据迁移 | 旧数据如何升级、失败阻止启动还是可恢复、旧应用是否读得懂新 schema | 隔离库升级测试、迁移记录与升级后业务断言 |
| 异步任务/外部依赖 | 切换期间任务如何继续、重复执行如何处理、超时失败如何观察 | 受影响队列/任务状态和副作用，不只健康接口 |

Flyway 是 schema 变更入口，应用容器启动会执行迁移；具体规则见
[数据库规范](../backend/database-guidelines.md)和[部署手册](../../../docs/09-deployment.md)。
因此首次启动新应用本身可能修改数据库，不能把“先启动看看”当作无副作用预检。
涉及不兼容字段或数据转换时，先明确应用/迁移顺序和兼容窗口；不能只写“回退镜像即可”。
备份应有对应恢复方式和可验证性；恢复数据、补偿副作用、回退应用是不同操作，执行需在已有授权范围内。

## 分层验收与收尾

### 例：修改 Web 子路径配置

本例说明如何落实“配置声明到实际使用”的检查；不是新的部署默认值。
涉及 `SKILLHUB_WEB_BASE_PATH`、同源 `SKILLHUB_WEB_API_BASE_URL` 或公开地址时，联读
[校验脚本](../../../scripts/validate-release-config.sh)、[Compose 透传](../../../compose.release.yml)、
[容器入口](../../../web/docker-entrypoint.d/20-base-path.sh)与[Nginx 模板](../../../web/nginx.conf.template)。

| 代表输入 | 应检查的结果 |
|---|---|
| Web base `/skillhub/`，同源 API base `/skillhub`，公开地址 `https://skillhub.example.com/skillhub` | 路径一致；生成带前缀的静态资源与 Nginx 路由 |
| Web base `/skillhub/`，同源 API base `/other` | 配置校验及运行入口拒绝，不静默使用另一 API 路径 |
| Web base `/api/` 或 `/foo/../bar/` | 拒绝保留前缀或点路径，避免遮蔽服务器路由或路径归一化后失配 |
| 根路径 `/` | 不生成重复子路径 location；静态资源保持根路径 |

不要只改 Compose 加一个变量就判定完成；也不能仅校验字符串末尾相同就接受公开地址的路径。
例如 `/other/skillhub` 与 `/skillhub` 不等价。独立 API 主机的绝对 URL 是允许的例外，不能套同源路径相等规则。

从仓库根目录运行已有代表检查：

```bash
sh scripts/tests/web-base-path-routing-test.sh
bash scripts/tests/validate-release-config-test.sh
```

前者使用临时 HTML 和配置文件检查容器入口生成结果，不启动 Nginx；后者使用假配置，
依赖 Docker Compose 的 `config` 渲染但不启动服务。它们均不是读取真实 `.env.release` 的交付校验，
也不能证明浏览器、OAuth 回调或反向代理实际可用。
需要容器 HTTP 验证时再读取[子路径 Smoke](../../../scripts/tests/web-base-path-nginx-smoke-test.sh)，
核对它的镜像、容器创建与清理范围后执行。

### 证据层次

1. 静态配置和构建：证明配置结构、产物可构建，未证明部署可用。
2. 启动与健康：证明服务就绪、依赖连通，未证明权限和业务闭环。
3. 真实业务 Smoke：检查受影响入口、读写、权限拒绝与结果状态；结合
   [E2E/Smoke 规范](../testing/e2e.md)隔离账号及数据。
4. 记录环境、版本、执行命令、观察结果、未验内容；清理只针对本次创建的资源。

已有运行环境与端口必须先识别。不要按旧工作流建议直接 kill 占用端口的进程；先确认所有者和影响。
`make clean`、`make dev-all-reset`、`make db-reset` 含卷或本地数据清理，不能用作常规失败重试。
`make staging-down` 只清理对应栈且不代表获得删除共享资源的授权。

来源：[发布配置校验](../../../scripts/validate-release-config.sh)、
[Compose 发布清单](../../../compose.release.yml)、[Makefile](../../../Makefile)。
