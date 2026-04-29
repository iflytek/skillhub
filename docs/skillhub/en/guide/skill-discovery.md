# Skill Search & Discovery

This page focuses on how to find and use the right skill packages. Search API details and storage-engine internals are intentionally left out here.

## Core Capabilities

- Full-text search
- Filtering by namespace, label, and sort order
- Permission-aware results
- Both Web and CLI entry points

## Common Paths

### Web UI

1. Open `/search`
2. Enter a keyword
3. Apply namespace, label, and sort filters as needed
4. Open the skill detail page to inspect versions, files, and install options

### CLI

```bash
export CLAWHUB_REGISTRY=http://localhost:8080

npx clawhub search pdf
npx clawhub install pdf-parser
npx clawhub install my-team--pdf-parser
```

## What to Look At

- Whether the title and summary match your intended use case
- Whether the namespace is trustworthy
- Whether the package has been updated recently enough
- Signals such as downloads, stars, and ratings
- Any review or security warnings on the detail page

## Namespaces and Coordinates

- Global skills are often installable directly by slug
- Namespace-scoped skills usually use the `<namespace>--<skill>` form
- Web and CLI results may differ depending on permission scope

## Usage Advice

- New team members should browse namespaces and popular skills first
- Read the detail page before installing similarly named packages
- Keep label naming conventions consistent across your team

## Continue Reading

- [Skill Publishing & Versioning](/en/guide/skill-publish)
- [Namespace & Team Management](/en/guide/namespace)
- [Social & Interaction](/en/guide/social)
