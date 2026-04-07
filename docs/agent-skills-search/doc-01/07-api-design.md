# Agent Skills 智能检索系统 - API 设计

## 1. 概述

Agent Skills 智能检索系统提供 RESTful API，支持 Agent 进行智能技能检索和会话管理。

## 2. API 基础信息

### 2.1 基础 URL

```
生产环境: https://api.skillhub.example.com/api/agent
开发环境: http://localhost:8080/api/agent
```

### 2.2 认证方式

使用 Bearer Token 认证：

```
Authorization: Bearer {access_token}
```

### 2.3 通用响应格式

**成功响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "timestamp": "2024-04-05T10:30:00Z"
}
```

**错误响应**：
```json
{
  "code": 40001,
  "message": "Invalid request parameter",
  "details": {
    "field": "query",
    "error": "Query text cannot be empty"
  },
  "timestamp": "2024-04-05T10:30:00Z"
}
```

## 3. 会话管理 API

### 3.1 创建会话

**请求**：
```
POST /api/agent/sessions
```

**请求体**：
```json
{
  "agentId": "agent-001",
  "userId": "user-123",
  "scenarioDescription": "Web 前端开发",
  "userRole": "frontend-developer",
  "metadata": {
    "projectId": "project-456",
    "environment": "development"
  }
}
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": "sess-789-xyz",
    "agentId": "agent-001",
    "userId": "user-123",
    "scenarioDescription": "Web 前端开发",
    "userRole": "frontend-developer",
    "metadata": {
      "projectId": "project-456",
      "environment": "development"
    },
    "startedAt": "2024-04-05T10:30:00Z",
    "updatedAt": "2024-04-05T10:30:00Z"
  }
}
```

### 3.2 获取会话

**请求**：
```
GET /api/agent/sessions/{sessionId}
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": "sess-789-xyz",
    "agentId": "agent-001",
    "userId": "user-123",
    "scenarioDescription": "Web 前端开发",
    "userRole": "frontend-developer",
    "messageCount": 5,
    "startedAt": "2024-04-05T10:30:00Z",
    "updatedAt": "2024-04-05T10:35:00Z",
    "endedAt": null
  }
}
```

### 3.3 获取会话历史

**请求**：
```
GET /api/agent/sessions/{sessionId}/messages?limit=10&offset=0
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "messages": [
      {
        "id": 1,
        "role": "user",
        "content": "我需要一个处理 JSON 数据的技能",
        "intentLabel": "data_processing",
        "intentConfidence": 0.92,
        "createdAt": "2024-04-05T10:30:00Z"
      },
      {
        "id": 2,
        "role": "assistant",
        "content": "我为您找到了几个相关技能...",
        "createdAt": "2024-04-05T10:30:05Z"
      }
    ],
    "total": 5,
    "limit": 10,
    "offset": 0
  }
}
```

### 3.4 结束会话

**请求**：
```
POST /api/agent/sessions/{sessionId}/end
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": "sess-789-xyz",
    "endedAt": "2024-04-05T11:00:00Z"
  }
}
```

## 4. 技能搜索 API

### 4.1 智能技能搜索

**请求**：
```
POST /api/agent/skills/search
```

**请求体**：
```json
{
  "query": "我需要一个处理 JSON 数据的技能",
  "sessionId": "sess-789-xyz",
  "limit": 10,
  "options": {
    "enableIntentRecognition": true,
    "enableContextAware": true,
    "tags": ["json", "data-processing"],
    "intent": "data_processing",
    "minConfidence": 0.3
  }
}
```

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | string | 是 | 搜索查询文本 |
| sessionId | string | 否 | 会话 ID，用于上下文感知 |
| limit | integer | 否 | 返回结果数量，默认 10 |
| options.enableIntentRecognition | boolean | 否 | 是否启用意图识别，默认 true |
| options.enableContextAware | boolean | 否 | 是否启用上下文感知，默认 true |
| options.tags | string[] | 否 | 指定标签过滤 |
| options.intent | string | 否 | 指定意图（跳过自动识别） |
| options.minConfidence | number | 否 | 最小置信度，默认 0.3 |

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "query": "我需要一个处理 JSON 数据的技能",
    "intent": {
      "label": "data_processing",
      "confidence": 0.92,
      "alternatives": [
        {
          "label": "file_processing",
          "confidence": 0.75
        }
      ]
    },
    "results": [
      {
        "skillId": 123,
        "name": "json-parser",
        "displayName": "JSON Parser",
        "namespace": "default",
        "summary": "强大的 JSON 解析和处理库",
        "version": "1.2.3",
        "scores": {
          "vectorSimilarity": 0.89,
          "intentMatchScore": 1.0,
          "tagMatchScore": 0.8,
          "popularityScore": 0.65,
          "ratingScore": 0.88,
          "finalScore": 0.87
        },
        "matchReason": "高度匹配：查询向量相似度 0.89，意图完全匹配，标签匹配度 80%",
        "tags": ["json", "parser", "data"],
        "downloadCount": 1520,
        "ratingAvg": 4.4,
        "ratingCount": 45
      },
      {
        "skillId": 456,
        "name": "data-transformer",
        "displayName": "Data Transformer",
        "namespace": "utils",
        "summary": "通用数据转换工具",
        "version": "2.1.0",
        "scores": {
          "vectorSimilarity": 0.76,
          "intentMatchScore": 1.0,
          "tagMatchScore": 0.6,
          "popularityScore": 0.45,
          "ratingScore": 0.8,
          "finalScore": 0.72
        },
        "matchReason": "良好匹配：意图完全匹配，部分标签匹配",
        "tags": ["data", "transform"],
        "downloadCount": 890,
        "ratingAvg": 4.0,
        "ratingCount": 28
      }
    ],
    "total": 45,
    "limit": 10,
    "processingTimeMs": 245
  }
}
```

### 4.2 快速搜索（简化版）

**请求**：
```
GET /api/agent/skills/search?q={query}&limit={limit}
```

**示例**：
```
GET /api/agent/skills/search?q=json%20parser&limit=5
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "query": "json parser",
    "results": [
      {
        "skillId": 123,
        "name": "json-parser",
        "displayName": "JSON Parser",
        "namespace": "default",
        "summary": "强大的 JSON 解析和处理库",
        "version": "1.2.3",
        "score": 0.87
      }
    ]
  }
}
```

## 5. 意图管理 API

### 5.1 获取所有意图

**请求**：
```
GET /api/agent/intents?activeOnly=true
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "intents": [
      {
        "id": 1,
        "label": "code_generation",
        "description": "代码生成相关技能",
        "skillCategories": ["coding", "development"],
        "exampleQueries": [
          "写一个函数",
          "生成代码",
          "创建类"
        ],
        "priority": 10,
        "active": true
      },
      {
        "id": 2,
        "label": "data_analysis",
        "description": "数据分析相关技能",
        "skillCategories": ["data", "analysis"],
        "exampleQueries": [
          "分析数据",
          "绘制图表",
          "数据统计"
        ],
        "priority": 10,
        "active": true
      }
    ]
  }
}
```

### 5.2 识别意图

**请求**：
```
POST /api/agent/intents/recognize
```

**请求体**：
```json
{
  "query": "我需要生成一个处理用户认证的函数"
}
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "primaryIntent": {
      "label": "code_generation",
      "confidence": 0.88
    },
    "alternativeIntents": [
      {
        "label": "authentication",
        "confidence": 0.72
      },
      {
        "label": "general",
        "confidence": 0.15
      }
    ],
    "processingTimeMs": 85
  }
}
```

## 6. 批量操作 API

### 6.1 批量生成技能嵌入

**请求**：
```
POST /api/agent/skills/embeddings/batch
```

**请求体**：
```json
{
  "skillIds": [123, 456, 789],
  "embeddingTypes": ["description", "combined"]
}
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "task-001-xyz",
    "status": "processing",
    "totalCount": 3,
    "successCount": 0,
    "failureCount": 0,
    "estimatedCompletion": "2024-04-05T10:35:00Z"
  }
}
```

### 6.2 查询批量任务状态

**请求**：
```
GET /api/agent/tasks/{taskId}
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "task-001-xyz",
    "status": "completed",
    "totalCount": 3,
    "successCount": 3,
    "failureCount": 0,
    "results": [
      {
        "skillId": 123,
        "status": "success",
        "embeddingTypes": ["description", "combined"]
      }
    ],
    "startedAt": "2024-04-05T10:30:00Z",
    "completedAt": "2024-04-05T10:31:15Z"
  }
}
```

## 7. 错误码

| 错误码 | 说明 | HTTP 状态码 |
|--------|------|-------------|
| 0 | 成功 | 200 |
| 40001 | 请求参数错误 | 400 |
| 40002 | 缺少必填参数 | 400 |
| 40101 | 未认证 | 401 |
| 40301 | 无权限访问 | 403 |
| 40401 | 资源不存在 | 404 |
| 40901 | 资源冲突 | 409 |
| 42901 | 请求频率超限 | 429 |
| 50001 | 服务器内部错误 | 500 |
| 50301 | 服务不可用 | 503 |
| 50002 | OpenAI API 调用失败 | 500 |
| 50003 | 向量索引不可用 | 500 |

## 8. 速率限制

| 端点 | 限制 | 时间窗口 |
|------|------|----------|
| POST /api/agent/skills/search | 100 | 1分钟 |
| GET /api/agent/sessions/{id} | 200 | 1分钟 |
| POST /api/agent/sessions | 50 | 1分钟 |
| POST /api/agent/intents/recognize | 60 | 1分钟 |

**响应头**：
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1712289000
```

## 9. Webhook 通知

### 9.1 向量生成完成通知

**请求体**：
```json
{
  "eventType": "embedding.completed",
  "timestamp": "2024-04-05T10:30:00Z",
  "data": {
    "skillId": 123,
    "skillVersionId": 456,
    "embeddingTypes": ["description", "combined"],
    "status": "success"
  }
}
```

## 10. SDK 示例

### 10.1 JavaScript/TypeScript

```typescript
import { SkillHubAgentClient } from '@skillhub/agent-sdk';

const client = new SkillHubAgentClient({
  baseURL: 'https://api.skillhub.example.com/api/agent',
  apiKey: 'your-api-key'
});

// 创建会话
const session = await client.createSession({
  agentId: 'agent-001',
  userId: 'user-123',
  scenarioDescription: 'Web 前端开发',
  userRole: 'frontend-developer'
});

// 搜索技能
const results = await client.searchSkills({
  query: '我需要一个处理 JSON 数据的技能',
  sessionId: session.sessionId,
  limit: 10
});

console.log(results.results);
```

### 10.2 Python

```python
from skillhub_agent import SkillHubAgentClient

client = SkillHubAgentClient(
    base_url='https://api.skillhub.example.com/api/agent',
    api_key='your-api-key'
)

# 创建会话
session = client.create_session(
    agent_id='agent-001',
    user_id='user-123',
    scenario_description='Web 前端开发',
    user_role='frontend-developer'
)

# 搜索技能
results = client.search_skills(
    query='我需要一个处理 JSON 数据的技能',
    session_id=session['sessionId'],
    limit=10
)

print(results['results'])
```

### 10.3 Java

```java
import com.iflytek.skillhub.agent.SkillHubAgentClient;
import com.iflytek.skillhub.agent.dto.*;

SkillHubAgentClient client = new SkillHubAgentClient(
    "https://api.skillhub.example.com/api/agent",
    "your-api-key"
);

// 创建会话
CreateSessionRequest sessionRequest = CreateSessionRequest.builder()
    .agentId("agent-001")
    .userId("user-123")
    .scenarioDescription("Web 前端开发")
    .userRole("frontend-developer")
    .build();

AgentSession session = client.createSession(sessionRequest);

// 搜索技能
SearchSkillsRequest searchRequest = SearchSkillsRequest.builder()
    .query("我需要一个处理 JSON 数据的技能")
    .sessionId(session.getSessionId())
    .limit(10)
    .build();

SearchSkillsResponse results = client.searchSkills(searchRequest);

results.getResults().forEach(skill -> {
    System.out.println(skill.getName() + ": " + skill.getFinalScore());
});
```
