# 文档总览

## 当前标准运行时

当前仓库默认/标准运行时请优先按下面理解：

- 数据库：`MySQL 8`
- 标准搜索 provider：`local-file-index`
- 标准生产运行时状态：`Redis`
- 常见本地联调回退：`memory`

对应入口文档：

- [local-runtime-quickstart.md](./local-runtime-quickstart.md)
- [../design/runtime/runtime-core-configuration-reference.md](../design/runtime/runtime-core-configuration-reference.md)
- [../design/runtime/production-readiness-assessment-and-hardening-plan.md](../design/runtime/production-readiness-assessment-and-hardening-plan.md)

历史材料若提到 `local-h2` / `h2-like`，仅表示归档背景，不代表当前标准源码运行方案。

## 当前主文档

以下文档仍属于当前项目的主入口文档：

- [00-product-direction.md](./00-product-direction.md)
- [01-system-architecture.md](./01-system-architecture.md)
- [02-domain-model.md](./02-domain-model.md)
- [03-authentication-design.md](./03-authentication-design.md)
- [04-search-architecture.md](./04-search-architecture.md)
- [05-business-flows.md](./05-business-flows.md)
- [06-api-design.md](./06-api-design.md)
- [07-skill-protocol.md](./07-skill-protocol.md)
- [08-frontend-architecture.md](./08-frontend-architecture.md)
- [09-deployment.md](./09-deployment.md)
- [14-skill-lifecycle.md](./14-skill-lifecycle.md)

## 开发与测试

- [dev-workflow.md](./dev-workflow.md)
- [local-runtime-quickstart.md](./local-runtime-quickstart.md)
- [e2e.md](./e2e.md)
- [mysql-main-path-regression-validation.md](./mysql-main-path-regression-validation.md)

## 需求、设计与实现专题

- [prds/README.md](./prds/README.md)
- [../design/README.md](../design/README.md)
- [../design/overview/project-deep-analysis.md](../design/overview/project-deep-analysis.md)
- [../design/governance/documentation-governance.md](../design/governance/documentation-governance.md)
- [../design/implemented/README.md](../design/implemented/README.md)
- [../design/auth/auth-extensibility-and-private-sso.md](../design/auth/auth-extensibility-and-private-sso.md)
- [../design/auth/private-sso-integration-playbook.md](../design/auth/private-sso-integration-playbook.md)
- [../design/runtime/security-scanning-runtime.md](../design/runtime/security-scanning-runtime.md)
- [../design/runtime/password-reset-email-smtp.md](../design/runtime/password-reset-email-smtp.md)
- [../design/runtime/runtime-core-configuration-reference.md](../design/runtime/runtime-core-configuration-reference.md)
- [../design/runtime/production-readiness-assessment-and-hardening-plan.md](../design/runtime/production-readiness-assessment-and-hardening-plan.md)

## 文档站

- [skillhub/index.md](./skillhub/index.md)
- [skillhub/introduction.md](./skillhub/introduction.md)
- [skillhub/quickstart.md](./skillhub/quickstart.md)

## 历史归档

- [archive/README.md](./archive/README.md)
