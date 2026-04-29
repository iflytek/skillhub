# 常见问题

本页只保留高频问题。操作步骤、部署流程和功能说明优先链接到专门页面，不在 FAQ 中重复展开。

## Q: SkillHub 和 ClawHub 有什么区别？

A: SkillHub 是企业级自托管方案，强调数据主权、命名空间治理、审核链路和安全扫描。ClawHub 更接近公共注册中心。

## Q: 如何备份数据？

A: 重点备份两部分：

- PostgreSQL 数据库
- 对象存储（MinIO / S3）

如果是生产环境，建议把备份策略纳入部署与运维流程。

## Q: 支持哪些认证方式？

A: 当前主路径包括：

- OAuth2
- 本地账号密码
- 可扩展的企业 SSO 兼容接入

更详细的设计说明见仓库中的认证与 SSO 设计文档。

## Q: 技能包大小有限制吗？

A: 默认限制为 `100MB`。实际限制受后端 multipart 配置和发布策略共同约束。

## Q: 如何配置 HTTPS？

A: 生产环境建议使用 Nginx 或 Traefik 作为反向代理。部署细节参考：

- [Kubernetes 部署](/guide/kubernetes)
- 仓库中的部署文档

## Q: 如何监控 SkillHub？

A: 常见监控入口包括：

- `GET /actuator/health`
- `GET /actuator/metrics`
- 安全扫描服务健康检查
- 审计日志与应用日志

## Q: 支持多租户吗？

A: SkillHub 通过命名空间实现逻辑隔离。每个命名空间有独立成员、权限和技能包。

## Q: 如何升级 SkillHub？

A: 升级建议、运行时参数和回滚前准备见 [快速开始](/quickstart) 与部署文档。升级前建议先备份数据库和对象存储。

## Q: 如何搜索、安装或发布技能包？

A: 统一参考以下页面：

- [快速开始](/quickstart)
- [Skill 发布与版本管理](/guide/skill-publish)
- [Skill 搜索与发现](/guide/skill-discovery)
- [命名空间与团队管理](/guide/namespace)

## Q: 本地开发启动失败怎么办？

A: 优先查看 [快速开始](/quickstart) 中的“本地开发”章节。若仍无法解决，再查看仓库根目录下的开发文档和后端日志。

## Q: 遇到问题怎么办？

A: 常用途径：

- GitHub Issues
- GitHub Discussions
- 仓库根目录 `README.md`
