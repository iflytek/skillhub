# 设计文档总览

`design/` 只保留对当前代码仍有解释价值的设计材料，不作为杂项文档堆放区。

## 目录规则

- 根目录只保留本索引
- `overview/`：项目级分析、依赖盘点、兼容性分析
- `governance/`：文档治理等元规则
- `auth/`：认证扩展与私有 SSO 兼容设计
- `runtime/`：已实现运行时专题说明
- `implemented/`：已实现 PRD 归档
- `archive/`：历史设计材料

## 当前目录

### `overview/`

- [project-deep-analysis.md](./overview/project-deep-analysis.md)
- [external-dependencies.md](./overview/external-dependencies.md)
- [java-and-database-compatibility.md](./overview/java-and-database-compatibility.md)（历史数据库选型分析，文首已标注不代表当前默认运行时）

### `governance/`

- [documentation-governance.md](./governance/documentation-governance.md)

### `auth/`

- [auth-extensibility-and-private-sso.md](./auth/auth-extensibility-and-private-sso.md)
- [private-sso-integration-playbook.md](./auth/private-sso-integration-playbook.md)

### `runtime/`

- [security-scanning-runtime.md](./runtime/security-scanning-runtime.md)
- [password-reset-email-smtp.md](./runtime/password-reset-email-smtp.md)
- [mysql-runtime-and-search-provider-migration.md](./runtime/mysql-runtime-and-search-provider-migration.md)
- [runtime-core-configuration-reference.md](./runtime/runtime-core-configuration-reference.md)
- [production-readiness-assessment-and-hardening-plan.md](./runtime/production-readiness-assessment-and-hardening-plan.md)

### `implemented/`

- [README.md](./implemented/README.md)

### `archive/`

- 历史设计与旧方案归档目录

## 维护原则

- 新增设计文档时，先判断它属于哪个主题目录
- 如果某个专题已经实现完成，优先考虑放到 `implemented/` 或将关键结论折叠进现有设计文档
- 如果文档只是一次性中间产物，不进入 `design/`
- 如果某份设计文档保留历史背景价值但不再代表当前默认路径，必须在文首明确标注“历史材料”或“状态更新”
