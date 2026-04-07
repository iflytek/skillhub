# Agent 模块设计方案

## 1. 设计概述

本文档描述 Agent 对话上下文处理模块的设计方案，涵盖对话上下文管理、意图识别、元数据模型、pgvector 交互以及上下文向量化策略。

### 1.1 设计目标

- **上下文感知**：结合多轮对话历史，理解用户当前需求
- **意图精准识别**：准确识别用户查询意图，提高技能匹配精度
- **角色适配**：根据用户角色信息，推荐适合的技能
- **场景理解**：基于场景描述，提供上下文相关的技能推荐
- **高效检索**：利用 pgvector 进行高效的语义向量检索

### 1.2 核心模块

```
┌─────────────────────────────────────────────────────────────────┐
│                        Agent 模块架构                             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐ │
│  │ 上下文处理模块   │  │  意图识别模块    │  │ 元数据管理模块 │ │
│  │ Context Handler  │  │ Intent Recognizer│  │ Metadata Mgr   │ │
│  └──────────────────┘  └──────────────────┘  └────────────────┘ │
│           │                     │                     │          │
│           └─────────────────────┴─────────────────────┘          │
│                                 │                                │
│                          ┌──────────────┐                        │
│                          │ 向量化策略   │                        │
│                          │ Vectorization│                        │
│                          └──────┬───────┘                        │
│                                 │                                │
│                          ┌──────────────┐                        │
│                          │ pgvector     │                        │
│                          │ Interaction  │                        │
│                          └──────────────┘                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Agent 对话上下文处理模块

### 2.1 模块职责

- 收集和管理 Agent 与用户的对话历史
- 构建完整的查询上下文
- 维护会话状态和元数据
- 提供上下文摘要和关键信息提取

### 2.2 核心组件

#### 2.2.1 ContextBuilder（上下文构建器）

**职责**：将分散的信息整合成完整的搜索上下文

**输入参数**：
- `queryText`：当前用户查询文本
- `sessionId`：会话标识符
- `userId`：用户标识
- `agentId`：Agent 标识

**输出**：`AgentSearchContext` 对象

**处理流程**：
1. 验证输入参数的有效性
2. 根据 sessionId 获取会话信息
3. 获取历史对话消息（最近 N 轮）
4. 提取场景描述和用户角色
5. 整合元数据信息
6. 构建返回上下文对象

#### 2.2.2 ConversationManager（对话管理器）

**职责**：管理对话消息的存储和检索

**核心功能**：
- 保存新消息到会话
- 获取会话历史消息
- 对话轮次计数
- 消息角色验证（user/assistant/system）
- 对话摘要生成

**存储策略**：
- 热数据：Redis 缓存，TTL 1 小时
- 冷数据：PostgreSQL 持久化
- 混合模式：优先从缓存读取，缓存未命中时查询数据库

#### 2.2.3 ContextSummarizer（上下文摘要器）

**职责**：从多轮对话中提取关键信息

**摘要策略**：
- **轮次限制**：保留最近 N 轮对话（默认 5 轮）
- **关键信息提取**：
  - 用户提到的技术栈关键词
  - 用户遇到的问题类型
  - 之前推荐的技能及反馈
- **上下文压缩**：将长对话压缩为紧凑的摘要文本

**摘要格式**：
```
[上下文摘要]
用户需求: {用户的核心需求}
技术栈: {识别出的技术关键词}
历史推荐: {之前推荐的技能及用户反馈}
当前状态: {对话当前进展状态}
```

### 2.3 上下文数据结构

#### AgentSearchContext

```
AgentSearchContext {
  // 基础信息
  query: String                    // 当前查询文本
  sessionId: String                // 会话 ID
  userId: String                   // 用户 ID
  agentId: String                  // Agent ID

  // 对话历史
  conversationHistory: List<AgentMessage>  // 历史消息列表
  conversationSummary: String              // 对话摘要
  turnCount: Integer                       // 对话轮次

  // 场景和角色
  scenarioDescription: String       // 场景描述
  userRole: String                  // 用户角色

  // 意图信息
  detectedIntent: IntentInfo        // 识别的意图
  intentConfidence: Float           // 意图置信度

  // 元数据
  metadata: Map<String, Any>        // 扩展元数据
  userPreferences: Map<String, Any> // 用户偏好设置

  // 时间信息
  contextTimestamp: Instant         // 上下文构建时间
}
```

#### AgentMessage

```
AgentMessage {
  id: Long                         // 消息 ID
  sessionId: Long                  // 所属会话 ID
  role: MessageRole                // 消息角色 (USER/ASSISTANT/SYSTEM)
  content: String                  // 消息内容
  intentLabel: String?             // 识别的意图标签
  intentConfidence: Float?         // 意图置信度
  embedding: Vector?               // 消息向量
  metadata: Map<String, Any>       // 消息元数据
  createdAt: Instant               // 创建时间
}
```

### 2.4 上下文处理流程

```
┌─────────────────────────────────────────────────────────────┐
│  1. 请求接收                                                  │
│     └─ 接收查询文本、sessionId、userId、agentId            │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│  2. 会话获取                                                  │
│     ├─ 尝试从 Redis 获取会话                                  │
│     ├─ 缓存未命中时从 PostgreSQL 获取                         │
│     └─ 会话不存在时创建新会话                                 │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│  3. 对话历史加载                                              │
│     ├─ 获取最近 N 轮消息                                      │
│     ├─ 过滤系统消息                                          │
│     └─ 按时间排序                                            │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│  4. 上下文摘要生成                                            │
│     ├─ 提取关键关键词                                        │
│     ├─ 识别用户意图变化                                      │
│     └─ 生成紧凑摘要文本                                      │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│  5. 场景和角色信息整合                                        │
│     ├─ 从会话元数据中获取场景描述                             │
│     ├─ 获取用户角色信息                                      │
│     └─ 加载用户偏好设置                                      │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│  6. 上下文对象构建                                            │
│     └─ 整合所有信息，返回 AgentSearchContext                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 意图识别模块

### 3.1 模块职责

- 识别用户查询的真实意图
- 提供意图置信度评估
- 支持多意图分类
- 意图库动态管理

### 3.2 识别策略

#### 3.2.1 基于向量相似度的意图分类

**核心原理**：将用户查询向量化后，与预定义的意图嵌入向量计算余弦相似度

**实现步骤**：
1. 将用户查询文本转换为向量
2. 从数据库加载所有活跃意图的嵌入向量
3. 计算查询向量与每个意图向量的余弦相似度
4. 按相似度降序排序
5. 返回 Top-N 意图及置信度

**相似度计算公式**：
```
similarity(query, intent) = cosine_similarity(
    embed(query_text),
    intent.embedding_vector
)
```

**置信度映射**：
- 相似度 >= 0.8：高置信度 (0.8-1.0)
- 相似度 >= 0.5：中等置信度 (0.5-0.8)
- 相似度 >= 0.3：低置信度 (0.3-0.5)
- 相似度 < 0.3：返回默认意图 "general"

#### 3.2.2 多意图支持

**场景**：用户查询可能涉及多个意图

**处理策略**：
- 返回 Top-3 候选意图
- 主意图：相似度最高的意图
- 备选意图：相似度次高的意图
- 意图组合：当多个意图相似度接近时，标记为组合意图

**组合意图示例**：
- 查询："生成一个处理用户数据的 API"
- 识别结果：
  - 主意图：code_generation (0.85)
  - 备选意图：api_integration (0.78)
  - 组合标记：code_generation + api_integration

### 3.3 意图库设计

#### 3.3.1 预定义意图类别

| 意图标签 | 描述 | 示例查询 | 关联技能类别 |
|----------|------|----------|--------------|
| code_generation | 代码生成 | "写一个函数"、"生成代码" | coding, development |
| data_analysis | 数据分析 | "分析数据"、"绘制图表" | data, analysis, visualization |
| text_processing | 文本处理 | "处理文本"、"提取关键词" | text, nlp, language |
| api_integration | API 集成 | "调用 API"、"HTTP 请求" | api, integration, web |
| file_processing | 文件处理 | "读取文件"、"写入文件" | file, io, storage |
| database | 数据库操作 | "查询数据库"、"SQL 语句" | database, sql, storage |
| general | 通用查询 | "帮助"、"能做什么" | general |

#### 3.3.2 意图嵌入向量生成

**生成策略**：
1. 收集每个意图的示例查询（至少 5 条）
2. 将所有示例查询转换为向量
3. 计算示例向量的平均值作为意图的嵌入向量
4. 存储到 intent_mapping 表

**更新策略**：
- 定期更新：基于用户查询数据重新计算意图向量
- 手动更新：管理员通过管理界面添加/修改意图示例
- 增量更新：新示例查询时，更新意图向量的移动平均值

### 3.4 意图识别流程

```
┌─────────────────────────────────────────────────────────────┐
│  1. 接收查询文本                                              │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│  2. 文本预处理                                                │
│     ├─ 去除特殊字符                                          │
│     ├─ 分词和标准化                                          │
│     └─ 停用词过滤                                            │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│  3. 查询向量化                                                │
│     ├─ 调用 OpenAI Embedding API                             │
│     ├─ 或使用缓存的查询向量                                  │
│     └─ 获取 1536 维向量                                      │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│  4. 意图向量加载                                              │
│     ├─ 从 Redis 缓存加载意图向量                             │
│     ├─ 或从 PostgreSQL 查询                                  │
│     └─ 过滤活跃的意图                                        │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│  5. 相似度计算                                                │
│     ├─ 计算查询向量与每个意图向量的余弦相似度                 │
│     ├─ pgvector 使用 <=> 操作符                              │
│     └─ 得到每个意图的相似度分数                              │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│  6. 结果排序和筛选                                            │
│     ├─ 按相似度降序排序                                      │
│     ├─ 检查最高相似度是否超过阈值                            │
│     └─ 阈值以下返回 "general" 意图                           │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│  7. 返回识别结果                                              │
│     ├─ 主意图及置信度                                        │
│     ├─ 备选意图列表                                          │
│     └─ 处理时间统计                                          │
└─────────────────────────────────────────────────────────────┘
```

### 3.5 意图识别数据结构

#### IntentInfo

```
IntentInfo {
  id: Long                         // 意图 ID
  label: String                    // 意图标签
  description: String              // 意图描述
  skillCategories: List<String>    // 关联的技能类别
  exampleQueries: List<String>     // 示例查询
  embedding: Vector                // 意图嵌入向量
  priority: Integer                // 优先级
  active: Boolean                  // 是否活跃
  createdAt: Instant               // 创建时间
  updatedAt: Instant               // 更新时间
}
```

#### IntentClassificationResult

```
IntentClassificationResult {
  primaryIntent: IntentMatch       // 主意图
  alternativeIntents: List<IntentMatch>  // 备选意图
  isMixed: Boolean                 // 是否为混合意图
  processingTimeMs: Long           // 处理时间（毫秒）
  confidenceThreshold: Float       // 使用的置信度阈值
}

IntentMatch {
  intentLabel: String              // 意图标签
  confidence: Float                // 置信度
  similarity: Float                // 相似度分数
  matchedCategories: List<String>  // 匹配的技能类别
}
```

---

## 4. Agent 元数据模型

### 4.1 元数据架构

Agent 元数据采用分层结构设计，支持灵活的扩展和高效的查询。

```
Agent Metadata Hierarchy
├── Agent 基础信息
│   ├── Agent ID
│   ├── Agent 名称
│   ├── Agent 类型
│   └── Agent 状态
├── 用户信息
│   ├── 用户 ID
│   ├── 用户角色
│   ├── 用户权限
│   └── 用户偏好
├── 会话信息
│   ├── 会话 ID
│   ├── 场景描述
│   ├── 会话状态
│   └── 会话元数据
├── 标签系统
│   ├── 用户标签
│   ├── 场景标签
│   ├── 技能标签
│   └── 自定义标签
└── 扩展元数据
    ├── 键值对存储
    ├── 嵌套对象
    └── 数组类型
```

### 4.2 用户角色模型

#### 4.2.1 角色定义

| 角色代码 | 角色名称 | 描述 | 权限级别 |
|----------|----------|------|----------|
| admin | 管理员 | 系统管理员，拥有所有权限 | FULL |
| developer | 开发者 | 软件开发者，可以使用开发相关技能 | HIGH |
| data_analyst | 数据分析师 | 数据分析人员，使用数据处理技能 | HIGH |
| designer | 设计师 | UI/UX 设计师，使用设计相关技能 | MEDIUM |
| tester | 测试人员 | QA 工程师，使用测试相关技能 | MEDIUM |
| user | 普通用户 | 普通用户，使用基础技能 | LOW |
| guest | 访客 | 未登录用户，受限访问 | MINIMAL |

#### 4.2.2 角色数据结构

```
UserRole {
  id: String                       // 角色 ID
  code: String                     // 角色代码
  name: String                     // 角色名称
  description: String              // 角色描述
  permissions: List<Permission>    // 权限列表
  skillCategories: List<String>    // 可访问的技能类别
  metadata: Map<String, Any>       // 角色元数据
  createdAt: Instant               // 创建时间
  updatedAt: Instant               // 更新时间
}

Permission {
  resource: String                 // 资源类型
  action: String                   // 操作类型
  conditions: Map<String, Any>     // 条件限制
}
```

### 4.3 标签系统模型

#### 4.3.1 标签类型

| 标签类型 | 说明 | 示例 |
|----------|------|------|
| 技术栈标签 | 表示使用的技术 | "python", "react", "postgresql" |
| 功能标签 | 表示技能的功能 | "file-io", "data-processing", "api-call" |
| 场景标签 | 表示使用场景 | "web-development", "data-science", "automation" |
| 难度标签 | 表示使用难度 | "beginner", "intermediate", "advanced" |
| 状态标签 | 表示技能状态 | "stable", "experimental", "deprecated" |
| 自定义标签 | 用户自定义标签 | "my-favorite", "team-internal" |

#### 4.3.2 标签数据结构

```
TagDefinition {
  id: Long                         // 标签 ID
  slug: String                     // 标签标识符（URL 友好）
  name: String                     // 标签名称
  type: TagType                    // 标签类型
  description: String              // 标签描述
  color: String?                   // 标签颜色（用于 UI 显示）
  icon: String?                    // 标签图标
  metadata: Map<String, Any>       // 标签元数据
  createdAt: Instant               // 创建时间
  updatedAt: Instant               // 更新时间
}

TagType {
  TECH_STACK                       // 技术栈
  FUNCTION                         // 功能
  SCENARIO                         // 场景
  DIFFICULTY                       // 难度
  STATUS                           // 状态
  CUSTOM                           // 自定义
}
```

### 4.4 场景描述模型

#### 4.4.1 预定义场景

| 场景代码 | 场景名称 | 描述 | 关联技能类别 |
|----------|----------|------|--------------|
| web_frontend | Web 前端开发 | 前端页面和交互开发 | frontend, javascript, css |
| web_backend | Web 后端开发 | 服务器端 API 开发 | backend, api, database |
| data_science | 数据科学 | 数据分析和机器学习 | data, analysis, ml |
| mobile_dev | 移动应用开发 | 移动端 App 开发 | mobile, ios, android |
| devops | DevOps | 运维和部署 | deployment, ci-cd, infrastructure |
| automation | 自动化 | 自动化脚本和流程 | automation, scripting, scheduling |
| testing | 软件测试 | 测试和质量保证 | testing, qa, quality |

#### 4.4.2 场景数据结构

```
Scenario {
  id: String                       // 场景 ID
  code: String                     // 场景代码
  name: String                     // 场景名称
  description: String              // 场景描述
  skillCategories: List<String>    // 推荐的技能类别
  defaultTags: List<String>        // 默认标签
  suggestedSkills: List<Long>      // 推荐技能 ID 列表
  metadata: Map<String, Any>       // 场景元数据
  createdAt: Instant               // 创建时间
  updatedAt: Instant               // 更新时间
}
```

### 4.5 元数据存储策略

#### 4.5.1 存储位置

| 数据类型 | 存储位置 | 访问频率 | 更新频率 |
|----------|----------|----------|----------|
| Agent 基础信息 | PostgreSQL | 高 | 低 |
| 用户角色信息 | PostgreSQL + Redis | 高 | 低 |
| 会话信息 | PostgreSQL + Redis | 高 | 高 |
| 标签定义 | PostgreSQL + Redis | 高 | 低 |
| 场景定义 | PostgreSQL + Redis | 中 | 低 |
| 用户偏好 | PostgreSQL | 中 | 中 |
| 自定义元数据 | PostgreSQL (JSONB) | 中 | 高 |

#### 4.5.2 缓存策略

- **热点数据**：频繁访问的元数据缓存到 Redis
- **缓存键设计**：
  - 用户角色：`agent:role:{userId}`
  - 标签定义：`agent:tag:{tagSlug}`
  - 场景定义：`agent:scenario:{scenarioCode}`
  - 用户偏好：`agent:preferences:{userId}`
- **缓存过期**：
  - 基础元数据：24 小时
  - 用户偏好：1 小时
  - 会话信息：10 分钟

### 4.6 元数据查询接口

```
// 获取用户角色
getUserRole(userId: String): UserRole

// 获取用户标签
getUserTags(userId: String): List<TagDefinition>

// 获取场景信息
getScenario(scenarioCode: String): Scenario

// 获取用户偏好
getUserPreferences(userId: String): Map<String, Any>

// 搜索标签
searchTags(query: String, type: TagType?): List<TagDefinition>

// 根据角色获取可访问的技能类别
getAccessibleCategories(roleCode: String): List<String>

// 根据场景获取推荐技能
getRecommendedSkills(scenarioCode: String): List<Long>
```

---

## 5. 与 pgvector 交互的数据结构

### 5.1 向量存储设计

#### 5.1.1 向量表结构

使用现有的 skill_embedding 表进行向量存储，支持多种嵌入类型：

```sql
skill_embedding (
  id BIGSERIAL PRIMARY KEY,
  skill_id BIGINT NOT NULL,
  skill_version_id BIGINT,
  embedding_type VARCHAR(32) NOT NULL,  -- description/example/usage/combined
  embedding vector(1536) NOT NULL,
  metadata JSONB DEFAULT '{}',
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
)
```

#### 5.1.2 向量索引策略

使用 ivfflat 索引类型，平衡精度和性能：

```sql
CREATE INDEX idx_skill_embedding_embedding
ON skill_embedding
USING ivfflat(embedding vector_cosine_ops)
WITH (lists = 100);
```

**索引参数调优**：
- lists = sqrt(行数)
- 1000 行：lists = 32
- 10000 行：lists = 100
- 100000 行：lists = 316

### 5.2 查询向量构建

#### 5.2.1 多维度向量融合

将多种上下文信息融合为一个查询向量：

```
query_vector = normalize(
  w1 * embed(query_text) +
  w2 * embed(conversation_summary) +
  w3 * embed(scenario_description) +
  w4 * embed(user_role) +
  w5 * intent_embedding
)
```

**权重配置**：

| 权重 | 值 | 说明 |
|------|-----|------|
| w1 | 0.4 | 查询文本（最重要） |
| w2 | 0.2 | 对话上下文摘要 |
| w3 | 0.15 | 场景描述 |
| w4 | 0.1 | 用户角色 |
| w5 | 0.15 | 意图向量 |

#### 5.2.2 向量归一化

确保所有向量在同一尺度上：

```
normalize(vector):
  norm = sqrt(sum(v[i]² for i in range(len(vector))))
  if norm == 0:
    return vector
  return vector / norm
```

### 5.3 pgvector 查询接口

#### 5.3.1 向量相似度查询

```sql
-- 基础相似度查询
SELECT
  se.skill_id,
  s.name,
  s.summary,
  1 - (se.embedding <=> :query_vector) as similarity
FROM skill_embedding se
JOIN skill s ON s.id = se.skill_id
WHERE se.embedding_type = 'description'
  AND s.status = 'ACTIVE'
ORDER BY se.embedding <=> :query_vector
LIMIT :limit;
```

#### 5.3.2 多类型向量融合查询

```sql
-- 融合多种嵌入类型的查询
WITH description_scores AS (
  SELECT
    skill_id,
    1 - (embedding <=> :query_vector) as score
  FROM skill_embedding
  WHERE embedding_type = 'description'
),
combined_scores AS (
  SELECT
    skill_id,
    1 - (embedding <=> :query_vector) as score
  FROM skill_embedding
  WHERE embedding_type = 'combined'
),
fused_scores AS (
  SELECT
    COALESCE(d.skill_id, c.skill_id) as skill_id,
    (COALESCE(d.score, 0) * 0.6 + COALESCE(c.score, 0) * 0.4) as fused_score
  FROM description_scores d
  FULL OUTER JOIN combined_scores c ON d.skill_id = c.skill_id
)
SELECT
  fs.skill_id,
  s.name,
  s.summary,
  fs.fused_score as similarity
FROM fused_scores fs
JOIN skill s ON s.id = fs.skill_id
WHERE s.status = 'ACTIVE'
ORDER BY fs.fused_score DESC
LIMIT :limit;
```

#### 5.3.3 带过滤条件的向量查询

```sql
-- 结合意图、标签、权限过滤的向量查询
WITH vector_candidates AS (
  SELECT
    se.skill_id,
    1 - (se.embedding <=> :query_vector) as vector_score
  FROM skill_embedding se
  JOIN skill s ON s.id = se.skill_id
  WHERE se.embedding_type = 'description'
    AND s.status = 'ACTIVE'
    AND s.category = ANY(:intent_categories)
  ORDER BY se.embedding <=> :query_vector
  LIMIT 200
),
tag_filtered AS (
  SELECT
    vc.skill_id,
    vc.vector_score,
    COUNT(DISTINCT ld.slug) FILTER (
      WHERE ld.slug = ANY(:user_tags)
    ) as tag_match_count
  FROM vector_candidates vc
  LEFT JOIN skill_label sl ON sl.skill_id = vc.skill_id
  LEFT JOIN label_definition ld ON ld.id = sl.label_id
  GROUP BY vc.skill_id, vc.vector_score
)
SELECT
  tf.skill_id,
  s.name,
  s.summary,
  tf.vector_score,
  tf.tag_match_count,
  s.download_count,
  s.rating_avg
FROM tag_filtered tf
JOIN skill s ON s.id = tf.skill_id
WHERE (s.visibility = 'PUBLIC')
   OR (s.visibility = 'NAMESPACE_ONLY' AND s.namespace_id = ANY(:member_namespace_ids))
   OR (s.visibility = 'PRIVATE' AND (s.owner_id = :user_id))
ORDER BY tf.vector_score DESC
LIMIT :limit;
```

### 5.4 向量操作封装

#### 5.4.1 向量插入

```sql
-- 插入技能向量
INSERT INTO skill_embedding (
  skill_id,
  skill_version_id,
  embedding_type,
  embedding,
  metadata
) VALUES (
  :skill_id,
  :skill_version_id,
  :embedding_type,
  :embedding_vector,
  :metadata::jsonb
)
ON CONFLICT (skill_id, embedding_type)
DO UPDATE SET
  embedding = EXCLUDED.embedding,
  updated_at = NOW();
```

#### 5.4.2 批量向量插入

```sql
-- 批量插入技能向量
INSERT INTO skill_embedding (
  skill_id,
  embedding_type,
  embedding,
  metadata
)
SELECT
  unnest(:skill_ids)::BIGINT,
  unnest(:embedding_types),
  unnest(:embeddings)::vector(1536),
  unnest(:metadata_array)::jsonb
ON CONFLICT (skill_id, embedding_type)
DO UPDATE SET
  embedding = EXCLUDED.embedding,
  updated_at = NOW();
```

#### 5.4.3 向量删除

```sql
-- 删除技能向量
DELETE FROM skill_embedding
WHERE skill_id = :skill_id;

-- 删除特定类型的向量
DELETE FROM skill_embedding
WHERE skill_id = :skill_id
  AND embedding_type = :embedding_type;
```

### 5.5 向量缓存策略

#### 5.5.1 缓存设计

| 缓存类型 | 缓存键 | TTL | 更新策略 |
|----------|--------|-----|----------|
| 技能向量 | `skill:embedding:{skill_id}:{type}` | 永久 | 技能更新时失效 |
| 意图向量 | `intent:embedding:{intent_label}` | 永久 | 意图更新时失效 |
| 查询向量 | `query:embedding:{hash(query_text)}` | 1 小时 | 主动过期 |
| 向量索引状态 | `vector:index:status` | 5 分钟 | 定期刷新 |

#### 5.5.2 缓存实现

```
// 获取技能向量
getSkillEmbedding(skillId: Long, type: EmbeddingType): Vector

// 缓存技能向量
cacheSkillEmbedding(skillId: Long, type: EmbeddingType, vector: Vector)

// 批量预加载技能向量
preloadSkillEmbeddings(skillIds: List<Long>, type: EmbeddingType)

// 失效技能向量缓存
invalidateSkillEmbedding(skillId: Long)

// 失效所有向量缓存
invalidateAllEmbeddings()
```

---

## 6. Agent 上下文向量化策略

### 6.1 向量化策略概述

Agent 上下文向量化是将多维上下文信息转换为统一向量表示的过程，是实现语义相似度检索的关键。

### 6.2 分层向量化策略

#### 6.2.1 第一层：基础文本向量化

**对象**：查询文本本身

**方法**：直接调用 OpenAI Embedding API

**输入**：用户原始查询文本

**输出**：1536 维向量

**权重**：0.4

#### 6.2.2 第二层：对话上下文向量化

**对象**：对话历史摘要

**方法**：
1. 提取最近 N 轮对话
2. 生成对话摘要（见第 2.2.3 节）
3. 对摘要文本进行向量化

**输入**：对话摘要文本

**输出**：1536 维向量

**权重**：0.2

#### 6.2.3 第三层：场景描述向量化

**对象**：使用场景

**方法**：
1. 获取场景描述文本
2. 结合场景代码和名称构建增强描述
3. 对增强描述进行向量化

**输入**：场景代码 + 场景名称 + 场景描述

**输出**：1536 维向量

**权重**：0.15

#### 6.2.4 第四层：用户角色向量化

**对象**：用户角色和权限

**方法**：
1. 获取用户角色名称和描述
2. 获取角色关联的技能类别
3. 构建角色特征文本
4. 对角色特征文本进行向量化

**输入**：角色名称 + 角色描述 + 关联技能类别

**输出**：1536 维向量

**权重**：0.1

#### 6.2.5 第五层：意图向量化

**对象**：识别出的意图

**方法**：
1. 执行意图识别
2. 获取意图的预定义嵌入向量
3. 直接使用意图向量（无需重新计算）

**输入**：意图标签

**输出**：1536 维向量（来自预定义意图库）

**权重**：0.15

### 6.3 向量融合算法

#### 6.3.1 加权平均融合

```
def fuse_vectors(vectors: List[Vector], weights: List[float]) -> Vector:
    """
    使用加权平均融合多个向量

    Args:
        vectors: 待融合的向量列表
        weights: 对应的权重列表

    Returns:
        融合后的向量
    """
    # 确保向量维度一致
    dimension = len(vectors[0])
    fused = [0.0] * dimension

    # 加权求和
    for vec, weight in zip(vectors, weights):
        for i in range(dimension):
            fused[i] += vec[i] * weight

    # 归一化
    norm = math.sqrt(sum(x * x for x in fused))
    if norm > 0:
        fused = [x / norm for x in fused]

    return fused
```

#### 6.3.2 自适应权重调整

根据上下文信息动态调整权重：

```
def adaptive_weights(context: AgentSearchContext) -> List[float]:
    """
    根据上下文自适应调整权重

    Args:
        context: Agent 搜索上下文

    Returns:
        调整后的权重列表
    """
    base_weights = [0.4, 0.2, 0.15, 0.1, 0.15]  # 基础权重

    # 对话轮次多时，增加对话上下文权重
    if context.turnCount > 5:
        base_weights[1] = min(base_weights[1] + 0.1, 0.4)
        base_weights[0] -= 0.1

    # 意图置信度高时，增加意图权重
    if context.intentConfidence > 0.8:
        base_weights[4] = min(base_weights[4] + 0.05, 0.2)
        base_weights[2] -= 0.05

    # 场景描述明确时，增加场景权重
    if context.scenarioDescription and len(context.scenarioDescription) > 50:
        base_weights[2] = min(base_weights[2] + 0.05, 0.2)
        base_weights[3] -= 0.05

    # 确保权重总和为 1
    total = sum(base_weights)
    base_weights = [w / total for w in base_weights]

    return base_weights
```

### 6.4 向量化优化策略

#### 6.4.1 向量缓存

**缓存键设计**：
```
vector_cache:{hash(content)}:{model_version}
```

**缓存策略**：
- 技能向量：永久缓存
- 意图向量：永久缓存
- 查询向量：TTL 1 小时
- 对话摘要向量：TTL 10 分钟

#### 6.4.2 批量向量化

对于多个文本的向量化，使用批量 API 减少调用次数：

```
def batch_embed(texts: List[str], batch_size: int = 100) -> List[Vector]:
    """
    批量生成嵌入向量

    Args:
        texts: 文本列表
        batch_size: 每批处理的文本数量

    Returns:
        向量列表
    """
    all_embeddings = []
    for i in range(0, len(texts), batch_size):
        batch = texts[i:i + batch_size]
        # 调用批量 Embedding API
        response = openai.Embedding.create(
            model="text-embedding-3-small",
            input=batch
        )
        batch_embeddings = [item['embedding'] for item in response['data']]
        all_embeddings.extend(batch_embeddings)
    return all_embeddings
```

#### 6.4.3 异步向量化

对于非实时要求的向量化任务，使用异步处理：

```
async def async_embed(text: str) -> Vector:
    """
    异步生成嵌入向量

    Args:
        text: 待向量化的文本

    Returns:
        向量
    """
    # 使用异步 HTTP 客户端调用 API
    async with httpx.AsyncClient() as client:
        response = await client.post(
            "https://api.openai.com/v1/embeddings",
            json={
                "model": "text-embedding-3-small",
                "input": text
            },
            headers={"Authorization": f"Bearer {API_KEY}"}
        )
        data = response.json()
        return data['data'][0]['embedding']
```

### 6.5 向量质量控制

#### 6.5.1 向量维度验证

```
def validate_vector(vector: Vector, expected_dim: int = 1536) -> bool:
    """
    验证向量维度

    Args:
        vector: 待验证的向量
        expected_dim: 期望的维度

    Returns:
        是否有效
    """
    if len(vector) != expected_dim:
        return False
    if not all(isinstance(x, (int, float)) for x in vector):
        return False
    if any(math.isnan(x) or math.isinf(x) for x in vector):
        return False
    return True
```

#### 6.5.2 向量归一化检查

```
def is_normalized(vector: Vector, tolerance: float = 1e-6) -> bool:
    """
    检查向量是否已归一化

    Args:
        vector: 待检查的向量
        tolerance: 容差范围

    Returns:
        是否已归一化
    """
    norm = math.sqrt(sum(x * x for x in vector))
    return abs(norm - 1.0) < tolerance
```

### 6.6 向量降级策略

当 OpenAI API 调用失败时，使用降级策略：

#### 6.6.1 本地哈希向量

```
def fallback_hash_vector(text: str, dim: int = 1536) -> Vector:
    """
    使用哈希算法生成降级向量

    Args:
        text: 输入文本
        dim: 向量维度

    Returns:
        降级向量
    """
    # 使用 SHA256 哈希
    hash_obj = hashlib.sha256(text.encode('utf-8'))
    hash_bytes = hash_obj.digest()

    # 扩展到指定维度
    vector = []
    for i in range(dim):
        byte = hash_bytes[i % len(hash_bytes)]
        # 将字节映射到 [-1, 1] 范围
        value = (byte / 127.5) - 1.0
        vector.append(value)

    # 归一化
    norm = math.sqrt(sum(x * x for x in vector))
    if norm > 0:
        vector = [x / norm for x in vector]

    return vector
```

#### 6.6.2 缓存向量复用

```
def get_cached_or_fallback_vector(
    text: str,
    cache_key: str,
    fallback_func: Callable
) -> Vector:
    """
    获取缓存向量或使用降级策略

    Args:
        text: 输入文本
        cache_key: 缓存键
        fallback_func: 降级函数

    Returns:
        向量
    """
    # 尝试从缓存获取
    cached = cache.get(cache_key)
    if cached and validate_vector(cached):
        return cached

    # 尝试调用 API
    try:
        vector = call_embedding_api(text)
        cache.set(cache_key, vector, ttl=3600)
        return vector
    except Exception as e:
        logger.warning(f"Embedding API failed: {e}, using fallback")
        # 使用降级策略
        vector = fallback_func(text)
        cache.set(cache_key, vector, ttl=300)  # 降级向量缓存时间短
        return vector
```

---

## 7. 模块交互流程

### 7.1 完整处理流程

```
┌─────────────────────────────────────────────────────────────────┐
│                      Agent 请求处理流程                          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  1. 请求接收层                                                  │
│     └─ 接收 Agent 发送的搜索请求                                │
│        ├─ query: 查询文本                                       │
│        ├─ sessionId: 会话 ID                                    │
│        ├─ userId: 用户 ID                                       │
│        └─ options: 可选参数（标签、意图等）                     │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. 上下文处理模块                                              │
│     ├─ ContextBuilder: 构建搜索上下文                           │
│     │   ├─ 获取会话信息                                         │
│     │   ├─ 加载对话历史                                         │
│     │   ├─ 生成对话摘要                                         │
│     │   ├─ 提取场景和角色信息                                   │
│     │   └─ 构建 AgentSearchContext                             │
│     │                                                          │
│     └─ ConversationManager: 管理对话                           │
│         ├─ 保存当前消息                                         │
│         └─ 更新会话状态                                         │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  3. 意图识别模块                                                │
│     └─ IntentRecognizer: 识别查询意图                           │
│         ├─ 文本预处理                                           │
│         ├─ 查询向量化                                           │
│         ├─ 加载意图向量                                         │
│         ├─ 计算相似度                                           │
│         └─ 返回 IntentClassificationResult                     │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  4. 元数据管理模块                                              │
│     └─ MetadataManager: 获取元数据                              │
│         ├─ 获取用户角色和权限                                   │
│         ├─ 获取用户标签                                         │
│         ├─ 获取场景信息                                         │
│         └─ 获取用户偏好                                         │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  5. 向量化策略模块                                              │
│     └─ VectorizationStrategy: 生成查询向量                      │
│         ├─ 查询文本向量化 (w1=0.4)                              │
│         ├─ 对话上下文向量化 (w2=0.2)                            │
│         ├─ 场景描述向量化 (w3=0.15)                             │
│         ├─ 用户角色向量化 (w4=0.1)                              │
│         ├─ 意图向量化 (w5=0.15)                                 │
│         ├─ 自适应权重调整                                       │
│         └─ 向量融合和归一化                                     │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  6. pgvector 交互模块                                           │
│     └─ VectorSearchService: 执行向量检索                        │
│         ├─ 构建向量查询 SQL                                     │
│         ├─ 执行 pgvector 相似度查询                             │
│         ├─ 应用意图、标签、权限过滤                             │
│         └─ 返回候选技能列表                                     │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  7. 重排序模块                                                  │
│     └─ SkillReranker: 多维度评分和排序                         │
│         ├─ 计算意图匹配分数                                     │
│         ├─ 计算标签匹配分数                                     │
│         ├─ 计算流行度分数                                       │
│         ├─ 计算用户评分分数                                     │
│         ├─ 综合评分计算                                         │
│         └─ 生成匹配原因说明                                     │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  8. 响应构建                                                    │
│     └─ 构建统一的响应格式                                       │
│         ├─ 包含查询信息                                         │
│         ├─ 包含意图识别结果                                     │
│         ├─ 包含技能列表和评分                                   │
│         └─ 包含处理时间统计                                     │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  9. 返回响应                                                    │
│     └─ 返回给 Agent                                            │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 模块依赖关系

```
AgentSearchController
    │
    ├─► ContextBuilder (上下文处理模块)
    │       ├─► ConversationManager
    │       │       └─► AgentSessionRepository
    │       │               └─► PostgreSQL / Redis
    │       │
    │       └─► ContextSummarizer
    │               └─► LLM Service (可选)
    │
    ├─► IntentRecognizer (意图识别模块)
    │       ├─► EmbeddingService
    │       │       └─► OpenAI API
    │       │
    │       └─► IntentMappingRepository
    │               └─► PostgreSQL
    │
    ├─► MetadataManager (元数据管理模块)
    │       ├─► UserRepository
    │       ├─► RoleRepository
    │       ├─► TagRepository
    │       └─► ScenarioRepository
    │
    ├─► VectorizationStrategy (向量化策略模块)
    │       ├─► EmbeddingService
    │       ├─► CacheService
    │       └─► FallbackStrategy
    │
    ├─► VectorSearchService (pgvector 交互模块)
    │       ├─► SkillEmbeddingRepository
    │       │       └─► PostgreSQL + pgvector
    │       │
    │       └─► CacheService
    │               └─► Redis
    │
    └─► SkillReranker (重排序模块)
            ├─► SkillRepository
            └─► LabelRepository
```

---

## 8. 性能优化建议

### 8.1 缓存优化

1. **多级缓存**：
   - L1：内存缓存（本地应用）
   - L2：Redis 缓存（分布式）
   - L3：数据库（持久化）

2. **缓存预热**：
   - 系统启动时预加载热点数据
   - 定期刷新即将过期的缓存

3. **缓存击穿防护**：
   - 使用互斥锁防止并发重建
   - 设置合理的缓存过期时间

### 8.2 数据库优化

1. **索引优化**：
   - 确保向量索引参数合理
   - 定期重建索引以保持性能

2. **查询优化**：
   - 使用 CTE（Common Table Expressions）优化复杂查询
   - 限制返回的候选集大小
   - 使用批处理减少数据库往返

3. **连接池配置**：
   - 合理设置连接池大小
   - 监控连接使用情况

### 8.3 API 调用优化

1. **批量处理**：
   - 批量生成嵌入向量
   - 减少网络往返次数

2. **异步处理**：
   - 非关键路径使用异步调用
   - 使用消息队列处理耗时任务

3. **降级策略**：
   - API 失败时使用缓存或降级方案
   - 设置合理的超时时间

### 8.4 监控和告警

1. **性能指标**：
   - 查询响应时间
   - 向量检索准确率
   - 意图识别准确率
   - 缓存命中率

2. **告警规则**：
   - 响应时间超过阈值
   - 缓存命中率低于阈值
   - API 调用失败率超过阈值

---

## 9. 扩展性设计

### 9.1 插件化架构

支持通过插件扩展功能：

- **意图识别插件**：支持不同的意图识别算法
- **向量化插件**：支持不同的嵌入模型
- **过滤插件**：支持自定义过滤规则
- **评分插件**：支持自定义评分策略

### 9.2 配置化设计

关键参数支持动态配置：

- 向量融合权重
- 意图识别阈值
- 缓存策略
- 降级策略

### 9.3 多语言支持

- 支持多种语言的文本处理
- 支持多语言意图识别
- 支持多语言向量嵌入

---

## 10. 安全性考虑

### 10.1 权限控制

- 基于角色的访问控制（RBAC）
- 细粒度的技能可见性控制
- 用户数据隔离

### 10.2 数据保护

- 敏感信息脱敏
- 加密存储用户数据
- 安全的 API 调用

### 10.3 防护措施

- 请求速率限制
- 输入验证和清理
- SQL 注入防护
- XSS 防护

---

## 11. 总结

本设计方案提供了完整的 Agent 对话上下文处理模块架构，包括：

1. **上下文处理模块**：负责收集和管理对话历史，构建完整的搜索上下文
2. **意图识别模块**：基于向量相似度的意图分类，支持多意图识别
3. **元数据模型**：分层设计的用户角色、标签、场景等元数据结构
4. **pgvector 交互**：高效的向量存储和查询接口设计
5. **向量化策略**：多维度向量融合和自适应权重调整

该设计方案具有以下特点：

- **灵活性**：支持多种配置和扩展
- **高性能**：多级缓存和优化策略
- **可靠性**：降级策略和错误处理
- **可维护性**：清晰的模块划分和接口定义
- **可扩展性**：插件化架构和配置化设计

通过该设计方案，系统能够准确理解用户意图，结合多轮对话上下文，为 Agent 推荐最合适的技能。
