# Namespace & Team Management

Namespaces are the core organizational unit in SkillHub. They define team boundaries, permission scope, and governance ownership.

## Role Model

| Role | Typical Responsibility |
|------|------------------------|
| Owner | Full control of the namespace and its members |
| Admin | Manage members, review skills, maintain settings |
| Member | Publish and use skills inside the namespace |

## Status Model

- `Active`: normal operation
- `Frozen`: new publishing is blocked
- `Archived`: hidden from the main discovery path and no longer run as an active space

## Daily Operations

### Create a Namespace

1. Open `/dashboard/namespaces`
2. Create the namespace and fill in name, slug, and description
3. The creator becomes the initial Owner

### Manage Members

1. Open the namespace detail page
2. Go to the members tab
3. Add members or adjust roles
4. Keep important permission changes traceable

### Govern the Lifecycle

- Freeze when publishing must stop temporarily
- Archive when the space is no longer active but history must remain

## Practical Advice

- Use a stable and recognizable slug
- Keep the number of Owners small
- Align visibility and review policy within the team early
- Periodically clean up stale members and outdated permissions

## Continue Reading

- [Skill Publishing & Versioning](/en/guide/skill-publish)
- [Review & Governance](/en/guide/review)
