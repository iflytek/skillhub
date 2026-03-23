# Scanner 接口故障影响分析

## 概述

本文档分析 Cisco skill-scanner API 接口出现故障时对 SkillHub 系统的影响，以及当前的错误处理机制。

## 故障场景分类

### 场景 A：Scanner 服务完全不可用

**现象**：
- HTTP 连接超时
- 服务宕机
- 网络不通

**影响**：
- ❌ **技能包发布流程中断**
- ❌ 技能版本状态卡在 `SCANNING`
- ⚠️ 用户无法继续发布新版本

### 场景 B：Scanner 服务响应慢

**现象**：
- 扫描超时（默认 5 分钟 read timeout）

**影响**：
- ⚠️ 发布流程变慢
- ⚠️ Redis Stream 消息堆积
- ⚠️ 可能触发重试机制

### 场景 C：Scanner 返回错误响应

**现象**：
- HTTP 4xx/5xx 错误

**影响**：
- ❌ 扫描任务失败
- ✅ 自动降级到人工审核流程

## 错误处理机制（当前实现）

### 处理流程

```
发布技能包
    ↓
triggerScan() → 创建 SecurityAudit + 发送 Redis 消息
    ↓
版本状态 → SCANNING
    ↓
ScanTaskConsumer 消费消息
    ↓
调用 securityScanner.scan()
    ↓
┌─────────────────────────────────────┐
│ 如果 Scanner 接口失败：              │
│                                     │
│ 1. 抛出 SecurityScanException       │
│ 2. AbstractStreamConsumer 捕获异常  │
│ 3. 调用 markFailed()                │
│ 4. 版本状态 → SCAN_FAILED           │
│ 5. 自动创建 ReviewTask              │
│ 6. 清理临时文件                      │
│ 7. 重试机制（最多 3 次）             │
└─────────────────────────────────────┘
```

### 关键代码位置

**错误处理逻辑**：
- `ScanTaskConsumer.markFailed()` - `server/skillhub-app/src/main/java/com/iflytek/skillhub/stream/ScanTaskConsumer.java:104-119`

```java
@Override
protected void markFailed(ScanTaskPayload payload, String error) {
    try {
        skillVersionRepository.findById(payload.versionId)
                .filter(version -> version.getStatus() == SkillVersionStatus.SCANNING)
                .ifPresent(version -> {
                    version.setStatus(SkillVersionStatus.SCAN_FAILED);  // ← 标记失败
                    skillVersionRepository.save(version);
                    skillRepository.findById(version.getSkillId())
                            .ifPresent(skill -> reviewTaskRepository.save(
                                    new ReviewTask(payload.versionId, skill.getNamespaceId(), version.getCreatedBy())  // ← 降级到人工审核
                            ));
                });
    } finally {
        cleanupTempPath(payload.skillPath);  // ← 清理临时文件
    }
}
```

## 具体影响总结

| 故障类型 | 用户体验 | 系统行为 | 数据一致性 | 恢复方式 |
|---------|---------|---------|-----------|---------|
| **Scanner 宕机** | ❌ 发布失败，显示扫描失败 | ✅ 自动降级到人工审核 | ✅ 版本状态正确更新 | 自动恢复 |
| **网络超时** | ⚠️ 等待 5 分钟后失败 | ✅ 重试 3 次后降级 | ✅ 状态一致 | 自动重试 |
| **Scanner 返回 5xx** | ❌ 扫描失败 | ✅ 降级到人工审核 | ✅ 状态一致 | 自动恢复 |
| **Scanner 返回 4xx** | ❌ 扫描失败 | ✅ 降级到人工审核 | ✅ 状态一致 | 需修复请求 |
| **Redis Stream 故障** | ❌ 消息丢失 | ❌ 版本卡在 SCANNING | ⚠️ 需手动修复 | 需运维介入 |

## 潜在问题和风险

### 🔴 高风险问题

#### 1. 版本状态卡死

**场景**：如果 Redis Stream 消费者未启动，或消息丢失

**影响**：版本永远停留在 `SCANNING` 状态

**后果**：用户无法继续发布，需要运维手动修复数据库

**排查方法**：
```sql
-- 查找卡在 SCANNING 状态超过 10 分钟的版本
SELECT id, skill_id, version, status, created_at
FROM skill_versions
WHERE status = 'SCANNING'
  AND created_at < NOW() - INTERVAL 10 MINUTE;
```

#### 2. 临时文件泄漏

**场景**：如果 `markFailed()` 或 `markCompleted()` 未执行

**影响**：`/tmp/skillhub-scans/` 目录持续增长

**后果**：磁盘空间耗尽

**排查方法**：
```bash
# 检查临时文件目录大小
du -sh /tmp/skillhub-scans/

# 查找超过 1 小时的临时文件
find /tmp/skillhub-scans/ -type f -mmin +60
```

### 🟡 中风险问题

#### 3. 重试风暴

**场景**：Scanner 持续返回 5xx 错误

**影响**：大量重试请求打满 Scanner 服务

**后果**：Scanner 雪崩，影响其他技能包扫描

#### 4. 审核队列堆积

**场景**：Scanner 长期不可用，所有扫描失败

**影响**：所有技能包都降级到人工审核

**后果**：审核员工作量激增

## 当前实现的优缺点

### ✅ 优点

- 有基本的错误处理和降级机制
- 失败后自动创建人工审核任务
- 有重试机制（最多 3 次）
- 会清理临时文件

### ❌ 不足

- 缺少熔断器，可能导致雪崩
- 缺少超时监控，版本可能卡死
- 缺少健康检查端点
- 缺少详细的错误日志和指标

## 相关文档

- [运维监控指南](./monitoring-guide.md)
- [改进建议](./improvement-recommendations.md)
- [配置说明](./configuration.md)
