# Skill Publishing & Versioning

This page focuses on the stable path for publishing skill packages into the registry. Low-level API details are intentionally not repeated here.

## Core Capabilities

- Semantic versioning
- Tags such as `latest`, `beta`, and `stable`
- Multiple versions of the same skill
- File browsing and download distribution
- Review and security scanning integration

## Before You Publish

Check the following first:

- The package structure is clear
- `skill.md` / `SKILL.md` and metadata are complete
- No plaintext secrets or irrelevant large files are included
- The version follows semantic versioning

## Recommended Flow

### CLI Publishing

```bash
export CLAWHUB_REGISTRY=http://localhost:8080

# Publish to the default namespace
npx clawhub publish ./my-skill

# Publish to a specific namespace
npx clawhub publish ./my-skill --namespace my-team
```

### Web UI Publishing

1. Open `/dashboard/publish`
2. Choose a namespace
3. Upload the zip package
4. Select visibility
5. Click “Publish”

## What Happens After Publishing

1. SkillHub receives and parses the package
2. A new version record is created
3. If security scanning is enabled, the package enters the scan pipeline
4. If the namespace requires review, it enters the review workflow
5. Once released, other users can search, inspect, and download it

## Versions and Tags

- New releases create new versions instead of mutating old ones
- `latest` should point to the recommended stable version
- `beta` is best reserved for staged testing
- If a version has issues, publish a fixed version rather than overwriting the old one

## Practical Notes

- Large packages and heavy dependencies increase scan and review time
- Namespace policy may affect when a skill becomes visible
- Published versions should be treated as immutable artifacts
- It is still worth doing a basic local verification before publishing

## Continue Reading

- [Skill Search & Discovery](/en/guide/skill-discovery)
- [Review & Governance](/en/guide/review)
- [Security Scanning](/en/guide/scanner)
