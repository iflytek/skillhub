# R1-C 本地验收记录

## 自动化回归

- `skillhub-auth` 身份核心回归：58 tests passed。
- `skillhub-app` 认证、Device Flow、Token、Provider wiring、企业 callback 和过滤器回归：46 tests passed。
- Web 组织登录入口、登录页、API client、analytics manifest：41 tests passed。
- Web `typecheck`、`lint`、`build` passed。
- `openspec validate enterprise-identity-r1c-oidc-login --strict` passed。

## 隔离 Keycloak 浏览器验收

实验室使用一次性 Compose project `skillhub-identity-lab-r1c`，包含 PostgreSQL、Valkey、CoreDNS、SkillHub、Keycloak 和 Caddy；SkillHub 镜像由当前分支本地构建。

已通过：

- OIDC discovery 和授权码跳转；
- 首次预授权绑定、重复登录和 Session Origin；
- 禁用上游用户拒绝；
- 跨组织未预授权拒绝；
- 浏览器退出后 `/api/v1/auth/me` 返回 401；
- 浏览器登录后暂停连接，旧企业会话立即返回 401；
- 日志脱敏检查通过。

验收完成后执行精确 `lab.sh reset oidc-real skillhub-identity-lab-r1c`，删除容器、卷、网络和临时状态。工作区未保留测试账号、Token 或 Cookie。

## 发布门禁

本地默认关闭、试点 allowlist 和日志脱敏已验证。对外试点仍需业务负责人确认试点组织、负责人、观察窗口和停止条件，确认前不勾选 3.3b，也不发布或合并 PR。
