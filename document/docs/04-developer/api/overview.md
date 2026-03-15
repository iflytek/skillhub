---
title: API 概述
sidebar_position: 1
description: SkillHub API 概述
---

# API 概述

SkillHub 提供 RESTful API 用于集成和自动化。

## API 分类

### 公开 API
- 技能搜索
- 技能详情
- 版本列表
- 下载技能
- 无需认证（PUBLIC 技能）

### 认证 API
- 发布技能
- 收藏/评分
- 命名空间管理
- 需要登录或 Bearer Token

### CLI 兼容层
- 兼容 ClawHub CLI 协议
- 现有工具可无缝迁移

## 响应格式

### 统一响应结构

```json
{
  "code": 0,
  "msg": "成功",
  "data": {},
  "timestamp": "2026-03-15T06:00:00Z",
  "requestId": "req-123"
}
```

### 分页响应

```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "items": [],
    "total": 100,
    "page": 1,
    "size": 20
  },
  "timestamp": "2026-03-15T06:00:00Z",
  "requestId": "req-123"
}
```

## 认证方式

### Session Cookie
Web 端使用 Session Cookie 认证。

### Bearer Token
CLI 和 API 集成使用 Bearer Token：

```bash
Authorization: Bearer <token>
```

### API Token
可创建长期有效的 API Token 用于自动化。

## 幂等性

所有写操作支持 `X-Request-Id` 请求头实现幂等：

```bash
X-Request-Id: <uuid-v4>
```

## 下一步

- [公开 API](./public) - 查看公开接口
