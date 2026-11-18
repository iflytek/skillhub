## Context

`github_source_binding` identifies the repository by GitHub repository ID and namespace and stores the current owner and name. `github_source_skill_binding` maps paths to Skill identities. Version provenance also records the ingestion repository and commit. The current `/github` page queries individual Skills.

## Goals / Non-Goals

- Browse and paginate repositories and their available members. Support direct links and browser navigation.
- Preserve current Skill search and detail links. Add snapshot history, exact version links, available snapshot comparisons and ZIP download.

## Decisions

- Use the verified source binding in the public `@github` catalog and paginate after filtering both repository and member rows. Use the stable repository ID in the detail URL and show the current name.
- Select active and unhidden Skills whose published versions are ready and have not been yanked. Only expose data the caller is allowed to see. Exclude sources with no visible member from the list. Use the same member query for detail results and list counts.
- Read existing associations in place. Leave records without verified association in the existing Skill view. Do not infer repository membership from a label, Skill slug or latest upstream HEAD.
- The repository page describes available members, which may come from different upstream commits. A repository snapshot binds one verified GitHub repository and commit to the exact versions of its independently usable Skills.
- Repository snapshots have their own identity, availability rules and installation lifecycle. A future repository batch-install flow uses a repository-specific manifest, API, CLI entry point and local installation record. It does not create a Suite, select an Entry Skill, call Suite installation services or expose Suite commands.

## Snapshot publication and ZIP delivery

- Persist an immutable snapshot for one source binding and commit only when a complete repository scan proves the eligible paths and their path ingestion IDs, which resolve to exact approved version IDs. For an unchanged path, link an earlier version only when the acquisition evidence confirms the same content.
- Acquisition completion and publication readiness are separate checks. Serve an installation plan only when all pinned members can be downloaded by the requester. Keep the previous eligible snapshot available while a new one awaits review. Recheck member availability when serving the plan or archive.
- Save membership evidence in its own tables in the acquisition completion transaction. Ignore partial, superseded and unverified legacy path sets. Historical browsing remains available for older published Skills without a snapshot.
- Snapshot history shows the 12 most recent captures, plus the two most recent currently available snapshots and an explicitly requested historical snapshot. The comparison shows up to 20 exact-version changes against the nearest earlier available snapshot returned in history.
- Generate each ZIP from the published bundles keyed by Skill ID and version ID. The manifest carries the repository ID, snapshot ID, Git commit, member coordinates and each package's SHA-256. Recheck membership after the archive is prepared and before sending it. Enforce a per-instance concurrency limit of two jobs and per-request limits of 250 members and 256 MiB of package data.

## Risks / Trade-offs

- Visibility drift could leak repository names and member counts. Exercise anonymous, member and non-member queries and verify unpublished and withdrawn members are excluded.
- Renames or collisions may invalidate name-based links. Use ID-based navigation and deterministic pagination.
- Repository batch installation owns exact-version validation, conflict handling and recovery on failure. Define and test these behaviors in the repository installation contract before implementation.

## Confirmed V3 page composition

The catalog places the Skill/repository switch and result count in a single toolbar below search. Repository cards include up to three visible published member previews, fetched in a batched query with the list. Detail uses a member/history workspace and a snapshot/download sidebar. Desktop content keeps a minimum height of 34rem, while narrow screens stack the sidebar above a minimum 27rem content area. URL parameters retain snapshot selection, filters and return paths.
