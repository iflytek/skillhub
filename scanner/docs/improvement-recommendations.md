# Scanner 系统改进建议

## 概述

本文档记录 Scanner 系统的改进建议，用于提升系统的可靠性、可观测性和容错能力。

**注意**：这些建议目前暂不实施，仅作为未来优化的参考。

## 改进优先级

### 🔴 P0 - 高优先级（防止数据不一致）

#### 1. 添加超时监控，防止版本卡死

**问题**：版本可能永久停留在 `SCANNING` 状态

**解决方案**：

```java
// 添加定时任务，自动处理卡死的扫描任务
@Scheduled(fixedRate = 300000) // 每 5 分钟执行一次
public void checkStuckScans() {
    List<SkillVersion> stuckVersions = skillVersionRepository
        .findByStatusAndUpdatedAtBefore(
            SkillVersionStatus.SCANNING,
            LocalDateTime.now().minusMinutes(10)
        );

    stuckVersions.forEach(version -> {
        log.warn("Scan stuck for versionId={}, auto-failing", version.getId());
        version.setStatus(SkillVersionStatus.SCAN_FAILED);
        skillVersionRepository.save(version);

        // 创建人工审核任务
        skillRepository.findById(version.getSkillId())
            .ifPresent(skill -> reviewTaskRepository.save(
                new ReviewTask(version.getId(), skill.getNamespaceId(), version.getCreatedBy())
            ));
    });
}
```

**配置项**：

```yaml
skillhub:
  security:
    scanner:
      stuck-scan-timeout-minutes: 10  # 超过 10 分钟自动标记失败
```

**预期效果**：
- 防止版本永久卡死
- 自动降级到人工审核
- 提升用户体验

---

### 🟡 P1 - 中优先级（防止服务雪崩）

#### 2. 添加熔断器，防止雪崩

**问题**：Scanner 持续故障时，大量重试请求可能导致雪崩

**解决方案**：

使用 Resilience4j 实现熔断器：

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.1.0</version>
</dependency>
```

```java
// SkillScannerService.java
@CircuitBreaker(name = "scanner", fallbackMethod = "scanFallback")
public SecurityScanResponse scan(SecurityScanRequest request) {
    // 原有的扫描逻辑
    return httpClient.post(scanUrl, request);
}

private SecurityScanResponse scanFallback(SecurityScanRequest request, Exception e) {
    log.error("Scanner circuit breaker triggered, falling back to manual review", e);
    throw new ScannerUnavailableException("Scanner service unavailable, please try again later");
}
```

**配置项**：

```yaml
resilience4j:
  circuitbreaker:
    instances:
      scanner:
        failure-rate-threshold: 50  # 失败率超过 50% 触发熔断
        wait-duration-in-open-state: 60s  # 熔断后等待 1 分钟
        sliding-window-size: 10  # 滑动窗口大小
        minimum-number-of-calls: 5  # 最小调用次数
        permitted-number-of-calls-in-half-open-state: 3  # 半开状态允许的调用次数
```

**预期效果**：
- 快速失败，避免长时间等待
- 保护 Scanner 服务不被打垮
- 自动恢复机制

---

#### 3. 添加重试策略优化

**问题**：当前重试机制可能导致请求风暴

**解决方案**：

```java
@Retry(name = "scanner", fallbackMethod = "scanFallback")
@CircuitBreaker(name = "scanner", fallbackMethod = "scanFallback")
public SecurityScanResponse scan(SecurityScanRequest request) {
    return httpClient.post(scanUrl, request);
}
```

**配置项**：

```yaml
resilience4j:
  retry:
    instances:
      scanner:
        max-attempts: 3  # 最多重试 3 次
        wait-duration: 5s  # 重试间隔 5 秒
        exponential-backoff-multiplier: 2  # 指数退避倍数
        retry-exceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
```

**预期效果**：
- 指数退避，避免请求风暴
- 只对特定异常重试
- 更智能的重试策略

---

### 🟢 P2 - 低优先级（提升可观测性）

#### 4. 添加健康检查端点

**问题**：无法快速判断 Scanner 服务是否可用

**解决方案**：

```java
@RestController
@RequestMapping("/actuator/health")
public class ScannerHealthIndicator {

    private final SkillScannerService scannerService;

    @GetMapping("/scanner")
    public ResponseEntity<Map<String, Object>> scannerHealth() {
        try {
            boolean healthy = scannerService.isHealthy();
            Map<String, Object> health = Map.of(
                "status", healthy ? "UP" : "DOWN",
                "timestamp", System.currentTimeMillis()
            );
            return healthy
                ? ResponseEntity.ok(health)
                : ResponseEntity.status(503).body(health);
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
}
```

```java
// SkillScannerService.java
public boolean isHealthy() {
    try {
        HttpResponse<String> response = httpClient.get(baseUrl + "/health");
        return response.statusCode() == 200;
    } catch (Exception e) {
        log.warn("Scanner health check failed", e);
        return false;
    }
}
```

**预期效果**：
- 快速判断 Scanner 服务状态
- 集成到监控系统
- 支持自动化健康检查

---

#### 5. 添加详细的指标和日志

**问题**：缺少详细的监控指标

**解决方案**：

```java
// 添加 Micrometer 指标
@Component
public class ScannerMetrics {

    private final MeterRegistry registry;
    private final Counter scanSuccessCounter;
    private final Counter scanFailureCounter;
    private final Timer scanDurationTimer;

    public ScannerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.scanSuccessCounter = Counter.builder("scanner.scans.success")
            .description("Number of successful scans")
            .register(registry);
        this.scanFailureCounter = Counter.builder("scanner.scans.failure")
            .description("Number of failed scans")
            .register(registry);
        this.scanDurationTimer = Timer.builder("scanner.scan.duration")
            .description("Scan duration")
            .register(registry);
    }

    public void recordSuccess() {
        scanSuccessCounter.increment();
    }

    public void recordFailure() {
        scanFailureCounter.increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }
}
```

**预期效果**：
- 详细的性能指标
- 支持 Prometheus 监控
- 便于问题排查

---

#### 6. 添加临时文件清理任务

**问题**：临时文件可能泄漏

**解决方案**：

```java
@Scheduled(cron = "0 0 2 * * ?") // 每天凌晨 2 点执行
public void cleanupOrphanedTempFiles() {
    Path tempDir = Paths.get("/tmp/skillhub-scans");
    if (!Files.exists(tempDir)) {
        return;
    }

    try (Stream<Path> files = Files.walk(tempDir)) {
        long cutoffTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);

        files.filter(Files::isRegularFile)
             .filter(path -> {
                 try {
                     return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                 } catch (IOException e) {
                     return false;
                 }
             })
             .forEach(path -> {
                 try {
                     Files.delete(path);
                     log.info("Cleaned up orphaned temp file: {}", path);
                 } catch (IOException e) {
                     log.warn("Failed to delete orphaned temp file: {}", path, e);
                 }
             });
    } catch (IOException e) {
        log.error("Failed to cleanup orphaned temp files", e);
    }
}
```

**配置项**：

```yaml
skillhub:
  security:
    scanner:
      temp-file-cleanup:
        enabled: true
        cron: "0 0 2 * * ?"  # 每天凌晨 2 点
        retention-hours: 1  # 保留 1 小时内的文件
```

**预期效果**：
- 自动清理孤儿文件
- 防止磁盘空间耗尽
- 定期维护

---

## 实施建议

### 阶段 1：紧急修复（1-2 天）

1. 实施超时监控（P0）
2. 添加基本的健康检查端点（P2）

### 阶段 2：稳定性提升（1 周）

1. 实施熔断器（P1）
2. 优化重试策略（P1）
3. 添加详细的指标和日志（P2）

### 阶段 3：运维优化（2 周）

1. 添加临时文件清理任务（P2）
2. 完善监控告警规则
3. 编写运维手册

---

## 技术依赖

### 新增依赖

```xml
<!-- Resilience4j 熔断器和重试 -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- Micrometer 指标（Spring Boot 已包含） -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### 配置变更

需要在 `application.yml` 中添加：
- Resilience4j 熔断器配置
- Resilience4j 重试配置
- Scanner 超时监控配置
- 临时文件清理配置

---

## 测试计划

### 单元测试

- [ ] 超时监控逻辑测试
- [ ] 熔断器触发和恢复测试
- [ ] 重试策略测试
- [ ] 健康检查端点测试
- [ ] 临时文件清理测试

### 集成测试

- [ ] Scanner 服务宕机场景测试
- [ ] Scanner 服务响应慢场景测试
- [ ] Scanner 服务返回错误场景测试
- [ ] 熔断器在高负载下的表现测试

### 性能测试

- [ ] 熔断器对性能的影响
- [ ] 重试策略对性能的影响
- [ ] 监控指标对性能的影响

---

## 相关文档

- [故障影响分析](./failure-impact-analysis.md)
- [运维监控指南](./monitoring-guide.md)
- [配置说明](./configuration.md)
