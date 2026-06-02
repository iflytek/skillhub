---
title: Authentication Configuration
sidebar_position: 1
description: Configure user authentication methods
---

# Authentication Configuration

SkillHub supports multiple authentication methods to meet different enterprise security requirements.

## OAuth2 Login

### GitHub OAuth

1. Create an OAuth App on GitHub
2. Configure environment variables:
   ```bash
   OAUTH2_GITHUB_CLIENT_ID=your-client-id
   OAUTH2_GITHUB_CLIENT_SECRET=your-client-secret
   ```

### DingTalk OAuth2

1. Create an H5 micro-app on [DingTalk Open Platform](https://open-dev.dingtalk.com/) and obtain AppKey and AppSecret
2. Enable the `Contact.User.Read` permission (required for fetching user info)
3. Publish the app version to activate OAuth2 credentials
4. Set the callback URL to `{baseUrl}/login/oauth2/code/dingtalk`
5. Configure environment variables:
   ```bash
   OAUTH2_DINGTALK_CLIENT_ID=your-appkey
   OAUTH2_DINGTALK_CLIENT_SECRET=your-appsecret
   ```

> DingTalk uses `corpid` scope (not standard OIDC `openid`). Users are identified by `unionId`.

### Extend OAuth Provider

The architecture supports extending to other OAuth providers like GitLab, Gitee, etc.

## Local Account Login

Local account login is supported in development environment, disabled by default in production.

## Enterprise SSO Integration

Supports integrating enterprise SSO (SAML/OIDC) through extension points.

## Next Steps

- [Authorization](./authorization) - Configure access control
