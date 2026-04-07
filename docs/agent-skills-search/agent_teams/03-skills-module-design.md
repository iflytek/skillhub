# Skills 模块设计方案

## 1. Skill 数据模型设计

### 1.1 核心数据表结构

#### skills 表
| 字段名 | 类型 | 说明 | 索引 |
|--------|------|------|------|
| id | uuid | 主键 | PK |
| name | varchar(255) | Skill 名称 | |
| description | text | Skill 描述 | |
| embedding | vector(1536) | 语义向量（基于 description 生成） | pgvector |
| category_id | uuid | 所属分类 | FK |
| is_active | boolean | 是否启用 | |
| created_at | timestamp | 创建时间 | |
| updated_at | timestamp | 更新时间 | |

#### skill_tags 表
| 字段名 | 类型 | 说明 | 索引 |
|--------|------|------|------|
| id | uuid | 主键 | PK |
| skill_id | uuid | 关联的 skill | FK |
| tag | varchar(100) | 标签名称 | |
| tag_type | varchar(50) | 标签类型（domain/role/action） | |

#### skill_metadata 表
| 字段名 | 类型 | 说明 | 索引 |
|--------|------|------|------|
| id | uuid | 主键 | PK |
| skill_id | uuid | 关联的 skill | FK |
| key | varchar(100) | 元数据键 | |
| value | text | 元数据值（JSON 格式） | |

#### skill_categories 表
| 字段名 | 类型 | 说明 | 索引 |
|--------|------|------|------|
| id | uuid | 主键 | PK |
| name | varchar(100) | 分类名称 | |
| parent_id | uuid | 父分类 | FK |
| level | int | 分类层级 | |

### 1.2 向量化方案

#### 向量化内容来源
1. **主要向量**：基于 Skill 的 `description` 字段
2. **增强向量**：合并 `name` + `description` + `tags` 生成更丰富的语义表示
3. **多维度向量**：针对不同场景生成独立向量（如：技术栈向量、业务场景向量）

#### 向量维度选择
- 推荐维度：1536（OpenAI text-embedding-3-small/large）
- 备选维度：768（轻量级场景）
- 存储类型：`vector(1536)` 或 `vector(768)`

#### 向量更新策略
- 创建 Skill 时自动生成向量
- 当 `description`、`name`、`tags` 变更时，异步重新计算向量
- 支持手动触发向量重新生成

## 2. Skill 标签和元数据结构

### 2.1 标签分类体系

#### Domain 标签（领域标签）
- 描述 Skill 所属业务领域
- 示例：`database`、`api`、`auth`、`ui`、`testing`、`deployment`

#### Role 标签（角色标签）
- 描述适合使用该 Skill 的角色
- 示例：`developer`、`devops`、`analyst`、`designer`、`manager`

#### Action 标签（动作标签）
- 描述 Skill 的主要动作类型
- 示例：`create`、`read`、`update`、`delete`、`analyze`、`deploy`

#### Tech Stack 标签（技术栈标签）
- 描述 Skill 涉及的技术栈
- 示例：`python`、`java`、`react`、`kubernetes`、`postgresql`

### 2.2 元数据结构设计

#### 标准元数据字段
```json
{
  "complexity": "low|medium|high",
  "estimated_time": "5m",
  "dependencies": ["skill_id_1", "skill_id_2"],
  "required_permissions": ["read_code", "write_files"],
  "compatibility": {
    "min_agent_version": "1.0.0",
    "supported_models": ["claude-sonnet-4.6", "claude-opus-4.6"]
  },
  "usage_stats": {
    "total_calls": 1000,
    "success_rate": 0.95
  }
}
```

#### 扩展元数据
- 自定义字段支持 JSON 格式存储
- 支持元数据查询和过滤

## 3. Skill 匹配算法设计

### 3.1 匹配流程架构

```
输入：查询上下文（意图、历史、场景、用户角色）
    ↓
阶段 1：规则过滤
    ├── 活跃状态过滤（is_active = true）
    ├── 角色权限过滤
    ├── 技术栈兼容性过滤
    └── 标签前置过滤
    ↓
阶段 2：语义相似度计算
    ├── 查询向量生成（意图 + 场景 + 上下文）
    ├── 向量相似度检索（ANN 搜索）
    └── 候选集排序
    ↓
阶段 3：多维度评分
    ├── 语义相似度分（40%）
    ├── 标签匹配分（30%）
    ├── 历史使用频率分（20%）
    └── 上下文相关性分（10%）
    ↓
阶段 4：结果重排
    ├── 多样性保证（避免重复类型）
    ├── 相关性阈值过滤
    └── Top-K 返回
    ↓
输出：匹配的 Skills 列表（带评分）
```

### 3.2 查询向量生成策略

#### 组合查询向量
将以下内容合并生成查询向量：
1. **当前意图文本**：用户当前的请求意图
2. **场景描述**：当前工作场景的上下文描述
3. **对话历史摘要**：最近 N 轮对话的语义摘要
4. **用户角色信息**：用户角色相关的描述文本

#### 向量生成方式
- 方式 A：直接拼接文本，生成单一向量
- 方式 B：分别生成向量后加权平均
- 方式 C：生成多个独立向量，分别检索后合并结果

### 3.3 相似度计算方法

#### 向量相似度
- 使用余弦相似度（Cosine Similarity）
- 公式：`similarity = (A · B) / (||A|| × ||B||)`
- pgvector 操作符：`<=>`（余弦距离，越小越相似）

#### 标签匹配度
- 精确匹配：标签完全一致
- 层次匹配：父子标签层级匹配
- 权重计算：不同标签类型设置不同权重

#### 上下文相关性
- 基于对话历史中的 Skill 使用模式
- 计算 Skill 与历史上下文的关联度

### 3.4 多维度评分公式

```
Total Score = w1 × SemanticScore + w2 × TagScore + w3 × HistoryScore + w4 × ContextScore

其中：
- w1 = 0.4（语义相似度权重）
- w2 = 0.3（标签匹配权重）
- w3 = 0.2（历史使用权重）
- w4 = 0.1（上下文相关性权重）
```

## 4. 场景描述处理策略

### 4.1 场景描述来源

1. **显式场景描述**：用户提供的场景文本
2. **隐式场景推断**：从对话历史和上下文自动提取
3. **环境上下文**：当前工作目录、文件类型、Git 状态等

### 4.2 场景描述处理流程

```
原始场景输入
    ↓
文本预处理
    ├── 去除噪声和冗余
    ├── 提取关键实体
    └── 识别技术关键词
    ↓
场景分类
    ├── 开发场景（coding/debugging/refactoring）
    ├── 部署场景（deploy/monitor/maintenance）
    └── 分析场景（review/analysis/report）
    ↓
场景增强
    ├── 关联历史相似场景
    ├── 添加相关技术标签
    └── 生成场景向量
    ↓
输出：结构化场景描述
```

### 4.3 场景描述缓存策略

- 热门场景描述缓存（LRU 策略）
- 场景向量预计算
- 定期清理过期缓存

## 5. pgvector 索引策略和检索优化

### 5.1 索引类型选择

#### HNSW 索引（推荐）
- 优点：查询速度快，适合大规模数据
- 适用场景：实时检索，查询频繁
- 参数配置：
  - `m = 16`（每个节点的连接数）
  - `ef_construction = 64`（构建时的搜索宽度）

```sql
CREATE INDEX idx_skills_embedding_hnsw
ON skills USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```

#### IVFFlat 索引
- 优点：构建速度快，内存占用低
- 适用场景：数据更新频繁
- 参数配置：
  - `lists = 100`（聚类中心数量）

```sql
CREATE INDEX idx_skills_embedding_ivfflat
ON skills USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);
```

### 5.2 检索优化策略

#### 批量检索优化
- 使用 `array_agg` 批量获取结果
- 减少 SQL 查询次数

#### 预过滤优化
- 先用规则过滤缩小候选集
- 再在候选集上进行向量检索

#### 查询参数调优
- `ef_search`：控制搜索精度与速度的权衡
- 推荐值：`ef_search = 40`（平衡性能）

```sql
SET hnsw.ef_search = 40;
```

#### 分页优化
- 使用游标分页代替 OFFSET
- 避免深度分页性能问题

### 5.3 索引维护策略

#### 定期重建索引
- 当数据变更超过 20% 时重建 HNSW 索引
- 使用 `REINDEX INDEX` 命令

#### 向量更新优化
- 批量更新向量，减少索引重建频率
- 使用异步任务处理向量计算

#### 索引监控
- 监控索引大小和查询性能
- 定期收集统计信息

```sql
ANALYZE skills;
```

### 5.4 查询性能优化

#### 查询计划分析
- 使用 `EXPLAIN ANALYZE` 分析查询性能
- 识别性能瓶颈并优化

#### 结果缓存
- 缓存热门查询结果
- 设置合理的缓存过期时间

#### 连接池配置
- 合理配置数据库连接池大小
- 避免连接频繁创建和销毁

## 6. 扩展性设计

### 6.1 插件化标签系统
- 支持自定义标签类型
- 支持标签权重动态配置

### 6.2 多语言向量支持
- 支持不同语言的向量嵌入
- 支持多语言混合查询

### 6.3 A/B 测试框架
- 支持不同匹配算法的对比测试
- 支持实时算法切换

### 6.4 可观测性
- 记录匹配过程的详细日志
- 统计各项指标（召回率、准确率、响应时间）
- 支持算法效果分析

---

**设计版本**：v1.0
**设计日期**：2026-04-05
**设计人**：Skills 开发工程师
