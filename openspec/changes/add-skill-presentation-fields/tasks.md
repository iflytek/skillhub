# Tasks

- [x] Add SKILL.md metadata parsing and validation.
- [x] Add multipart publish fields and package-preview defaults.
- [x] Snapshot presentation fields in parsed metadata and update effective published summary.
- [x] Preserve original description in search text and keep pending updates isolated.
- [x] Add web short-description and Markdown-overview inputs.
- [x] Add full inheritance/restore-default/rollback acceptance coverage (published-only frozen inheritance, reset/default-not-copied, yank rollback, approval, promotion, and rerelease).
- [x] Regenerate OpenAPI types and update CLI usage documentation.
- [x] Add detail-page overview projection.
- [x] Replace the standalone SKILL.md button with the Skill instructions tab and rendered/source reading.
- [x] Add read-only version selection in the Versions tab, historical-version notice, and return to latest published.
- [x] Add regression coverage for selected-version navigation, unavailable/error responses, instruction reading, and asynchronous keyboard focus.

## Acceptance status

The checklist records implementation and automated coverage, not full PR acceptance. Earlier real-runtime diagnostics identified unresolved presentation inheritance and reviewer-content visibility defects. The detail-navigation follow-up does not mark those scenarios as passed; browser validation and the full presentation acceptance matrix must be reported separately.
