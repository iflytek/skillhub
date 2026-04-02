# Skill Publishing & Version Management

## Overview

Skill publishing is the core feature of SkillHub. Developers can upload locally developed Agent skill packages to the registry with one click. The system automatically handles version management, metadata extraction, and file indexing.

![Concept Diagram](/diagrams/skill-publish-concept.png)

**Problems Solved**:

Traditionally, team members distribute skill packages through Git repositories or file sharing. This approach has several pain points:

- **Version Chaos**: Different versions scattered everywhere, hard to track
- **Permission Control Lost**: Cannot finely control who can access which skill packages
- **Discovery Difficulty**: New members don't know what skills are available

SkillHub provides an npm-like publishing experience with enterprise-grade permission control and review mechanisms.

**Core Features**:

- **Semantic Versioning**: Supports `major.minor.patch` version specification
- **Tag System**: `latest`, `beta`, `stable` and custom tags
- **Multi-version Coexistence**: Same skill package can retain multiple historical versions
- **Version Resolution**: Smart parsing of version selectors (e.g., `^1.2.0`, `~2.0.0`)
- **File Browsing**: Browse skill package file structure online
- **Download Distribution**: Download by version or tag

## Use Cases

**Case 1: Developer Publishes New Skill**

You just completed a Claude Code skill package and want other team members to use it.

![Screenshot](/screenshots/homepage.png)

**Case 2: Version Iteration**

Skill package needs bug fixes or new features, publish new version while maintaining backward compatibility.

**Case 3: Beta Testing**

New features are not stable yet, publish with `beta` tag for limited testing before promoting to `latest`.

**Case 4: Version Rollback**

New version has serious issues, need to point `latest` tag to previous stable version.

## Steps

1. **Prepare Skill Package**

Ensure skill package conforms to SkillHub specification:
- Contains `skill.md` (skill description)
- Contains `package.json` or `SKILL.md` (metadata)
- Clear file structure, no sensitive information

2. **Publish via CLI (Recommended)**

```bash
# Configure registry
export CLAWHUB_REGISTRY=http://localhost:8080

# Publish to default namespace
npx clawhub publish ./my-skill

# Publish to specific namespace
npx clawhub publish ./my-skill --namespace my-team
```

3. **Publish via Web UI**

Visit `http://localhost:3000/dashboard/publish`, select namespace, upload zip file, choose visibility, and click "Publish".

4. **Publish via REST API**

```bash
POST /api/v1/skills/{namespace}/publish
Content-Type: multipart/form-data

file: skill-package.zip
visibility: PUBLIC
```

![Flow Diagram](/diagrams/skill-publish-flow.png)

5. **Security Scan**

After publishing, [Skill Scanner](/en/guide/scanner) automatically scans the skill package for potential security risks. Results appear on the skill package detail page.

6. **Wait for Review** (if namespace has review enabled)

Team admins receive review notifications and approve skill packages for official release.

7. **Publish Success**

Skill package can be discovered through search, others can download via CLI or Web UI.

## API Reference

**Publish Skill Package**:
```bash
POST /api/v1/skills/{namespace}/publish
Content-Type: multipart/form-data

# Parameters
file: MultipartFile (required)
visibility: PUBLIC | PRIVATE | INTERNAL (optional, default PUBLIC)
```

**Parameter Reference**:
| Parameter | Type | Description |
|-----------|------|-------------|
| namespace | string | Namespace slug (path parameter) |
| file | MultipartFile | Skill package zip file |
| visibility | enum | Visibility level: PUBLIC, PRIVATE, INTERNAL |

**Get Skill Details**:
```bash
GET /api/v1/skills/{namespace}/{slug}
```

**List Versions**:
```bash
GET /api/v1/skills/{namespace}/{slug}/versions?page=0&size=20
```

**Get Version Details**:
```bash
GET /api/v1/skills/{namespace}/{slug}/versions/{version}
```

**Download Specific Version**:
```bash
GET /api/v1/skills/{namespace}/{slug}/versions/{version}/download
```

**Download by Tag**:
```bash
GET /api/v1/skills/{namespace}/{slug}/tags/{tagName}/download
```

**Version Resolution**:
```bash
GET /api/v1/skills/{namespace}/{slug}/resolve?version=^1.2.0
```

## Notes

> **Version Specification**: SkillHub uses Semantic Versioning. Version format is `major.minor.patch`, e.g., `1.2.3`.

- **First Publish**: Version should start from `0.1.0` or `1.0.0`
- **Tag Management**: `latest` tag automatically points to the latest stable version
- **Review Process**: If namespace has review enabled, new versions need admin approval
- **File Size Limit**: Single skill package max 100MB (configurable)
- **Naming Convention**: Skill slug supports lowercase letters, numbers, hyphens, and Unicode characters
- **Version Immutability**: Published versions cannot be modified, only new versions can be published
