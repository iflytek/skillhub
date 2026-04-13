# OpenClaw Integration Guide

This document explains how to configure OpenClaw CLI to connect to a SkillHub private registry for publishing, searching, and downloading skills.
> Not only applicable to Openclaw, but also compatible with other CLI Coding Agents (Claude Code, OpenCode, Qcoder, etc.) or Agent assistants (Nanobot, CoPaw, etc.) by specifying the installation directory.

## Overview

SkillHub provides a ClawHub-compatible API layer, allowing OpenClaw CLI to seamlessly integrate with private registries. With simple configuration, you can:

- 🔍 Search for private skills within your organization
- 📥 Download and install skill packages
- 📤 Publish new skills to the private registry
- ⭐ Star and rate skills

## Quick Start

### 1. Configure Registry URL

Set the SkillHub registry address in your OpenClaw configuration:

```bash
# Via environment variable (temporary)
export CLAWHUB_REGISTRY=https://skillhub.your-company.com
```

### 2. Authentication (Optional)

For **global namespace (@global) PUBLIC skills**, no login is required to download. Authentication is required for:

- Team namespace skills (regardless of visibility)
- NAMESPACE_ONLY or PRIVATE skills
- Write operations like publishing, starring, etc.

```bash
# Log in with an API token
npx clawhub login --token YOUR_API_TOKEN
# If you have installed clawhub via npm i -g clawhub, you can use clawhub directly instead of npx clawhub for all commands in this document

# View current logged in user
npx clawhub whoami

# Log out current user
npx clawhub logout

# View help
npx clawhub --help
```

#### Obtaining an API Token

1. Log in to SkillHub Web UI
2. Navigate to **Settings → API Tokens**
3. Click **Create New Token**
4. Set token name and permissions
5. Copy the generated token

### 3. Search/Explore/View Skills

```bash
# Search, display all matching skills
npx clawhub search <skill-name>
# Search, display first 5 results
npx clawhub search <skill-name> --limit 5
# Show skill details
npx clawhub inspect <skill-name>
# Explore latest skills
npx clawhub explore
npx clawhub explore --limit 20    # First 20

# Examples
npx clawhub search find-skills
npx clawhub search find-skills --limit 5
npx clawhub inspect find-skills

# Help
npx clawhub search --help
npx clawhub inspect --help
```

### 4. Install/Update/Uninstall Skills

```bash
# Install
npx clawhub install <skill-name>
npx clawhub install <skill-name> --version <version number>   # Specific version
npx clawhub install <skill-name> --force                      # Overwrite existing
npx clawhub --dir <install-path> install <skill-name>         # Specific directory

# Update
npx clawhub update <skill-name>
npx clawhub update --all

# Uninstall
npx clawhub uninstall <skill-name>

# View installed skills
npx clawhub list

# Claude Code Installation Skill Example
npx clawhub --dir ~/.claude/skills install find-skills
CLAWHUB_WORKDIR=~/.claude/skills npx clawhub install find-skills

# Help
npx clawhub install --help
npx clawhub update --help
npx clawhub uninstall --help
npx clawhub list --help
```

### 5. Publish Skills

```bash
# Publish to the global namespace (requires appropriate permissions)
npx clawhub publish ./my-skill --slug my-skill --name "My Skill" --version 1.0.0

# Publish to a team namespace such as my-space
npx clawhub publish ./my-skill --slug my-space--my-skill --name "My Skill" --version 1.0.0
npx clawhub sync --all # Upload all skills in current folder

# Help
npx clawhub publish --help
npx clawhub sync --help
```

Notes:
- `my-space--my-skill` is the canonical compatibility slug. SkillHub parses it as namespace `my-space` plus skill slug `my-skill`
- To avoid mismatches between CLI display text and the final persisted coordinate, keep the `name` in `SKILL.md` aligned with the canonical slug suffix

## API Endpoints

SkillHub compatibility layer provides the following endpoints:

| Endpoint | Method | Description | Auth Required |
|----------|--------|-------------|---------------|
| `/api/v1/whoami` | GET | Get current user info | Required |
| `/api/v1/search` | GET | Search skills | Optional |
| `/api/v1/resolve` | GET | Resolve skill version | Optional |
| `/api/v1/download/{slug}` | GET | Download skill (redirect) | Optional* |
| `/api/v1/download` | GET | Download skill (query params) | Optional* |
| `/api/v1/skills/{slug}` | GET | Get skill details | Optional |
| `/api/v1/skills/{slug}/star` | POST | Star a skill | Required |
| `/api/v1/skills/{slug}/unstar` | DELETE | Unstar a skill | Required |
| `/api/v1/publish` | POST | Publish a skill | Required |

Notes:
- The compatibility layer may still expose the term "latest" externally, but it must strictly mean "latest published version"
- Internally, compat responses should map from the unified lifecycle projection's `publishedVersion` rather than inferring an ad hoc "current version"

\* Download endpoint authentication requirements:
- **Global namespace (@global) PUBLIC skills**: No authentication required
- **All team namespace skills**: Authentication required
- **NAMESPACE_ONLY and PRIVATE skills**: Authentication required

## Skill Visibility Levels

SkillHub supports three visibility levels with the following download permission rules:

### PUBLIC
- ✅ Anyone can search and view
- ✅ **Global namespace (@global)**: No login required to download
- 🔒 **Team namespaces**: Authentication required to download
- 📍 Suitable for organization-wide, publicly shareable skills

### NAMESPACE_ONLY
- ✅ Namespace members can search and view
- 🔒 Login required and must be namespace member to download
- 📍 Suitable for team-internal skills

### PRIVATE
- ✅ Only owner can view
- 🔒 Login required and must be owner to download
- 📍 Suitable for skills under personal development

**Important Notes**:
- Global namespace (`@global`) PUBLIC skills support anonymous downloads for wide distribution within the organization
- All team namespace skills (including PUBLIC) require authentication to ensure team boundary security

## Canonical Slug Mapping

SkillHub internally uses `@{namespace}/{skill}` format, but the compatibility layer automatically converts to ClawHub-style canonical slugs:

| SkillHub Internal | Canonical Slug | Description |
|-------------------|----------------|-------------|
| `@global/my-skill` | `my-skill` | Global namespace skill |
| `@my-team/my-skill` | `my-team--my-skill` | Team namespace skill |

OpenClaw CLI uses canonical slug format, and SkillHub handles the conversion automatically.

## Configuration Examples

### ClawHub CLI Environment Variables

ClawHub CLI is configured via environment variables:

```bash
# Registry configuration
export CLAWHUB_REGISTRY=https://skillhub.your-company.com

# Authenticate once if needed
clawhub login --token sk_your_api_token_here
```

### Environment Variables

```bash
# Registry configuration
export CLAWHUB_REGISTRY=https://skillhub.your-company.com

# Optionally log in before running authenticated commands
clawhub login --token sk_your_api_token_here
```

## FAQ

### Q: How do I switch back to public ClawHub?

```bash
# Unset custom registry
unset CLAWHUB_REGISTRY

# ClawHub CLI will use the default public registry
```

### Q: Getting 403 Forbidden when downloading?

Possible causes:
1. Skill belongs to a team namespace, authentication required
2. Skill is NAMESPACE_ONLY or PRIVATE, authentication required
3. You're not a member of the namespace
4. API Token has expired

Solution:
```bash
# Log in again with a new token
clawhub login --token YOUR_NEW_TOKEN

# Test connection
curl https://skillhub.your-company.com/api/v1/whoami \
  -H "Authorization: Bearer YOUR_NEW_TOKEN"
```

**Tip**: Global namespace (@global) PUBLIC skills can be downloaded anonymously without authentication.

### Q: How do I see all skills I have access to?

```bash
# Search all skills (filtered by permissions)
npx clawhub search ""
```

### Q: Permission denied when publishing?

- Publishing to global namespace (`@global`) requires `SUPER_ADMIN` permission
- Publishing to team namespace requires OWNER or ADMIN role in that namespace
- Contact your administrator for appropriate permissions

### Q: Which OpenClaw versions are supported?

SkillHub compatibility layer is designed to work with tools using ClawHub CLI. ClawHub CLI is distributed via npm:

```bash
# Install ClawHub CLI
npm install -g clawhub

# Or use npx directly
npx clawhub install my-skill
```

If you encounter compatibility issues, please file an issue.

## API Response Formats

### Search Response Example

```json
{
  "results": [
    {
      "slug": "my-team--email-sender",
      "name": "Email Sender",
      "description": "Send emails via SMTP",
      "author": {
        "handle": "user123",
        "displayName": "John Doe"
      },
      "version": "1.2.0",
      "downloadCount": 150,
      "starCount": 25,
      "createdAt": "2026-01-15T10:00:00Z",
      "updatedAt": "2026-03-10T14:30:00Z"
    }
  ],
  "total": 1,
  "page": 1,
  "limit": 20
}
```

### Version Resolution Response Example

```json
{
  "slug": "my-skill",
  "version": "1.2.0",
  "downloadUrl": "/api/v1/skills/global/my-skill/versions/1.2.0/download"
}
```

### Publish Response Example

```json
{
  "id": "12345",
  "version": {
    "id": "67890"
  }
}
```

## Security Recommendations

1. **Use HTTPS**: Always use HTTPS in production
2. **Token Management**:
   - Rotate API tokens regularly
   - Never hardcode tokens in code
   - Use environment variables or secret management tools
3. **Least Privilege**: Assign minimum required permissions to tokens
4. **Audit Logs**: Regularly review SkillHub audit logs

## Troubleshooting

### Enable Debug Logging

```bash
# View detailed request logs
DEBUG=clawhub:* npx clawhub search my-skill

# Or use verbose mode
npx clawhub --verbose install my-skill
```

### Test Connection

```bash
# Test registry connection
curl https://skillhub.your-company.com/api/v1/whoami \
  -H "Authorization: Bearer YOUR_TOKEN"

# Test search
curl "https://skillhub.your-company.com/api/v1/search?q=test"
```

## Further Reading

- [SkillHub API Design](./06-api-design.md)
- [Skill Protocol Specification](./07-skill-protocol.md)
- [Authentication & Authorization](./03-authentication-design.md)
- [Deployment Guide](./09-deployment.md)

## Support

For questions or suggestions:
- 📖 Full Documentation: https://zread.ai/iflytek/skillhub
- 💬 GitHub Discussions: https://github.com/iflytek/skillhub/discussions
- 🐛 Submit Issues: https://github.com/iflytek/skillhub/issues
