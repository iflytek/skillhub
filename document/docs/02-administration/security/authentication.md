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

### 钉钉 OAuth2

1. 在[钉钉开放平台](https://open-dev.dingtalk.com/)创建 H5 微应用，获取 AppKey 和 AppSecret
2. 开通 `Contact.User.Read` 权限（获取用户信息）
3. 发布应用版本以激活 OAuth2 凭证
4. 回调地址填写 `{baseUrl}/login/oauth2/code/dingtalk`
5. 配置环境变量：
   ```bash
   OAUTH2_DINGTALK_CLIENT_ID=你的AppKey
   OAUTH2_DINGTALK_CLIENT_SECRET=你的AppSecret
   ```

> 钉钉使用 `corpid` scope（非标准 OIDC `openid`），用户以 `unionId` 作为唯一标识。

### 扩展 OAuth Provider

架构支持扩展其他 OAuth Provider，如 GitLab、Gitee 等。

## 本地账号登录

开发环境支持本地账号登录，生产环境默认关闭。

## 企业 SSO 集成

支持通过扩展点集成企业 SSO（SAML/OIDC）。

## 下一步

- [权限管理](./authorization) - 配置权限控制
