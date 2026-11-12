# Team-only automatic publication

## Why

The unreleased #1069 draft combines a namespace master switch with a member review exemption.
The maintainer confirmed one namespace-local MEMBER permission instead: 团队内自动发布.
A grant authorizes system approval after a current SAFE scan, not a new role or direct publication.

## What Changes

- Replace both switches with one default-absent grant identity on a TEAM namespace member.
- Allow only the current namespace OWNER/ADMIN to manage grants through session-based member management.
- Reject every API token for grant management, independently of scopes and account roles.
- Capture the active grant identity and accepted submit capability on new in-scope review tasks.
- Support first NAMESPACE_ONLY publication and same-visibility NAMESPACE_ONLY updates only.
- Bind automatic decisions to the current scan round and serialize competing lifecycle operations.
- Preserve review tasks, structured SYSTEM_AUTOMATIC/HUMAN decisions, audit and normal fallback notifications.
- Add member-management controls, localized review labels, generated API contracts and bilingual usage documentation.
- Keep #1078 capability implementation separate; current submit capability remains skill:publish.

## Impact

Affected areas: namespace membership, ordinary Skill submission, scanning/review, events/projections,
web member/review management, protocol documentation and the unreleased V124 migration.
Migrations through V122 and GitHub/Suite/Bundle policies remain unchanged.

The source of truth is the maintainer confirmation for #1069, comment 6785596349, sections 1–8.
Acceptance must distinguish unit tests, PostgreSQL integration, controlled scan callbacks and real scanner runs.
