# PRD: Java Unit Test Line Coverage to 100%

> This document has been superseded by:
> [uass-session-auth-and-java-coverage-v1.0-prd.md](./uass-session-auth-and-java-coverage-v1.0-prd.md)
>
> Use the merged PRD as the active execution document for the shared feature branch.

## 1. Introduction

当前仓库 Java 单元测试的 JaCoCo 行覆盖率未达到 100%。基于 `server/*/target/site/jacoco/jacoco.csv` 的汇总结果，当前总体 line coverage 为 **73.73%**，仍有大量生产类未被单元测试完全覆盖。

本 PRD 用于定义一轮系统性的测试补齐工作，使当前 Java 生产代码的行覆盖率达到 100%，并建立后续不回退的验证机制。

## 2. Goals

- 将当前 Java 生产代码的 JaCoCo **line coverage 提升到 100%**。
- 明确所有未达 100% 的类，并按模块拆解整改范围。
- 优先补齐高风险业务路径，而不是只追求机械覆盖率。
- 在 CI 或本地验证链路中加入覆盖率门禁，防止回退。

## 3. Current Coverage Baseline

| Module | Line Missed | Line Covered | Line Coverage |
|------|------:|------:|------:|
| `skillhub-app` | 1219 | 3660 | 75.02% |
| `skillhub-auth` | 513 | 873 | 62.99% |
| `skillhub-domain` | 765 | 2543 | 76.87% |
| `skillhub-infra` | 173 | 122 | 41.36% |
| `skillhub-notification` | 17 | 190 | 91.79% |
| `skillhub-search` | 90 | 488 | 84.43% |
| `skillhub-storage` | 78 | 135 | 63.38% |
| **Total** | **2855** | **8011** | **73.73%** |

完整未达 100% 清单见：`docs/prds/java-unit-line-coverage-inventory.md`

## 4. User Stories

### US-001: 建立覆盖率基线与分层整改范围
**Description:** 作为维护者，我希望先拿到完整且稳定的覆盖率基线，以便后续补测试时有明确范围和验收口径。

**Acceptance Criteria:**
- [ ] 使用 JaCoCo 生成所有 Java 模块的 line coverage 报告。
- [ ] 输出模块级 coverage 汇总表。
- [ ] 输出所有 line coverage 未达 100% 的类清单。
- [ ] 将清单按模块分组，便于分批实施。

### US-002: 优先补齐核心业务服务测试
**Description:** 作为维护者，我希望先补齐核心业务服务和治理链路测试，以便优先覆盖最容易引发行为回归的代码路径。

**Acceptance Criteria:**
- [ ] 优先覆盖 `skillhub-domain` 与 `skillhub-app` 中的核心 service。
- [ ] 对发布、审核、推广、命名空间治理、认证等主链路补齐正常路径与异常路径。
- [ ] 对已有日志驱动的异常分支补齐断言，而不是仅依赖“代码跑过”。
- [ ] `./mvnw -q test` 通过。

### US-003: 补齐配置、DTO、适配器和低覆盖辅助类
**Description:** 作为维护者，我希望把剩余的配置类、DTO、adapter、repository helper 等统一补齐，以便最终把 line coverage 拉到 100%。

**Acceptance Criteria:**
- [ ] 为配置类和简单 DTO 增加最小但有效的覆盖测试。
- [ ] 为 adapter / facade / controller helper 增加边界条件覆盖。
- [ ] 为 repository/query helper 类补齐空结果、边界分页、异常输入等场景。
- [ ] 全部生产类的 JaCoCo line missed 归零。

### US-004: 建立覆盖率门禁
**Description:** 作为维护者，我希望把 100% 行覆盖率变成可验证门禁，以便后续修改不会让覆盖率悄悄回退。

**Acceptance Criteria:**
- [ ] 在 Maven 构建中增加 JaCoCo 校验规则或等价门禁。
- [ ] 校验口径明确为 Java 生产代码 line coverage 100%。
- [ ] 本地开发文档说明如何执行覆盖率检查。
- [ ] 新增或修改代码后，未满足门禁时构建明确失败。

## 5. Functional Requirements

- FR-1: 系统必须能够为 `server` 下所有 Java 模块生成 JaCoCo line coverage 报告。
- FR-2: 系统必须能够识别所有 line coverage 未达到 100% 的生产类。
- FR-3: 整改计划必须按模块拆分，而不是一次性无差别补测。
- FR-4: 核心业务链路必须优先补齐，包括认证、命名空间、发布、审核、推广、搜索、通知和存储关键路径。
- FR-5: 最终构建必须对 line coverage 100% 建立自动校验。

## 6. Non-Goals

- 不要求将前端 TypeScript 覆盖率提升到 100%。
- 不要求在本轮中补齐 E2E 覆盖率门禁。
- 不以“删除生产代码”作为达到 100% 的主要手段。
- 不把日志输出本身当作测试通过的替代物。

## 7. Design Considerations

- `skillhub-app` 和 `skillhub-domain` 是当前最大缺口，应作为第一优先级。
- `skillhub-infra` 覆盖率极低，但类数量相对少，适合单独清扫。
- 大量 0% DTO / record / config 类需要明确策略：
  - 如果属于真正的业务对象，应加覆盖。
  - 如果属于纯数据载体，测试应保持轻量，不制造无意义复杂度。

## 8. Technical Considerations

- 建议统一使用 JaCoCo CSV/XML 作为覆盖率事实来源。
- 对使用 `Clock`、事件发布、事务回调、异常补偿的类，需要显式断言分支结果。
- 对 Java 17 环境，测试代码不能继续依赖 Java 21 的 `List.getFirst()` / `getLast()` 一类 API。
- 对 controller / config 类，优先采用已有单元测试模式，不强推全量 SpringBoot 集成测试。

## 9. Priority Hotspots

以下类按当前行遗漏量与业务重要性优先处理：

| Module | Class | Missed Lines | Line Coverage |
|------|------|------:|------:|
| `skillhub-app` | `com.iflytek.skillhub.repository.JpaGovernanceQueryRepository` | 12 | 92.59% |
| `skillhub-app` | `com.iflytek.skillhub.compat.ClawHubCompatAppService` | 86 | 50.29% |
| `skillhub-app` | `com.iflytek.skillhub.compat.ClawHubRegistryFacade` | 53 | 36.90% |
| `skillhub-app` | `com.iflytek.skillhub.config.SkillScannerConfig` | 43 | 6.52% |
| `skillhub-app` | `com.iflytek.skillhub.security.AuthFailureThrottleService` | 84 | 6.67% |
| `skillhub-app` | `com.iflytek.skillhub.service.ReviewPortalAppService` | 32 | 68.93% |
| `skillhub-app` | `com.iflytek.skillhub.service.SkillLifecycleAppService` | 29 | 70.71% |
| `skillhub-domain` | `com.iflytek.skillhub.domain.skill.service.SkillQueryService` | 98 | 69.38% |
| `skillhub-domain` | `com.iflytek.skillhub.domain.review.ReviewService` | 26 | 84.43% |
| `skillhub-domain` | `com.iflytek.skillhub.domain.review.PromotionService` | 32 | 81.07% |
| `skillhub-infra` | `com.iflytek.skillhub.infra.http.WebClientHttpClient` | 49 | 0.00% |
| `skillhub-storage` | `com.iflytek.skillhub.storage.S3StorageService` | 62 | 52.31% |

## 10. Success Metrics

- JaCoCo Java production code line coverage = **100.00%**。
- `line_missed > 0` 的生产类数量 = **0**。
- `./mvnw -q test` 与覆盖率校验命令稳定通过。
- 新增门禁后，再次引入未覆盖行时构建失败。

## 11. Open Questions

- 是否需要对纯 DTO / record / enum 类型单独设豁免，还是严格要求全部 100%？
- 是否要把 generated / synthetic inner classes 排除在门禁之外？
- 覆盖率门禁应放在父 POM，还是仅对具体模块启用？
