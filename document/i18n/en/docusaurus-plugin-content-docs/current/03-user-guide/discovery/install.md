---
title: Install & Use
sidebar_position: 2
description: Install and use skills
---

# Install & Use

## Install via CLI

### Install Latest Version

```bash
clawhub install @team/my-skill
```

### Install Specific Version

```bash
clawhub install @team/my-skill@1.2.0
```

### Install by Tag

```bash
clawhub install @team/my-skill@beta
```

### Install with ClawHub CLI

```bash
clawhub install my-skill
clawhub install team-name--my-skill
```

## Installation Directory

Install by the following priority:

| Priority | Path | Description |
|----------|------|-------------|
| 1 | `./.agent/skills/` | Project level, universal mode |
| 2 | `~/.agent/skills/` | Global level, universal mode |
| 3 | `./.claude/skills/` | Project level, Claude default |
| 4 | `~/.claude/skills/` | Global level, Claude default |

## Use in Claude Code

After installation, skills are automatically discovered and loaded by Claude Code.

## Next Steps

- [Ratings & Stars](./ratings) - Feedback and favorite skills
