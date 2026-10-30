# Design

- Parse `metadata.short-description` and `metadata.overview` from SKILL.md, with limits of 200 and 20,000 characters.
- Accept multipart `shortDescription`, `overview`, and `resetPresentation`; web values override package metadata and are stored in `parsedMetadataJson` as `shortDescriptionOverride`, `overviewOverride`, and `presentationInheritedFrom`.
- Keep the existing display-name/version snapshot model and publish/review/rollback lifecycle. Pending versions never update the Skill container summary.
- The effective short description feeds `skill.summary`; search also retains the original package description in `search_text`. Overview is detail-only and falls back to README then SKILL.md in the existing documentation reader.
- Web package preview supplies defaults without editing the uploaded archive. Generated API types remain authoritative.
