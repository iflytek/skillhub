---
name: tester
description: SkillHub 测试 agent。编写前端 Vitest 单测、后端 Spring Boot 单测，并基于 `web/e2e` 执行 Playwright 真实请求 E2E，把守质量门禁。
---

# SkillHub 测试规范

## 前端单元测试（Vitest）

### 规则

- 文件名 kebab-case，与源文件**同目录**（`overview-collapse.test.ts` 和 `overview-collapse.ts` 同级）
- 使用 `describe / it / expect` 结构
- 覆盖：正常路径、边界值、错误场景

### 示例

```typescript
import { describe, it, expect } from 'vitest'
import { getOverviewCollapseMaxHeight, shouldCollapseOverview } from './overview-collapse'

describe('overview collapse helpers', () => {
  it('uses fixed max height on desktop', () => {
    expect(getOverviewCollapseMaxHeight(1280, 900)).toBe(720)
  })

  it('uses viewport ratio on mobile', () => {
    expect(getOverviewCollapseMaxHeight(375, 900)).toBe(Math.round(900 * 0.6))
  })
})
```

### 运行

```bash
# 单个测试
cd web && pnpm exec vitest run src/features/skill/overview-collapse.test.ts

# 全部测试
make test-frontend
```

## 后端单元测试（Spring Boot）

### Controller 层模式

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MyControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean MyAppService myAppService;

    @Test
    void shouldReturnData() throws Exception {
        when(myAppService.getData()).thenReturn(someData);
        mockMvc.perform(get("/api/v1/resource")
                .with(authentication(mockAuth())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data").exists());
    }
}
```

### 运行

```bash
# 全部测试
make test-backend-app

# 单个测试
cd server && JDK_JAVA_OPTIONS="-XX:+EnableDynamicAgentLoading" \
  ./mvnw -pl skillhub-app -am test -Dtest=MyControllerTest
```

## E2E 验证（Playwright + `web/e2e`）

**前提**：确认本地服务已启动（`make dev-all`），访问 `http://localhost:3000` 可用。

### 职责要求

- 不再使用 Playwright MCP 做页面交互验证
- 统一使用仓库内 `web/e2e/**/*.spec.ts` 进行 E2E 验证
- 新增/修改 E2E 用例与 helper 时，必须遵循 [`docs/e2e.md`](../../docs/e2e.md) 的规范（真实请求、禁止 API mock、选择器优先级、会话与数据构建约定）

### 运行

```bash
# 全量 E2E
make test-e2e-frontend

# Smoke
make test-e2e-smoke-frontend

# 单个 spec
cd web && pnpm exec playwright test e2e/<feature>.spec.ts
```

## 质量门禁（完成测试任务后必须全部通过）

| 检查 | 命令 | 通过条件 |
|---|---|---|
| TypeScript 类型检查 | `make typecheck-web` | 0 errors |
| ESLint | `make lint-web` | 0 errors，0 warnings |
| 前端单测 | `make test-frontend` | 所有 Vitest 测试通过 |
| 后端单测 | `make test-backend-app` | 有后端变更时运行：`git diff --name-only HEAD -- server/` 有输出才执行 |

## 禁止行为

- 为让测试通过而 mock 掉真实业务逻辑（Controller 测试可以 mock AppService，但 AppService 本身的逻辑不能 mock 掉）
- 跳过质量门禁直接声明完成
- 使用 Playwright MCP 替代 `web/e2e` 进行 E2E 验证
