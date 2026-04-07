# Agent Skills 智能检索系统 - 架构设计

## 1. 系统架构分层

```
┌─────────────────────────────────────────────────────────────────┐
│                         API Gateway Layer                        │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         Agent Skill Search Controller                    │  │
│  │  - POST /api/agent/skills/search                         │  │
│  │  - POST /api/agent/sessions                              │  │
│  │  - GET  /api/agent/sessions/{id}                         │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                        Service Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   Context    │  │    Intent    │  │    Query     │         │
│  │   Builder    │  │  Classifier  │  │   Embedder   │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │  Hybrid      │  │   Skill      │  │    Agent     │         │
│  │  Search      │  │  Reranker    │  │   Session    │         │
│  │  Service     │  │              │  │   Service    │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                      Domain Layer                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   Agent      │  │    Skill     │  │    Intent    │         │
│  │   Session    │  │  Embedding   │  │   Mapping    │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                   Infrastructure Layer                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │  PostgreSQL  │  │    Redis     │  │   OpenAI     │         │
│  │  + pgvector  │  │    Cache     │  │   Embedding  │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

## 2. 核心组件详解

### 2.1 Context Builder（上下文构建器）

**职责**：收集并构建完整的查询上下文

**输入**：
- 用户查询文本
- Session ID
- 用户 ID
- 场景描述
- 用户角色

**输出**：`AgentSearchContext`

```java
public record AgentSearchContext(
    String query,                              // 查询文本
    List<AgentMessage> conversationHistory,    // 对话历史
    String scenarioDescription,                // 场景描述
    String userRole,                           // 用户角色
    Map<String, Object> metadata               // 元数据
) {}
```

### 2.2 Intent Classifier（意图分类器）

**职责**：识别用户查询的意图类型

**实现方式**：基于向量相似度的分类

**流程**：
1. 将查询文本转换为向量
2. 与预定义的意图嵌入计算余弦相似度
3. 返回 Top-N 意图及置信度

```java
public interface IntentClassifier {
    IntentClassificationResult classify(
        String query,
        AgentSearchContext context
    );
}

public record IntentClassificationResult(
    String intentLabel,           // 主要意图
    double confidence,            // 置信度
    List<String> alternativeIntents  // 备选意图
) {}
```

### 2.3 Query Embedder（查询向量化）

**职责**：将综合查询上下文转换为向量

**向量构建公式**：
```
query_vector = normalize(
    w1 * embed(query_text) +
    w2 * embed(conversation_context) +
    w3 * embed(scenario_description) +
    w4 * embed(user_role) +
    w5 * intent_embedding
)
```

**权重配置**：
- w1 (查询文本): 0.4
- w2 (对话上下文): 0.2
- w3 (场景描述): 0.15
- w4 (用户角色): 0.1
- w5 (意图): 0.15

### 2.4 Hybrid Search Service（混合检索服务）

**职责**：执行多维度检索

**检索流程**：
1. **向量检索**：使用 pgvector 进行语义相似度检索
2. **意图过滤**：根据识别的意图过滤技能类别
3. **标签匹配**：匹配用户指定或相关的标签
4. **权限过滤**：根据用户角色过滤可见技能

### 2.5 Skill Reranker（技能重排序器）

**职责**：对检索结果进行多维度重排序

**评分公式**：
```
final_score = α * vector_similarity
            + β * intent_match_score
            + γ * tag_match_score
            + δ * popularity_score
            + ε * rating_score
```

**权重配置**：
- α (向量相似度): 0.4
- β (意图匹配): 0.25
- γ (标签匹配): 0.2
- δ (流行度): 0.1
- ε (评分): 0.05

### 2.6 Agent Session Service（会话管理服务）

**职责**：管理 Agent 对话会话

**功能**：
- 创建新会话
- 保存对话消息
- 获取会话历史
- 会话过期管理

## 3. 数据流

```
┌──────────────┐
│ Agent Request│
└──────┬───────┘
       │
       ▼
┌─────────────────────────────────┐
│ 1. Context Builder              │
│    - 收集查询、历史、角色        │
│    - 构建 SearchContext         │
└──────┬──────────────────────────┘
       │
       ▼
┌─────────────────────────────────┐
│ 2. Intent Classifier            │
│    - 向量化查询                  │
│    - 匹配意图                    │
└──────┬──────────────────────────┘
       │
       ▼
┌─────────────────────────────────┐
│ 3. Query Embedder               │
│    - 融合多维度信息              │
│    - 生成查询向量                │
└──────┬──────────────────────────┘
       │
       ▼
┌─────────────────────────────────┐
│ 4. Hybrid Search                │
│    - 向量检索 (pgvector)         │
│    - 意图/标签/权限过滤          │
└──────┬──────────────────────────┘
       │
       ▼
┌─────────────────────────────────┐
│ 5. Skill Reranker               │
│    - 多维度评分                  │
│    - 重排序结果                  │
└──────┬──────────────────────────┘
       │
       ▼
┌─────────────────────────────────┐
│ 6. Return Results               │
│    - 返回匹配技能列表            │
│    - 包含匹配原因                │
└─────────────────────────────────┘
```

## 4. 模块依赖关系

```
Agent Skill Search Controller
    │
    ├─► AgentContextBuilder
    │       └─► AgentSessionService
    │               └─► Redis / PostgreSQL
    │
    ├─► IntentClassifier
    │       ├─► QueryEmbedder
    │       └─► IntentMappingRepository
    │               └─► PostgreSQL
    │
    ├─► HybridSkillSearchService
    │       ├─► QueryEmbedder
    │       ├─► SkillEmbeddingRepository
    │       ├─► IntentClassifier
    │       └─► SkillReranker
    │
    └─► SkillReranker
            ├─► SkillRepository
            └─► LabelRepository
```

## 5. 外部依赖

| 依赖 | 用途 | 调用方式 |
|------|------|----------|
| PostgreSQL | 数据持久化、向量检索 | JPA / Native SQL |
| Redis | 会话缓存、热数据存储 | Spring Data Redis |
| OpenAI API | 嵌入向量生成 | HTTP REST API |

## 6. 缓存策略

| 数据类型 | 缓存位置 | TTL | 更新策略 |
|----------|----------|-----|----------|
| 活跃会话 | Redis | 1小时 | 每次访问更新 |
| 意图嵌入 | Redis | 永久 | 管理接口更新 |
| 技能嵌入 | PostgreSQL | - | 版本更新时 |
| 查询结果 | Redis | 5分钟 | 主动失效 |

## 7. 错误处理

| 场景 | 处理策略 |
|------|----------|
| OpenAI API 调用失败 | 降级到基于关键词的检索 |
| 向量索引未就绪 | 使用全文搜索 |
| 会话不存在 | 创建新会话 |
| 意图识别失败 | 使用通用意图 "general" |

## 8. 扩展点

1. **嵌入模型切换**：支持配置不同的嵌入模型
2. **意图库扩展**：通过数据库动态添加新意图
3. **评分策略**：可配置的权重参数
4. **检索后处理**：支持自定义业务规则过滤
