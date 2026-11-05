# Tasks

- [x] Add SKILL.md metadata parsing and validation.
- [x] Add multipart publish fields and package-preview defaults.
- [x] Snapshot presentation fields in parsed metadata and update effective published summary.
- [x] Preserve original description in search text and keep pending updates isolated.
- [x] Add automated inheritance, reset/default, rollback, approval, promotion and rerelease coverage.
- [x] Regenerate OpenAPI types and update CLI usage documentation.
- [x] Add detail-page overview projection.
- [x] Use Overview, Skill instructions, Files and Versions tabs, with rendered/source original instructions.
- [x] Add read-only historical-version selection, URL history, notices and return to latest published.
- [x] Add regression coverage for unavailable versions, asynchronous loading and keyboard focus.

## Publish form and editor

- [x] Implement A1 desktop/sidebar and mobile settings-before-introduction layout.
- [x] Place package selection before basic information and presentation, with avatar preceding the display name.
- [x] Keep a single overview editor entry at the field header and a read-only form preview.
- [x] Integrate visual/Markdown/preview editing with same-origin assets, recoverable syntax and cancel/apply behavior.
- [x] Use direct summary editing and actual-value changes to create independent custom fields.
- [x] Confirm per-field restore-to-package actions and preserve default-following behavior after package replacement.
- [x] Require effective web introduction values without making optional CLI/API metadata mandatory.
- [x] Implement accessible empty/selected/reading/rejected upload states and recoverable replacements.
- [x] Preserve shared upload hints for Suite Bundle and isolate skill-specific package requirements.
- [x] Prevent cross-resource cached snapshots and late package responses from populating a different form.
- [x] Implement mutually exclusive submission progress, actual-status receipts and actionable recovery without automatic replay.
- [x] Preserve readonly, avatar-only updates, staged-avatar state and existing publication permissions.
- [x] Verify frontend types/lint/tests/build, documentation build and responsive browser workflows.

## Full-PR acceptance

- [ ] Revalidate single-field CLI/API inheritance independently of the web form's complete payload.
- [ ] Revalidate reviewer visibility of the effective custom summary and overview before approving a version.
- [ ] Complete the final-head lifecycle, rollback, promotion, search and access-control acceptance matrix.

The completed items record implementation and automated or scoped browser coverage. They do not imply that the full PR is accepted or merge-ready. Native OS IME, physical drag-and-drop, system file-dialog cancellation and real Safari remain separate manual checks. Browser-generated drag events and fault-injected responses must be reported separately from real OS and backend behavior. Local preview provenance, test commands and remaining findings belong to the validation report rather than this formal checklist.
