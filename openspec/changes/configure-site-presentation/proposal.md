# Configure site branding and homepage presentation

## Why

Issue #1074 describes private deployments maintaining source patches for homepage and footer customization; upgrading invalidates these patches. Operators need supported instance-wide presentation configuration using official images. Defaults and existing catalog authorization must remain unchanged.

## Scope

- REQ-01: store versioned presentation settings in the existing system_setting table; database values survive restarts/upgrades, missing fields use code defaults.
- REQ-02: public read returns only typed presentation fields; SUPER_ADMIN edits and uploads, with audit and optimistic concurrency.
- REQ-03: configurable display name and separate logo/favicon; retain the existing default landing homepage with section-by-section visibility, localized presentation text, selected element switches and fixed safe link overrides. Remove the landing/catalog selector and alternative catalog composition.
- REQ-04: safe immutable brand assets in existing object storage; PNG/JPEG upload is verified and re-encoded, public access restricted to brand assets.
- REQ-05: management uses existing UI with local preview, unsaved changes protection, retained inputs after errors, group reset and clear save states.
- REQ-06: existing search/auth/visibility remain authoritative; disabled sections do not issue requests; theme/language personal preferences remain independent.
- REQ-07: one-click operators use the same deployment entry; document configuration, refresh behavior, asset storage, backup and upgrades in both languages.
- REQ-08: verify default/custom instances, permission negatives, concurrent saves, image validation, escaping, responsive UI and persistence.

Out of scope: curated recommendations (#1035), arbitrary HTML/CSS/scripts, full design-system migration, multiple instances/tenant branding, operational secrets, importing deployment files or a separate configuration service.

## Status

Human preview retained the original default homepage as the visual baseline. The implementation is being revised to configure its eight existing regions instead of introducing alternative homepage modes. Not yet accepted or released.
