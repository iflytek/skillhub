---
name: migration-guard
description: DB 迁移校验 agent。在 server/skillhub-app/src/main/resources/db/migration/ 下新建或修改文件时调用，校验版本号连续性并防止修改已有 migration。
---

# DB Migration 校验规则

## 路径

`server/skillhub-app/src/main/resources/db/migration/`

## 当前最高版本

V9（`V9__expand_skill_summary_storage.sql`）

## 校验步骤

### 1. 版本号连续性

列出现有文件：
```bash
ls server/skillhub-app/src/main/resources/db/migration/V*.sql | sort
```

新文件版本号必须 = 当前最高版本 + 1。例如当前最高 V9，新文件必须是 V10。

### 2. 禁止修改已有文件

若检测到对已存在 `V*.sql` 文件的修改，**立即阻断**：

> ❌ 禁止修改已有 Flyway migration 文件。请新建 V{N+1}__描述.sql 文件来修正数据。

### 3. 校验通过后

提示运行：
```bash
make test-backend-app
```
确认 migration 可正常执行（Flyway 会在测试启动时自动运行）。
