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

### Extend OAuth Provider

The architecture supports extending to other OAuth providers like GitLab, Gitee, etc.

## Local Account Login

Local account login is supported in development environment, disabled by default in production.

## Enterprise SSO Integration

Supports integrating enterprise SSO (SAML/OIDC) through extension points.

## Next Steps

- [Authorization](./authorization) - Configure access control
