# Agent Skills 智能检索系统 - 数据模型

## 1. 数据库扩展

### 1.1 pgvector 扩展安装

```sql
-- 在 PostgreSQL 中安装 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 验证安装
SELECT * FROM pg_extension WHERE extname = 'vector';
```

## 2. 核心数据表

### 2.1 agent_session（Agent 会话表）

存储 Agent 与用户的对话会话信息。

```sql
CREATE TABLE agent_session (
    -- 主键
    id BIGSERIAL PRIMARY KEY,

    -- 标识信息
    agent_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128) UNIQUE NOT NULL,

    -- 上下文信息
    scenario_description TEXT,
    user_role VARCHAR(128),

    -- 元数据（JSONB 格式，灵活扩展）
    metadata JSONB DEFAULT '{}',

    -- 时间戳
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMP WITH TIME ZONE,

    -- 索引
    CONSTRAINT fk_agent_session_user FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX idx_agent_session_agent_id ON agent_session(agent_id);
CREATE INDEX idx_agent_session_user_id ON agent_session(user_id);
CREATE INDEX idx_agent_session_session_id ON agent_session(session_id);
CREATE INDEX idx_agent_session_started_at ON agent_session(started_at DESC);

-- GIN 索引用于 JSONB 查询
CREATE INDEX idx_agent_session_metadata ON agent_session USING GIN(metadata);
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键 |
| agent_id | VARCHAR(128) | Agent 标识符 |
| user_id | VARCHAR(128) | 用户 ID |
| session_id | VARCHAR(128) | 会话唯一标识 |
| scenario_description | TEXT | 场景描述 |
| user_role | VARCHAR(128) | 用户角色 |
| metadata | JSONB | 扩展元数据 |
| started_at | TIMESTAMPTZ | 会话开始时间 |
| updated_at | TIMESTAMPTZ | 会话更新时间 |
| ended_at | TIMESTAMPTZ | 会话结束时间 |

### 2.2 agent_message（对话消息表）

存储会话中的每条消息。

```sql
CREATE TABLE agent_message (
    -- 主键
    id BIGSERIAL PRIMARY KEY,

    -- 关联会话
    session_id BIGINT NOT NULL,

    -- 消息内容
    role VARCHAR(32) NOT NULL,  -- 'user' | 'assistant' | 'system'
    content TEXT NOT NULL,

    -- 意图识别结果
    intent_label VARCHAR(128),
    intent_confidence DECIMAL(5,4),

    -- 向量嵌入
    embedding vector(1536),

    -- 元数据
    metadata JSONB DEFAULT '{}',

    -- 时间戳
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- 外键约束
    CONSTRAINT fk_agent_message_session
        FOREIGN KEY (session_id)
        REFERENCES agent_session(id)
        ON DELETE CASCADE,

    -- 检查约束
    CONSTRAINT chk_agent_message_role
        CHECK (role IN ('user', 'assistant', 'system')),

    CONSTRAINT chk_agent_message_confidence
        CHECK (intent_confidence IS NULL OR
               (intent_confidence >= 0 AND intent_confidence <= 1))
);

-- 创建索引
CREATE INDEX idx_agent_message_session_id ON agent_message(session_id);
CREATE INDEX idx_agent_message_created_at ON agent_message(created_at DESC);
CREATE INDEX idx_agent_message_intent ON agent_message(intent_label);

-- 向量索引（需要先有足够数据）
-- CREATE INDEX idx_agent_message_embedding ON agent_message
--     USING ivfflat(embedding vector_cosine_ops) WITH (lists = 100);

-- GIN 索引用于 JSONB 查询
CREATE INDEX idx_agent_message_metadata ON agent_message USING GIN(metadata);
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键 |
| session_id | BIGINT | 关联的会话 ID |
| role | VARCHAR(32) | 消息角色 |
| content | TEXT | 消息内容 |
| intent_label | VARCHAR(128) | 识别的意图标签 |
| intent_confidence | DECIMAL(5,4) | 意图置信度 |
| embedding | vector(1536) | 消息向量 |
| metadata | JSONB | 扩展元数据 |
| created_at | TIMESTAMPTZ | 创建时间 |

### 2.3 skill_embedding（技能向量表）

存储技能的向量嵌入，用于语义检索。

```sql
CREATE TABLE skill_embedding (
    -- 主键
    id BIGSERIAL PRIMARY KEY,

    -- 关联技能
    skill_id BIGINT NOT NULL,
    skill_version_id BIGINT,

    -- 嵌入类型和向量
    embedding_type VARCHAR(32) NOT NULL,
    embedding vector(1536) NOT NULL,

    -- 元数据
    metadata JSONB DEFAULT '{}',

    -- 时间戳
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- 外键约束
    CONSTRAINT fk_skill_embedding_skill
        FOREIGN KEY (skill_id)
        REFERENCES skill(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_skill_embedding_version
        FOREIGN KEY (skill_version_id)
        REFERENCES skill_version(id)
        ON DELETE SET NULL,

    -- 唯一约束
    CONSTRAINT uk_skill_embedding UNIQUE(skill_id, embedding_type),

    -- 检查约束
    CONSTRAINT chk_skill_embedding_type
        CHECK (embedding_type IN ('description', 'example', 'usage', 'combined'))
);

-- 创建索引
CREATE INDEX idx_skill_embedding_skill_id ON skill_embedding(skill_id);
CREATE INDEX idx_skill_embedding_version_id ON skill_embedding(skill_version_id);

-- 向量索引（ivfflat 类型，平衡精度和性能）
CREATE INDEX idx_skill_embedding_embedding ON skill_embedding
    USING ivfflat(embedding vector_cosine_ops)
    WITH (lists = 100);

-- GIN 索引用于 JSONB 查询
CREATE INDEX idx_skill_embedding_metadata ON skill_embedding USING GIN(metadata);
```

**embedding_type 枚举值**：

| 类型 | 说明 |
|------|------|
| description | 技能描述向量化 |
| example | 示例代码向量化 |
| usage | 使用说明向量化 |
| combined | 综合向量 |

### 2.4 intent_mapping（意图映射表）

存储意图类别及其相关配置。

```sql
CREATE TABLE intent_mapping (
    -- 主键
    id BIGSERIAL PRIMARY KEY,

    -- 意图信息
    intent_label VARCHAR(128) UNIQUE NOT NULL,
    description TEXT,

    -- 关联的技能类别
    skill_categories TEXT[],

    -- 示例查询（用于意图识别）
    example_queries TEXT[],

    -- 意图向量
    embedding vector(1536) NOT NULL,

    -- 优先级和状态
    priority INTEGER DEFAULT 0,
    active BOOLEAN DEFAULT true,

    -- 时间戳
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- 检查约束
    CONSTRAINT chk_intent_mapping_priority
        CHECK (priority >= 0)
);

-- 创建索引
CREATE INDEX idx_intent_mapping_label ON intent_mapping(intent_label);
CREATE INDEX idx_intent_mapping_active ON intent_mapping(active);

-- 向量索引
CREATE INDEX idx_intent_mapping_embedding ON intent_mapping
    USING ivfflat(embedding vector_cosine_ops)
    WITH (lists = 50);

-- GIN 索引用于数组查询
CREATE INDEX idx_intent_mapping_categories ON intent_mapping
    USING GIN(skill_categories);
```

## 3. Java 领域模型

### 3.1 AgentSession

```java
package com.iflytek.skillhub.domain.agent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "agent_session")
public class AgentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false, length = 128)
    private String agentId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "session_id", nullable = false, unique = true, length = 128)
    private String sessionId;

    @Column(name = "scenario_description", columnDefinition = "TEXT")
    private String scenarioDescription;

    @Column(name = "user_role", length = 128)
    private String userRole;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = Map.of();

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters...
}
```

### 3.2 AgentMessage

```java
package com.iflytek.skillhub.domain.agent;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "agent_message")
public class AgentMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private MessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "intent_label", length = 128)
    private String intentLabel;

    @Column(name = "intent_confidence", precision = 5, scale = 4)
    private BigDecimal intentConfidence;

    // 注意：pgvector 的 Java 类型需要自定义类型处理器
    // 这里使用 String 存储序列化后的向量
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private String embedding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = Map.of();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public enum MessageRole {
        USER, ASSISTANT, SYSTEM
    }

    // Getters and Setters...
}
```

### 3.3 SkillEmbedding

```java
package com.iflytek.skillhub.domain.agent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "skill_embedding")
public class SkillEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "skill_version_id")
    private Long skillVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_type", nullable = false, length = 32)
    private EmbeddingType embeddingType;

    @Column(name = "embedding", nullable = false, columnDefinition = "vector(1536)")
    private String embedding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = Map.of();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum EmbeddingType {
        DESCRIPTION, EXAMPLE, USAGE, COMBINED
    }

    // Getters and Setters...
}
```

### 3.4 IntentMapping

```java
package com.iflytek.skillhub.domain.agent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "intent_mapping")
public class IntentMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intent_label", nullable = false, unique = true, length = 128)
    private String intentLabel;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "intent_skill_categories", joinColumns = @JoinColumn(name = "intent_id"))
    @Column(name = "category")
    private List<String> skillCategories;

    @ElementCollection
    @CollectionTable(name = "intent_example_queries", joinColumns = @JoinColumn(name = "intent_id"))
    @Column(name = "query")
    private List<String> exampleQueries;

    @Column(name = "embedding", nullable = false, columnDefinition = "vector(1536)")
    private String embedding;

    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters...
}
```

## 4. 初始数据

### 4.1 预定义意图类别

```sql
-- 插入预定义意图
INSERT INTO intent_mapping (intent_label, description, skill_categories, example_queries, priority, active) VALUES
('code_generation', '代码生成相关技能', ARRAY['coding', 'development'], ARRAY['写一个函数', '生成代码', '创建类'], 10, true),
('data_analysis', '数据分析相关技能', ARRAY['data', 'analysis', 'visualization'], ARRAY['分析数据', '绘制图表', '数据统计'], 10, true),
('text_processing', '文本处理相关技能', ARRAY['text', 'nlp', 'language'], ARRAY['处理文本', '提取关键词', '文本分类'], 10, true),
('api_integration', 'API 集成相关技能', ARRAY['api', 'integration', 'web'], ARRAY['调用 API', 'HTTP 请求', 'REST API'], 10, true),
('file_processing', '文件处理相关技能', ARRAY['file', 'io', 'storage'], ARRAY['读取文件', '写入文件', '文件转换'], 10, true),
('database', '数据库相关技能', ARRAY['database', 'sql', 'storage'], ARRAY['查询数据库', 'SQL 语句', '数据库操作'], 10, true),
('general', '通用查询', ARRAY['general'], ARRAY['帮助', '能做什么', '功能列表'], 0, true);
```

## 5. 数据迁移

### 5.1 Flyway 迁移脚本

```sql
-- V__create_agent_tables.sql

-- 1. 安装 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 创建 agent_session 表
CREATE TABLE agent_session (
    id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128) UNIQUE NOT NULL,
    scenario_description TEXT,
    user_role VARCHAR(128),
    metadata JSONB DEFAULT '{}',
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMP WITH TIME ZONE
);

-- 3. 创建 agent_message 表
CREATE TABLE agent_message (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content TEXT NOT NULL,
    intent_label VARCHAR(128),
    intent_confidence DECIMAL(5,4) CHECK (intent_confidence IS NULL OR (intent_confidence >= 0 AND intent_confidence <= 1)),
    embedding vector(1536),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_agent_message_session FOREIGN KEY (session_id) REFERENCES agent_session(id) ON DELETE CASCADE
);

-- 4. 创建 skill_embedding 表
CREATE TABLE skill_embedding (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL,
    skill_version_id BIGINT,
    embedding_type VARCHAR(32) NOT NULL CHECK (embedding_type IN ('description', 'example', 'usage', 'combined')),
    embedding vector(1536) NOT NULL,
    metadata JSONB DEFAULT '{}',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_skill_embedding UNIQUE(skill_id, embedding_type),
    CONSTRAINT fk_skill_embedding_skill FOREIGN KEY (skill_id) REFERENCES skill(id) ON DELETE CASCADE
);

-- 5. 创建 intent_mapping 表
CREATE TABLE intent_mapping (
    id BIGSERIAL PRIMARY KEY,
    intent_label VARCHAR(128) UNIQUE NOT NULL,
    description TEXT,
    skill_categories TEXT[],
    example_queries TEXT[],
    embedding vector(1536) NOT NULL,
    priority INTEGER DEFAULT 0 CHECK (priority >= 0),
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 6. 创建索引
CREATE INDEX idx_agent_session_agent_id ON agent_session(agent_id);
CREATE INDEX idx_agent_session_user_id ON agent_session(user_id);
CREATE INDEX idx_agent_session_session_id ON agent_session(session_id);
CREATE INDEX idx_agent_session_metadata ON agent_session USING GIN(metadata);

CREATE INDEX idx_agent_message_session_id ON agent_message(session_id);
CREATE INDEX idx_agent_message_intent ON agent_message(intent_label);
CREATE INDEX idx_agent_message_metadata ON agent_message USING GIN(metadata);

CREATE INDEX idx_skill_embedding_skill_id ON skill_embedding(skill_id);
CREATE INDEX idx_skill_embedding_embedding ON skill_embedding USING ivfflat(embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_skill_embedding_metadata ON skill_embedding USING GIN(metadata);

CREATE INDEX idx_intent_mapping_label ON intent_mapping(intent_label);
CREATE INDEX idx_intent_mapping_active ON intent_mapping(active);
CREATE INDEX idx_intent_mapping_embedding ON intent_mapping USING ivfflat(embedding vector_cosine_ops) WITH (lists = 50);
CREATE INDEX idx_intent_mapping_categories ON intent_mapping USING GIN(skill_categories);
```
