---
title: 认证配置
sidebar_position: 1
description: 配置用户认证方式
---

# 认证配置

SkillHub 支持多种认证方式，满足不同企业的安全需求。

## OAuth2 登录

### GitHub OAuth

1. 在 GitHub 创建 OAuth App
2. 配置环境变量：
   ```bash
   OAUTH2_GITHUB_CLIENT_ID=your-client-id
   OAUTH2_GITHUB_CLIENT_SECRET=your-client-secret
   ```

### 扩展 OAuth Provider

架构支持扩展其他 OAuth Provider，如 GitLab、Gitee 等。

## 本地账号登录

开发环境支持本地账号登录，生产环境默认关闭。

## 企业 SSO 集成

支持通过扩展点集成企业 SSO（SAML/OIDC）。

## 下一步

- [权限管理](./authorization) - 配置权限控制
