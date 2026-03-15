---
title: Skill Protocol
sidebar_position: 1
description: SKILL.md specification and skill package protocol
---

# Skill Protocol

## SKILL.md Specification

### Basic Format

```markdown
---
name: my-skill
description: When to use this skill
---

# Markdown Body

Skill instruction content...
```

### Required Fields

| Field | Description |
|-------|-------------|
| `name` | Skill identifier, kebab-case |
| `description` | Brief skill description |

### Extension Fields

| Field | Description |
|-------|-------------|
| `x-astron-category` | Category tag |
| `x-astron-runtime` | Runtime requirement |
| `x-astron-min-version` | Minimum version requirement |

## Skill Package Structure

```
my-skill/
├── SKILL.md              # Main entry file (required)
├── references/           # References (optional)
├── scripts/              # Scripts (optional)
└── assets/               # Static assets (optional)
```

## File Validation

- Root directory must contain `SKILL.md`
- File type whitelist
- Single file size limit: 1MB
- Total package size limit: 10MB
- File count limit: 100

## Client Installation Directory

Install by the following priority:

1. `./.agent/skills/`
2. `~/.agent/skills/`
3. `./.claude/skills/`
4. `~/.claude/skills/`

## Next Steps

- [Storage SPI](./storage-spi) - Extend storage backend
