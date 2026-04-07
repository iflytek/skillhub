# Agent Skills 智能检索系统 - 实施指南

## 1. 实施阶段

### 阶段 1：基础设施准备（1-2 周）

#### 1.1 数据库配置

**安装 pgvector 扩展**：

```bash
# 连接到 PostgreSQL
psql -U postgres -d skillhub

# 安装 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

# 验证安装
SELECT * FROM pg_extension WHERE extname = 'vector';

# 查看可用版本
SELECT * FROM pg_available_extensions WHERE name = 'vector';
```

**配置连接池**（application.yml）：
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

#### 1.2 Redis 配置

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD}
      database: 0
      timeout: 5000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
```

#### 1.3 OpenAI API 配置

```yaml
openai:
  api-key: ${OPENAI_API_KEY}
  embedding:
    model: text-embedding-3-small
    dimension: 1536
    timeout: 30000
    max-retries: 3
```

### 阶段 2：数据库迁移（1 周）

#### 2.1 创建迁移脚本

创建 `V__create_agent_tables.sql`：

```sql
-- 参见 03-data-model.md 中的完整 SQL

-- 1. 安装 pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 创建 agent_session 表
...

-- 3. 创建 agent_message 表
...

-- 4. 创建 skill_embedding 表
...

-- 5. 创建 intent_mapping 表
...

-- 6. 创建索引
...

-- 7. 插入初始数据
INSERT INTO intent_mapping (intent_label, description, skill_categories, example_queries, priority, active)
VALUES
    ('code_generation', '代码生成相关技能', ARRAY['coding', 'development'], ARRAY['写一个函数', '生成代码', '创建类'], 10, true),
    ('data_analysis', '数据分析相关技能', ARRAY['data', 'analysis'], ARRAY['分析数据', '绘制图表', '数据统计'], 10, true),
    ...
;
```

#### 2.2 执行迁移

```bash
# 使用 Flyway
mvn flyway:migrate

# 或使用 Spring Boot 自动迁移
mvn spring-boot:run -Dspring.flyway.enabled=true
```

### 阶段 3：核心服务开发（2-3 周）

#### 3.1 领域模型

创建 `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/` 目录：

```java
// AgentSession.java
@Entity
@Table(name = "agent_session")
public class AgentSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    // ... 其他字段

    @PrePersist
    protected void onCreate() {
        startedAt = Instant.now();
        updatedAt = Instant.now();
    }
}
```

#### 3.2 Repository 接口

```java
// AgentSessionRepository.java
@Repository
public interface AgentSessionRepository extends JpaRepository<AgentSession, Long> {
    Optional<AgentSession> findBySessionId(String sessionId);

    List<AgentSession> findByUserIdAndEndedAtIsNull(String userId);

    @Query("SELECT a FROM AgentSession a WHERE a.sessionId = :sessionId AND a.endedAt IS NULL")
    Optional<AgentSession> findActiveBySessionId(@Param("sessionId") String sessionId);
}
```

#### 3.3 服务实现

**AgentContextBuilder.java**：
```java
@Service
public class AgentContextBuilderImpl implements AgentContextBuilder {

    private final AgentSessionService sessionService;
    private final int maxHistorySize;

    @Override
    public AgentSearchContext build(AgentSearchRequest request) {
        // 获取会话历史
        List<AgentMessage> history = request.sessionId() != null
            ? sessionService.getRecentMessages(request.sessionId(), maxHistorySize)
            : List.of();

        return new AgentSearchContext(
            request.query(),
            history,
            request.scenarioDescription(),
            request.userRole(),
            request.metadata()
        );
    }
}
```

**IntentClassifier.java**：
```java
@Service
public class VectorIntentClassifier implements IntentClassifier {

    private final IntentMappingRepository intentRepository;
    private final OpenAiEmbeddingClient embeddingClient;
    private final double minConfidence = 0.3;

    @Override
    public IntentClassificationResult classify(String query, AgentSearchContext context) {
        // 生成查询向量
        float[] queryVector = embeddingClient.embed(query);

        // 获取所有活跃意图
        List<IntentMapping> intents = intentRepository.findByActiveTrue();

        // 计算相似度
        List<IntentScore> scores = intents.stream()
            .map(intent -> new IntentScore(
                intent.getIntentLabel(),
                cosineSimilarity(queryVector, parseVector(intent.getEmbedding()))
            ))
            .sorted(Comparator.comparingDouble(IntentScore::score).reversed())
            .toList();

        if (scores.isEmpty() || scores.get(0).score() < minConfidence) {
            return new IntentClassificationResult("general", 0.0, List.of());
        }

        return new IntentClassificationResult(
            scores.get(0).label(),
            scores.get(0).score(),
            scores.subList(1, Math.min(3, scores.size()))
                .stream()
                .map(IntentScore::label)
                .toList()
        );
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

#### 3.4 OpenAI 集成

**OpenAiEmbeddingClient.java**：
```java
@Service
public class OpenAiEmbeddingClient {

    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;

    public float[] embed(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
            "model", model,
            "input", text
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        OpenAiResponse response = restTemplate.postForObject(
            "https://api.openai.com/v1/embeddings",
            request,
            OpenAiResponse.class
        );

        return response.data().get(0).embedding();
    }
}
```

### 阶段 4：向量生成（1 周）

#### 4.1 批量向量生成任务

```java
@Service
public class SkillEmbeddingGenerator {

    @Scheduled(fixedDelay = 3600000) // 每小时检查一次
    public void generatePendingEmbeddings() {
        List<Skill> skills = skillRepository.findSkillsWithoutEmbeddings();

        for (Skill skill : skills) {
            try {
                generateEmbeddings(skill);
            } catch (Exception e) {
                log.error("Failed to generate embeddings for skill {}", skill.getId(), e);
            }
        }
    }

    private void generateEmbeddings(Skill skill) {
        String description = skill.getSummary() != null ? skill.getSummary() : "";
        float[] descVector = embeddingClient.embed(description);

        SkillEmbedding embedding = new SkillEmbedding();
        embedding.setSkillId(skill.getId());
        embedding.setEmbeddingType(EmbeddingType.DESCRIPTION);
        embedding.setEmbedding(serializeVector(descVector));
        embedding.setUpdatedAt(Instant.now());

        embeddingRepository.save(embedding);
    }
}
```

#### 4.2 手动触发向量生成

```java
@RestController
@RequestMapping("/api/admin/skills")
public class AdminSkillController {

    @PostMapping("/{skillId}/embeddings")
    public ResponseEntity<?> generateEmbeddings(@PathVariable Long skillId) {
        Skill skill = skillRepository.findById(skillId)
            .orElseThrow(() -> new NotFoundException("Skill not found"));

        embeddingGenerator.generateEmbeddings(skill);

        return ResponseEntity.ok(Map.of("status", "processing"));
    }
}
```

### 阶段 5：API 开发（1 周）

#### 5.1 Controller 实现

**AgentSkillSearchController.java**：
```java
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentSkillSearchController {

    private final HybridSkillSearchService searchService;

    @PostMapping("/skills/search")
    public ResponseEntity<ApiResponse<SearchResult>> searchSkills(
        @Valid @RequestBody AgentSearchRequest request
    ) {
        SearchResult result = searchService.search(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<AgentSession>> createSession(
        @Valid @RequestBody CreateSessionRequest request
    ) {
        AgentSession session = sessionService.createSession(request);
        return ResponseEntity.ok(ApiResponse.success(session));
    }
}
```

#### 5.2 DTO 定义

```java
public record AgentSearchRequest(
    @NotBlank String query,
    String sessionId,
    @Min(1) @Max(100) Integer limit,
    SearchOptions options
) {
    public AgentSearchRequest {
        if (limit == null) limit = 10;
        if (options == null) options = new SearchOptions();
    }
}

public record SearchOptions(
    Boolean enableIntentRecognition,
    Boolean enableContextAware,
    List<String> tags,
    String intent,
    Double minConfidence
) {
    public SearchOptions {
        if (enableIntentRecognition == null) enableIntentRecognition = true;
        if (enableContextAware == null) enableContextAware = true;
        if (minConfidence == null) minConfidence = 0.3;
    }
}
```

### 阶段 6：测试（1-2 周）

#### 6.1 单元测试

```java
@SpringBootTest
class IntentClassifierTest {

    @Autowired
    private IntentClassifier intentClassifier;

    @Test
    void testClassifyCodeGenerationIntent() {
        String query = "写一个快速排序算法";

        IntentClassificationResult result = intentClassifier.classify(query, null);

        assertEquals("code_generation", result.intentLabel());
        assertTrue(result.confidence() > 0.5);
    }
}
```

#### 6.2 集成测试

```java
@SpringBootTest
@AutoConfigureMockMvc
class AgentSkillSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testSearchSkillsEndpoint() throws Exception {
        String request = """
            {
                "query": "处理 JSON 数据",
                "sessionId": "test-session-123",
                "limit": 5
            }
            """;

        mockMvc.perform(post("/api/agent/skills/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.results").isArray());
    }
}
```

### 阶段 7：部署（1 周）

#### 7.1 Docker 配置

**Dockerfile**：
```dockerfile
FROM openjdk:21-slim

WORKDIR /app

COPY target/skillhub-app.jar /app/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "skillhub-app.jar"]
```

#### 7.2 Kubernetes 配置

**deployment.yaml**：
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: skillhub-agent
spec:
  replicas: 3
  selector:
    matchLabels:
      app: skillhub-agent
  template:
    metadata:
      labels:
        app: skillhub-agent
    spec:
      containers:
      - name: skillhub
        image: skillhub/agent:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            secretKeyRef:
              name: skillhub-secrets
              key: database-url
        - name: OPENAI_API_KEY
          valueFrom:
            secretKeyRef:
              name: skillhub-secrets
              key: openai-api-key
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
```

### 阶段 8：监控与调优（持续）

#### 8.1 监控指标

```java
@Component
public class SearchMetrics {

    private final MeterRegistry meterRegistry;

    public void recordSearch(SearchResult result, long duration) {
        meterRegistry.counter("skill.search.total",
            "intent", result.intent().label()
        ).increment();

        meterRegistry.timer("skill.search.duration").record(duration, TimeUnit.MILLISECONDS);

        result.results().forEach(r ->
            meterRegistry.histogram("skill.search.score").record(r.finalScore())
        );
    }
}
```

#### 8.2 性能调优

**向量索引调优**：
```sql
-- 根据数据量调整 lists 参数
-- 建议值：sqrt(行数)
CREATE INDEX idx_skill_embedding_embedding ON skill_embedding
    USING ivfflat(embedding vector_cosine_ops)
    WITH (lists = 100);
```

**缓存配置优化**：
```yaml
skillhub:
  agent:
    search:
      cache:
        enabled: true
        ttl: 300  # 5分钟
        max-size: 10000
```

## 9. 检查清单

### 准备阶段
- [ ] PostgreSQL 17 安装完成
- [ ] pgvector 扩展安装完成
- [ ] Redis 7.0 配置完成
- [ ] OpenAI API Key 获取
- [ ] 数据库迁移脚本准备

### 开发阶段
- [ ] 领域模型定义完成
- [ ] Repository 接口定义完成
- [ ] 核心服务实现完成
- [ ] OpenAI 集成完成
- [ ] 混合检索实现完成
- [ ] API 端点实现完成

### 测试阶段
- [ ] 单元测试覆盖率 > 80%
- [ ] 集成测试通过
- [ ] 性能测试达标（< 500ms）
- [ ] 负载测试通过（100+ QPS）

### 部署阶段
- [ ] Docker 镜像构建完成
- [ ] Kubernetes 配置完成
- [ ] 环境变量配置完成
- [ ] 健康检查配置完成
- [ ] 日志收集配置完成

### 上线后
- [ ] 监控指标配置完成
- [ ] 告警规则配置完成
- [ ] 文档更新完成
- [ ] 团队培训完成

## 10. 常见问题

### Q1: pgvector 索引创建失败

**原因**：数据量不足或参数不当

**解决**：
```sql
-- 确保有足够的数据（至少 100 行）
-- 调整 lists 参数
CREATE INDEX idx_skill_embedding_embedding ON skill_embedding
    USING ivfflat(embedding vector_cosine_ops)
    WITH (lists = 32);
```

### Q2: OpenAI API 调用超时

**原因**：网络延迟或 API 限制

**解决**：
```yaml
openai:
  embedding:
    timeout: 60000  # 增加超时时间
    max-retries: 5   # 增加重试次数
```

### Q3: 搜索结果不准确

**原因**：向量未生成或权重配置不当

**解决**：
1. 检查技能向量是否生成
2. 调整重排序权重
3. 增加意图类别和示例

### Q4: 内存占用过高

**原因**：向量数据量大，缓存过多

**解决**：
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50

skillhub:
  agent:
    search:
      cache:
        max-size: 5000  # 减少缓存大小
```

## 11. 参考资源

- [pgvector 官方文档](https://github.com/pgvector/pgvector)
- [OpenAI Embeddings API](https://platform.openai.com/docs/guides/embeddings)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)
- [PostgreSQL 性能调优](https://wiki.postgresql.org/wiki/Performance_Optimization)
