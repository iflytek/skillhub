## Context

`github_source_binding` identifies the repository by GitHub repository ID and namespace and stores the current owner and name. `github_source_skill_binding` maps paths to Skill identities. Version provenance also records the ingestion repository and commit. The current `/github` page queries individual Skills.

## Goals / Non-Goals

- Browse and paginate repositories and their available members. Support direct links and browser navigation.
- Preserve current Skill search and detail links. No new installation or review actions in this delivery.

## Decisions

- Use the verified source binding in the public `@github` catalog and paginate after filtering both repository and member rows. Use the stable repository ID in the detail URL and show the current name.
- Select active and unhidden Skills whose published versions are ready and have not been yanked. Only expose data the caller is allowed to see. Exclude sources with no visible member from the list. Use the same member query for detail results and list counts.
- Read existing associations in place. Leave records without verified association in the existing Skill view. Do not infer repository membership from a label, Skill slug or latest upstream HEAD.
- The repository page describes available members, which may come from different upstream commits. A future installation manifest must prove membership at one commit and pin exact versions.

## Risks / Trade-offs

- Visibility drift could leak repository names and member counts. Exercise anonymous, member and non-member queries and verify unpublished and withdrawn members are excluded.
- Renames or collisions may invalidate name-based links. Use ID-based navigation and deterministic pagination.
- Reusing the Suite model directly would impose an entry member and member count limit. Installation design can reuse exact-member planning after it establishes a valid manifest.
