# Docker Compose 部署与升级

使用已有 Registry 安装技能不需要部署服务。只有需要自托管 Registry 时才执行本页流程。源码开发使用 `make dev-all`；容器回归使用 `make staging`；正式单机交付使用 `compose.release.yml`。集群部署见 [Kubernetes](kubernetes.md)。

## 准备配置

需要 Docker、Docker Compose，以及与目标版本匹配的仓库文件。在仓库根目录执行：

```bash
cp .env.release.example .env.release
```

编辑 `.env.release` 后再启动：

| 配置 | 需要确认 |
| --- | --- |
| `SKILLHUB_VERSION` | 固定已发布版本，避免升级时无意更换镜像；`latest` 跟踪稳定发布，`edge` 跟踪主分支构建 |
| `SKILLHUB_PUBLIC_BASE_URL` | 用户实际访问的地址，生产环境使用 HTTPS |
| `WEB_PORT` / `API_PORT` | 默认分别为 80 / 8080，确认端口可用 |
| `SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET` | 自行生成至少 32 字符的随机值；默认占位值会阻止启动 |
| `POSTGRES_PASSWORD` | 设置自己的数据库密码 |
| `BOOTSTRAP_ADMIN_ENABLED` / `BOOTSTRAP_ADMIN_PASSWORD` | 是否初始化首个管理员；启用时必须更换示例密码 |
| 存储配置 | 默认及可选配置以模板为准；外部存储按[对象存储手册](https://github.com/iflytek/skillhub/blob/main/docs/object-storage.md)配置 |

`.env.release` 包含敏感信息，不要提交或分享。已有数据库的密码不会因修改环境变量自动轮换。发布环境不要启用 `local` profile 的 Mock 登录。

## 启动与确认

```bash
make validate-release-config
docker compose --env-file .env.release -f compose.release.yml pull
docker compose --env-file .env.release -f compose.release.yml up -d
docker compose --env-file .env.release -f compose.release.yml ps
docker compose --env-file .env.release -f compose.release.yml logs --tail=100 server
```

校验通过后再启动。首次启动或升级时，后端会执行 Flyway 数据库迁移。访问配置的公开地址，使用自己配置的管理员凭证登录，并验证搜索、下载以及一次符合权限的发布/审核流程；容器健康不代表这些业务路径已通过。

Suite 审核写入在单机 release Compose 中默认开启；Suite Bundle 确认默认关闭。需要批量导入时，按 [Bundle 导入](suite-bundle.md#部署开关)启用对应开关。滚动升级新旧后端共存时，另按[套装部署顺序](https://github.com/iflytek/skillhub/blob/main/docs/25-skill-suites.md#部署顺序)操作。

## 升级、停止与排障

升级前保存当前镜像版本、配置，并备份数据库和技能对象存储。修改 `SKILLHUB_VERSION` 为目标发布版本，重新执行校验、`pull`、`up -d` 和业务验证。回退镜像不等于回退数据库；先检查目标迁移的兼容性。

```bash
# 停止本实例；不要附加 -v，除非明确要删除数据卷
docker compose --env-file .env.release -f compose.release.yml down
```

启动失败先查看 `ps` 和服务日志，核对数据库、Redis、存储和公开地址配置。提交问题时提供镜像版本、脱敏日志和请求 ID，不提供 `.env.release` 或凭证。更多 Redis、反向代理、采集服务和迁移配置见[运维手册](https://github.com/iflytek/skillhub/blob/main/docs/09-deployment.md)。

## 配置检查与上线验收

发布模板和 Compose 需来自同一版本。普通 `.env.release` 是 Compose 的替换值来源，只有清单引用的变量才会进入进程。额外配置应通过自己的 override 显式传入，不能只在环境文件追加任意属性。重建服务后检查行为；`restart` 不更新容器环境。

| 检查层 | 操作与通过标准 |
| --- | --- |
| 配置 | `make validate-release-config` 无错误；警告逐项确认，不公开包含配置值的输出 |
| 依赖和进程 | `ps` 中相关服务运行，`server` 日志没有持续连接失败或迁移失败 |
| 对外入口 | 浏览器从最终域名访问、登录；CLI 对同一 Registry 执行 `whoami` |
| 业务与持久化 | 发布测试技能，跟踪最终状态，安装固定版本并检查 `SKILL.md`；重启后资源和登录能力仍可恢复 |
| 运行依赖 | 技能包存储可读写；扫描服务可达，真实扫描任务能完成，而非仅容器健康 |

生产域名走 HTTPS 时设置 `SESSION_COOKIE_SECURE=true`。子路径部署需同时配置 `SKILLHUB_WEB_BASE_PATH=/skillhub/`、同源 `SKILLHUB_WEB_API_BASE_URL=/skillhub` 和带 `/skillhub` 的公开地址。只设置公网 URL 不会自动配置 TLS 证书、代理或 DNS。

| 现象 | 首先检查 |
| --- | --- |
| 校验器拒绝启动 | 数据库/管理员密码是否仍为示例，匿名下载密钥长度，存储 provider 和 S3 必需项 |
| 页面正常但 API 502 | `server` 日志、数据库迁移、`SKILLHUB_API_UPSTREAM` 和容器网络 |
| 登录回调或设备码地址错误 | `SKILLHUB_PUBLIC_BASE_URL` 是否与真实域名和子路径一致 |
| 配置保存后没变化 | 是否重建正确 Compose 项目，是否保留原 override，变量是否实际透传 |
| 上传进入扫描但不结束 | 扫描器日志、配置、后端到扫描器网络；不要将失败视为审核通过 |

备份至少覆盖 PostgreSQL、技能与头像存储、私有运行配置及加密密钥。恢复时使用匹配的应用版本和配置，先在隔离环境验证恢复后的登录与下载；不要以删除卷或清空数据库作为升级失败的默认处理。
