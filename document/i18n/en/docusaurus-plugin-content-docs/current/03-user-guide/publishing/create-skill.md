---
title: Create Skill Package
sidebar_position: 1
description: Learn how to create a compliant skill package
---

# Create Skill Package

## Skill Package Structure

A standard SkillHub skill package structure looks like this:

```
my-skill/
├── SKILL.md              # Main entry file (required)
├── references/           # References (optional)
├── scripts/              # Scripts (optional)
└── assets/               # Static assets (optional)
```

## SKILL.md Format

SKILL.md is the main entry file of a skill package, using YAML frontmatter + Markdown body format:

```markdown
---
name: my-skill
description: One sentence describing what this skill is for
x-astron-category: code-review
---

# Skill Description

Detailed skill description goes here...
```

### Frontmatter Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Skill identifier, kebab-case format |
| `description` | Yes | Brief skill description |
| `x-astron-category` | No | Category tag |
| `x-astron-runtime` | No | Runtime requirement |
| `x-astron-min-version` | No | Minimum version requirement |

## File Limits

- Single file size: Max 1MB
- Total package size: Max 10MB
- File count: Max 100
- Allowed file types: `.md`, `.txt`, `.json`, `.yaml`, `.yml`, `.js`, `.ts`, `.py`, `.sh`, `.png`, `.jpg`, `.svg`

## Next Steps

- [Publish Workflow](./publish) - Publish skill package
