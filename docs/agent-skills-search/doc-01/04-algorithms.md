# Agent Skills 智能检索系统 - 算法设计

## 1. 向量嵌入生成

### 1.1 嵌入模型配置

使用 OpenAI `text-embedding-3-small` 模型：
- 维度：1536
- 输入长度：最多 8191 tokens
- 输出格式：浮点数数组

### 1.2 技能向量生成

**输入文本构建**：
```
skill_text = skill_name + "\n" +
             skill_description + "\n" +
             skill_keywords + "\n" +
             usage_examples
```

**生成流程**：
```python
def generate_skill_embedding(skill):
    # 构建输入文本
    text = build_skill_text(skill)

    # 调用 OpenAI API
    response = openai.Embedding.create(
        model="text-embedding-3-small",
        input=text
    )

    # 返回向量
    return response['data'][0]['embedding']
```

**存储策略**：
- 为每个技能生成 4 种类型的嵌入
  - `description`: 仅技能描述
  - `example`: 仅示例代码
  - `usage`: 仅使用说明
  - `combined`: 综合所有内容

## 2. 意图识别算法

### 2.1 基于向量相似度的意图分类

**算法流程**：

```
1. 将用户查询转换为向量
   query_vector = embed(query_text)

2. 与所有活跃意图的嵌入计算余弦相似度
   for each intent in active_intents:
       similarity = cosine_similarity(query_vector, intent.embedding)

3. 按相似度排序，取 Top-N
   top_intents = sort_by_similarity(all_similarities)[:N]

4. 返回结果
   return top_intents[0] as primary_intent
```

**相似度计算**：
```sql
-- 使用 pgvector 的 <=> 操作符
SELECT
    intent_label,
    1 - (embedding <=> :query_vector) as similarity
FROM intent_mapping
WHERE active = true
ORDER BY embedding <=> :query_vector
LIMIT 5;
```

**置信度处理**：
- 最高相似度 < 0.3：返回默认意图 "general"
- 最高相似度 >= 0.3：返回识别的意图
- 返回 Top-3 备选意图

### 2.2 意图库更新

当添加新意图时：
1. 收集该意图的示例查询
2. 生成意图嵌入向量（使用示例查询的平均向量）
3. 插入 `intent_mapping` 表

## 3. 查询向量构建

### 3.1 多维度向量融合

**公式**：
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

| 权重 | 值 | 说明 |
|------|-----|------|
| w1 | 0.4 | 查询文本（最重要） |
| w2 | 0.2 | 对话上下文 |
| w3 | 0.15 | 场景描述 |
| w4 | 0.1 | 用户角色 |
| w5 | 0.15 | 意图向量 |

**归一化**：
```python
def normalize(vector):
    norm = np.linalg.norm(vector)
    if norm == 0:
        return vector
    return vector / norm
```

### 3.2 对话上下文编码

**策略**：使用最近 N 轮对话的摘要

```python
def encode_conversation(messages, max_turns=5):
    # 取最近 N 轮对话
    recent_messages = messages[-2*max_turns:]

    # 构建上下文文本
    context_text = "\n".join([
        f"{msg.role}: {msg.content}"
        for msg in recent_messages
    ])

    # 生成嵌入
    return embed(context_text)
```

## 4. 混合检索算法

### 4.1 向量检索（Vector Search）

**SQL 查询**：
```sql
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
LIMIT 200;
```

**向量索引**：
```sql
CREATE INDEX idx_skill_embedding_embedding ON skill_embedding
    USING ivfflat(embedding vector_cosine_ops)
    WITH (lists = 100);
```

### 4.2 意图过滤（Intent Filter）

**过滤逻辑**：
```sql
SELECT vc.skill_id, vc.similarity
FROM vector_candidates vc
JOIN skill s ON s.id = vc.skill_id
WHERE s.category = ANY(:intent_categories)
   OR vc.similarity > 0.7;  -- 高相似度可跨意图
```

### 4.3 标签匹配（Tag Matching）

**精确匹配**：
```sql
SELECT s.id, COUNT(*) as match_count
FROM skill s
JOIN skill_label sl ON sl.skill_id = s.id
JOIN label_definition ld ON ld.id = sl.label_id
WHERE s.id IN (:candidate_ids)
  AND ld.slug = ANY(:user_tags)
GROUP BY s.id;
```

**模糊匹配**：
- 计算标签向量与查询向量的相似度
- 相似度 > 0.6 的标签视为匹配

### 4.4 权限过滤（Permission Filter）

```sql
-- 公开技能
visibility = 'PUBLIC'

-- 用户私有技能
visibility = 'PRIVATE' AND (owner_id = :user_id OR namespace_id IN (:admin_namespaces))

-- 命名空间内可见
visibility = 'NAMESPACE_ONLY' AND namespace_id IN (:member_namespaces)
```

### 4.5 综合检索 SQL

```sql
WITH vector_candidates AS (
    SELECT
        se.skill_id,
        1 - (se.embedding <=> :query_vector) as vector_score
    FROM skill_embedding se
    JOIN skill s ON s.id = se.skill_id
    WHERE se.embedding_type = 'description'
      AND s.status = 'ACTIVE'
    ORDER BY se.embedding <=> :query_vector
    LIMIT 200
),
intent_filtered AS (
    SELECT
        vc.skill_id,
        vc.vector_score
    FROM vector_candidates vc
    JOIN skill s ON s.id = vc.skill_id
    WHERE s.category = ANY(:intent_categories)
       OR vc.vector_score > 0.7
),
permission_filtered AS (
    SELECT
        if.skill_id,
        if.vector_score
    FROM intent_filtered if
    JOIN skill s ON s.id = if.skill_id
    WHERE (s.visibility = 'PUBLIC')
       OR (s.visibility = 'NAMESPACE_ONLY' AND s.namespace_id = ANY(:member_namespace_ids))
       OR (s.visibility = 'PRIVATE' AND (s.owner_id = :user_id OR s.namespace_id = ANY(:admin_namespace_ids)))
),
tag_matched AS (
    SELECT
        pf.skill_id,
        pf.vector_score,
        COUNT(*) FILTER (WHERE ld.slug = ANY(:user_tags)) as tag_count
    FROM permission_filtered pf
    LEFT JOIN skill_label sl ON sl.skill_id = pf.skill_id
    LEFT JOIN label_definition ld ON ld.id = sl.label_id
    GROUP BY pf.skill_id, pf.vector_score
)
SELECT
    tm.skill_id,
    s.name,
    s.summary,
    s.description,
    tm.vector_score,
    tm.tag_count,
    s.download_count,
    s.rating_avg
FROM tag_matched tm
JOIN skill s ON s.id = tm.skill_id
ORDER BY tm.vector_score DESC
LIMIT :limit;
```

## 5. 重排序算法

### 5.1 多维度评分

**评分公式**：
```
final_score = α * normalize(vector_score)
            + β * normalize(intent_match_score)
            + γ * normalize(tag_match_score)
            + δ * normalize(popularity_score)
            + ε * normalize(rating_score)
```

**权重配置**：

| 权重 | 值 | 说明 |
|------|-----|------|
| α | 0.4 | 向量相似度 |
| β | 0.25 | 意图匹配度 |
| γ | 0.2 | 标签匹配度 |
| δ | 0.1 | 流行度（下载次数） |
| ε | 0.05 | 用户评分 |

### 5.2 各维度计算

**1. 向量相似度**：
```
vector_score = 1 - (skill_embedding <=> query_embedding)
范围：[0, 1]
```

**2. 意图匹配度**：
```
intent_match_score = 1.0  # 如果技能类别匹配意图
                    0.7  # 如果技能类别在意图的相关类别中
                    0.3  # 其他
```

**3. 标签匹配度**：
```
tag_match_score = min(tag_count / max_user_tags, 1.0)
范围：[0, 1]
```

**4. 流行度**：
```
popularity_score = download_count / max_download_count
使用对数缩放：log(1 + download_count) / log(1 + max_download_count)
```

**5. 用户评分**：
```
rating_score = rating_avg / 5.0
范围：[0, 1]
```

### 5.3 重排序实现

```java
public class SkillReranker {

    private final double wVector = 0.4;
    private final double wIntent = 0.25;
    private final double wTag = 0.2;
    private final double wPopularity = 0.1;
    private final double wRating = 0.05;

    public List<SkillMatchResult> rerank(
        List<SkillCandidate> candidates,
        IntentClassificationResult intent,
        Set<String> userTags,
        double maxDownloadCount
    ) {
        // 计算每个候选技能的综合分数
        return candidates.stream()
            .map(candidate -> calculateScore(candidate, intent, userTags, maxDownloadCount))
            .sorted(Comparator.comparingDouble(SkillMatchResult::finalScore).reversed())
            .collect(Collectors.toList());
    }

    private SkillMatchResult calculateScore(
        SkillCandidate candidate,
        IntentClassificationResult intent,
        Set<String> userTags,
        double maxDownloadCount
    ) {
        double vectorScore = candidate.vectorSimilarity();
        double intentScore = calculateIntentScore(candidate, intent);
        double tagScore = calculateTagScore(candidate, userTags);
        double popularityScore = Math.log(1 + candidate.downloadCount())
            / Math.log(1 + maxDownloadCount);
        double ratingScore = candidate.ratingAvg() / 5.0;

        double finalScore = wVector * vectorScore
            + wIntent * intentScore
            + wTag * tagScore
            + wPopularity * popularityScore
            + wRating * ratingScore;

        return new SkillMatchResult(
            candidate.skillId(),
            candidate.name(),
            candidate.summary(),
            vectorScore,
            intentScore,
            tagScore,
            finalScore,
            buildMatchReason(vectorScore, intentScore, tagScore)
        );
    }
}
```

## 6. 缓存算法

### 6.1 查询结果缓存

**缓存键**：
```
cache_key = "agent_search:" +
            hash(query_text + ":" +
                 intent_label + ":" +
                 sorted(user_tags) + ":" +
                 user_role + ":" +
                 scenario_description)
```

**TTL**：5 分钟

### 6.2 嵌入向量缓存

**缓存策略**：
- 技能嵌入：永久缓存（版本更新时失效）
- 意图嵌入：永久缓存（意图更新时失效）
- 查询嵌入：TTL 1 小时

## 7. 降级策略

| 场景 | 降级方案 |
|------|----------|
| OpenAI API 调用失败 | 使用本地哈希向量 |
| 向量索引未就绪 | 使用全文搜索 |
| 意图识别失败 | 使用默认意图 |
| 响应超时 | 返回部分结果 |

## 8. 性能优化

### 8.1 向量索引调优

```sql
-- ivfflat 索引参数
-- lists = sqrt(行数)
-- 1000 行：lists = 32
-- 10000 行：lists = 100
-- 100000 行：lists = 316
```

### 8.2 查询优化

1. **限制候选集**：向量检索先返回 Top-200，再过滤
2. **异步处理**：非阻塞式嵌入生成
3. **批量查询**：减少数据库往返

### 8.3 批量向量化

```python
def batch_embed(texts, batch_size=100):
    """批量生成嵌入向量"""
    all_embeddings = []
    for i in range(0, len(texts), batch_size):
        batch = texts[i:i + batch_size]
        response = openai.Embedding.create(
            model="text-embedding-3-small",
            input=batch
        )
        all_embeddings.extend([item['embedding'] for item in response['data']])
    return all_embeddings
```
