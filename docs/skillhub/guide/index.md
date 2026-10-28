# 文档导航

按要完成的任务选择入口。第一次使用，从[快速开始](../quickstart.md)安装一个技能；不需要先阅读部署或开发文档。

## 开始使用

- [账号与访问令牌](./account-access.md)：网页登录、CLI 授权、Token 到期和密码找回。
- [项目简介](../introduction.md)：了解注册中心解决什么问题。
- [快速开始](../quickstart.md)：搜索、安装，在 Agent 中使用。
- [常见问题](../faq.md)：排查登录、安装和配置问题。

## 使用技能

| 你要做什么 | 阅读 |
| --- | --- |
| 找到适合任务的技能，查看版本和权限 | [搜索与安装技能](./skill-discovery.md) |
| 一次安装一组固定版本技能，从入口技能开始使用 | [管理技能套装](./skill-suites.md) |
| 选择 Agent 和安装范围，更新、卸载或同步工作区 | [CLI 使用参考](./cli.md) |
| 收藏、评价技能并查看通知 | [收藏、评分与通知](./social.md) |

## 创作与发布

先准备 `SKILL.md` 和所需文件，再提交发布。提交成功后仍需跟踪扫描、审核及最终版本状态。

- [发布与更新技能](./skill-publish.md)：包结构、元数据、上传和版本管理。
- [通过 Bundle 导入技能套装](./suite-bundle.md)：作者上传 Bundle，预览成员差异后确认。安装现有套装请用上方的使用指南。

## 团队与治理

- [管理命名空间](./namespace.md)：成员、角色和资源可见范围。
- [审核技能与套装](./review.md)：处理技能与套装申请。
- [安全扫描与报告](./scanner.md)：扫描服务接入及结果解读。

## 部署与运维

仅在需要自己运行注册中心时阅读。先选择部署方式，再配置所需的可选能力。

- [Docker 部署](./deployment.md)：安装、配置、升级和备份。
- [Kubernetes 部署](./kubernetes.md)：集群和 Helm 部署。
- [技能头像与审核](./skill-avatars.md)：管理员配置图片审核服务。

## 开发与集成

- [Runtime 集成](./runtime-integration.md)：外部运行时使用注册中心的接口契约。
- [源码开发手册](https://github.com/iflytek/skillhub/blob/main/docs/dev-workflow.md)：本地开发环境和仓库检查命令。
