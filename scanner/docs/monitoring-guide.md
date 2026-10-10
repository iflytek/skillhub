# Scanner 运维监控指南

## 概述

本文档提供 Scanner 服务的运维监控指南，包括关键指标、告警规则和故障排查方法。

## 关键监控指标

### 1. 版本状态监控

#### SCANNING 状态的版本数量

```sql
-- 查询当前处于 SCANNING 状态的版本数量
SELECT COUNT(*) as scanning_count
FROM skill_versions
WHERE status = 'SCANNING';
```

**告警阈值**：
- ⚠️ 警告：> 10 个版本
- 🔴 严重：> 50 个版本

#### SCAN_FAILED 状态的版本数量

```sql
-- 查询最近 1 小时内扫描失败的版本数量
SELECT COUNT(*) as failed_count
FROM skill_versions
WHERE status = 'SCAN_FAILED'
  AND updated_at > NOW() - INTERVAL 1 HOUR;
```

**告警阈值**：
- ⚠️ 警告：> 5 个版本/小时
- 🔴 严重：> 20 个版本/小时

#### 卡死的扫描任务

```sql
-- 查找卡在 SCANNING 状态超过 10 分钟的版本
SELECT id, skill_id, version, status, created_at, updated_at
FROM skill_versions
WHERE status = 'SCANNING'
  AND updated_at < NOW() - INTERVAL 10 MINUTE
ORDER BY updated_at ASC;
```

**告警阈值**：
- 🔴 严重：任何超过 10 分钟的 SCANNING 状态

### 2. Redis Stream 监控

#### 消息堆积情况

```bash
# 查看 scan 队列的消息堆积情况
redis-cli XPENDING skillhub:scan:requests skillhub-scanners

# 查看队列长度
redis-cli XLEN skillhub:scan:requests
```

**告警阈值**：
- ⚠️ 警告：队列长度 > 100
- 🔴 严重：队列长度 > 500

#### 消费者状态

```bash
# 查看消费者组信息
redis-cli XINFO GROUPS skillhub:scan:requests

# 查看消费者信息
redis-cli XINFO CONSUMERS skillhub:scan:requests skillhub-scanners
```

**检查项**：
- 消费者是否在线
- 是否有长时间未确认的消息

### 3. 临时文件监控

#### 磁盘空间使用

```bash
# 检查临时文件目录大小
du -sh /tmp/skillhub-scans/

# 检查 /tmp 分区剩余空间
df -h /tmp
```

**告警阈值**：
- ⚠️ 警告：/tmp 剩余空间 < 5GB
- 🔴 严重：/tmp 剩余空间 < 1GB

#### 孤儿文件清理

```bash
# 查找超过 1 小时的临时文件（可能是孤儿文件）
find /tmp/skillhub-scans/ -type f -mmin +60

# 清理孤儿文件（谨慎操作）
find /tmp/skillhub-scans/ -type f -mmin +60 -delete
```

### 4. Scanner 服务健康检查

#### HTTP 健康检查

```bash
# 检查 Scanner 服务是否可用
curl -f http://localhost:8000/health || echo "Scanner service is down"

# 检查响应时间
time curl -s http://localhost:8000/health > /dev/null
```

**告警阈值**：
- ⚠️ 警告：响应时间 > 5 秒
- 🔴 严重：服务不可用

#### 扫描成功率

```sql
-- 计算最近 1 小时的扫描成功率
SELECT
    COUNT(CASE WHEN status = 'PENDING_REVIEW' THEN 1 END) as success_count,
    COUNT(CASE WHEN status = 'SCAN_FAILED' THEN 1 END) as failed_count,
    ROUND(
        COUNT(CASE WHEN status = 'PENDING_REVIEW' THEN 1 END) * 100.0 /
        NULLIF(COUNT(*), 0),
        2
    ) as success_rate
FROM skill_versions
WHERE updated_at > NOW() - INTERVAL 1 HOUR
  AND status IN ('PENDING_REVIEW', 'SCAN_FAILED');
```

**告警阈值**：
- ⚠️ 警告：成功率 < 80%
- 🔴 严重：成功率 < 50%

## Prometheus 告警规则示例

```yaml
groups:
  - name: scanner_alerts
    interval: 30s
    rules:
      # Scanner 服务不可用
      - alert: ScannerServiceDown
        expr: up{job="skill-scanner"} == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Scanner service is down"
          description: "Scanner service has been down for more than 2 minutes"

      # 扫描失败率过高
      - alert: ScannerHighFailureRate
        expr: rate(scanner_failures_total[5m]) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Scanner failure rate > 50%"
          description: "Scanner failure rate is {{ $value | humanizePercentage }} in the last 5 minutes"

      # 版本卡在 SCANNING 状态
      - alert: ScanStuckTooLong
        expr: skillhub_scanning_versions{status="SCANNING"} > 0
        for: 10m
        labels:
          severity: critical
        annotations:
          summary: "Versions stuck in SCANNING state"
          description: "{{ $value }} versions have been in SCANNING state for more than 10 minutes"

      # 临时文件磁盘空间不足
      - alert: TempFilesDiskUsage
        expr: node_filesystem_avail_bytes{mountpoint="/tmp"} < 1e9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Temp files disk usage high"
          description: "Only {{ $value | humanize1024 }}B available in /tmp"

      # Redis Stream 消息堆积
      - alert: ScanQueueBacklog
        expr: redis_stream_length{stream="skillhub:scan:requests"} > 100
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Scan queue backlog"
          description: "{{ $value }} messages pending in scan queue"
```

## 故障排查手册

### 问题 1：版本卡在 SCANNING 状态

**症状**：
- 用户反馈技能包一直在扫描中
- 数据库中版本状态为 SCANNING 超过 10 分钟

**排查步骤**：

1. 检查 Redis Stream 消费者是否在线
```bash
redis-cli XINFO CONSUMERS skillhub:scan:requests skillhub-scanners
```

2. 检查是否有对应的消息
```bash
redis-cli XPENDING skillhub:scan:requests skillhub-scanners
```

3. 检查应用日志
```bash
kubectl logs -l app=skillhub-backend --tail=100 | grep "versionId=<VERSION_ID>"
```

**解决方案**：

如果确认消息丢失或消费者异常，手动修复版本状态：

```sql
-- 将卡死的版本标记为 SCAN_FAILED
UPDATE skill_versions
SET status = 'SCAN_FAILED', updated_at = NOW()
WHERE id = <VERSION_ID> AND status = 'SCANNING';

-- 创建人工审核任务
INSERT INTO review_tasks (skill_version_id, namespace_id, requester_id, created_at)
SELECT id, (SELECT namespace_id FROM skills WHERE id = skill_id), created_by, NOW()
FROM skill_versions
WHERE id = <VERSION_ID>;
```

### 问题 2：Scanner 服务不可用

**症状**：
- 所有扫描任务失败
- HTTP 连接超时

**排查步骤**：

1. 检查 Scanner 服务状态
```bash
# Docker 环境
docker ps | grep scanner

# Kubernetes 环境
kubectl get pods -l app=skill-scanner
```

2. 检查 Scanner 日志
```bash
# Docker 环境
docker logs skill-scanner --tail=100

# Kubernetes 环境
kubectl logs -l app=skill-scanner --tail=100
```

3. 检查网络连通性
```bash
curl -v http://localhost:8000/health
```

**解决方案**：

- 重启 Scanner 服务
- 检查配置是否正确（API key、base URL 等）
- 检查资源限制（CPU、内存）

### 问题 3：临时文件占满磁盘

**症状**：
- /tmp 分区空间不足
- 扫描任务失败，日志显示 "No space left on device"

**排查步骤**：

1. 检查磁盘使用情况
```bash
df -h /tmp
du -sh /tmp/skillhub-scans/
```

2. 查找大文件
```bash
find /tmp/skillhub-scans/ -type f -size +100M -exec ls -lh {} \;
```

3. 查找孤儿文件
```bash
find /tmp/skillhub-scans/ -type f -mmin +60
```

**解决方案**：

```bash
# 清理超过 1 小时的临时文件
find /tmp/skillhub-scans/ -type f -mmin +60 -delete

# 清理空目录
find /tmp/skillhub-scans/ -type d -empty -delete
```

### 问题 4：Redis Stream 消息堆积

**症状**：
- 扫描任务延迟严重
- Redis Stream 队列长度持续增长

**排查步骤**：

1. 检查队列长度
```bash
redis-cli XLEN skillhub:scan:requests
```

2. 检查消费者数量和状态
```bash
redis-cli XINFO CONSUMERS skillhub:scan:requests skillhub-scanners
```

3. 检查应用实例数量
```bash
kubectl get pods -l app=skillhub-backend
```

**解决方案**：

- 增加应用实例数量（水平扩展）
- 检查 Scanner 服务性能
- 临时禁用扫描功能，清空队列后再启用

## 日常巡检清单

### 每日检查

- [ ] 检查 SCANNING 状态的版本数量
- [ ] 检查 SCAN_FAILED 状态的版本数量
- [ ] 检查 Scanner 服务健康状态
- [ ] 检查 /tmp 磁盘空间使用情况

### 每周检查

- [ ] 检查扫描成功率趋势
- [ ] 检查 Redis Stream 消息堆积情况
- [ ] 清理孤儿临时文件
- [ ] 检查告警规则是否触发

### 每月检查

- [ ] 审查扫描失败的原因分布
- [ ] 评估 Scanner 服务性能
- [ ] 优化告警阈值
- [ ] 更新运维文档

## 相关文档

- [故障影响分析](./failure-impact-analysis.md)
- [改进建议](./improvement-recommendations.md)
- [配置说明](./configuration.md)
