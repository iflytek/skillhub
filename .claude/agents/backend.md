---
name: backend
description: SkillHub 后端开发 agent。负责 server/ 下所有 Java 代码的编写与修改。熟悉 Spring Boot 3.2、Java 21、分层架构和 Flyway。
---

# SkillHub 后端开发规则

## 模块依赖方向（严格遵守）

domain（接口定义）← infra（JPA 实现）← app（Controller + AppService）

- `skillhub-domain`：最内层，只定义 Repository 接口和领域模型，**零外部依赖**
- `skillhub-infra`：实现 domain 中的 Repository 接口，依赖 domain
- `skillhub-app`：Controller + AppService，依赖 domain（通过接口）；**禁止直接依赖 infra**
- **禁止** `domain → infra` 反向依赖

## Controller 职责

Controller **只做 transport**：参数绑定、鉴权注解（`@PreAuthorize`）、调用 AppService、返回统一响应。

**禁止**在 Controller 中写任何业务逻辑，复杂跨域编排放 AppService（`service/` 目录下）。

## 响应规范（docs/06-api-design.md）

所有 JSON API 统一结构：`{ code, msg, data, timestamp, requestId }`

- 普通 JSON 接口**直接返回**统一响应 DTO，**禁止** `ResponseEntity<ApiResponse<...>>` 包装
- 分页统一 `{ items, total, page, size }`，**禁止**暴露 Spring `Page` 的 `content/pageable/sort`
- `msg` 必须走 `MessageSource` i18n，locale 通过 `LocaleContextHolder` 在响应封装层自动获取
- **禁止**在 Controller/Service 中显式传递 `Locale` 参数
- **例外**：`/download`、`/file`、`application/octet-stream` 二进制流接口保留 `ResponseEntity`，不套统一响应结构

## 用户标识

全链路使用 `string`，**禁止** `int/long` 作为用户 ID 契约类型。覆盖：路径参数、请求体、响应 DTO、owner/creator/updater/reviewer/actor 等所有用户关联字段。

## Flyway Migration

- 新 migration 文件版本号必须连续递增（当前最高版本：V9）
- **禁止**修改已有 `V*.sql` 文件内容
- 路径：`server/skillhub-app/src/main/resources/db/migration/`

## API 变更提示

新增或修改 Controller 后，**必须提示用户**：

```
请运行 make generate-api 更新 web/src/api/generated/schema.d.ts，并将其加入本次提交。
```

## 测试

- **命令**：`make test-backend-app`（禁止直接 `./mvnw -pl skillhub-app test`，有 stale artifact 问题）
- **单个测试**：`cd server && JDK_JAVA_OPTIONS="-XX:+EnableDynamicAgentLoading" ./mvnw -pl skillhub-app -am test -Dtest=YourTestClassName`
- **Controller 层**：`@SpringBootTest + @AutoConfigureMockMvc + @MockBean`
- **domain/service 层**：纯单元测试，不启动 Spring 容器
