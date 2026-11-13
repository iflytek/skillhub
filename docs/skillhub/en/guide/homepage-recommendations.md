# Homepage picks and collections

Super administrators manage recommendations under **Account menu (top right) → System settings → Picks & collections**. Visitors and ordinary users see published content only.

## Content and navigation

Platform picks are an ordered list, not a new skill label or permission. Collections combine a title, description, optional cover and selected skills. They are editorial content, not installable Skill Suites. Existing skill labels remain independent.

The homepage shows three collections per desktop group and fewer columns on narrow screens. Previous/next controls and horizontal scrolling reveal more. Browse collection expands its full current skill list within the existing collection section, replacing the carousel without opening a dialog. Collapse restores the carousel. Viewing a skill uses its existing detail route; the expanded section has a viewport-aware height limit and one scrollable content area, with its collapse action remaining accessible. Browser Back restores the collection, filters, page position and internal scroll position. Browse all picks opens the recommendation page; skill cards use the existing skill detail route. Direct collection links remain supported.

Limits are 12 collections, 24 picks and 24 skills per collection. Enabled collections require a title and at least one skill. Candidates must be public, active, published and downloadable, even when selected by a super administrator.

## Draft workflow

1. Review the preview, then open Manage collections, Manage picks or a collection's edit action.
2. Drag the left handle to reorder. Keyboard users can pick up with Space, move with arrows, drop with Space and cancel the current drag with Escape.
3. Dialog edits use working copies. In a collection editor or skill picker, Back, Cancel and Escape discard only that layer and return to its parent while retaining earlier management ordering. Done accepts that layer. Cancel in the root management dialog discards the whole edit; Apply updates the page draft only.
4. Save draft persists changes without changing the public homepage. Preview saves unsaved edits before opening a read-only draft preview.
5. Publish configuration saves first and asks for confirmation. The server publishes its saved draft. This never publishes a skill or changes its review status.

Failures retain input. Concurrent stale writes return `409`; the administrator must explicitly load the latest version rather than overwrite another administrator's work.

The background around a custom cover uses the selected editorial style, with a corresponding dark theme; an omitted style falls back consistently by collection ID. Custom covers retain their full aspect ratio, with the title and description outside the image. Small images are not enlarged beyond their native size. A failed cover does not prevent browsing the collection. The section has a collapse action, without a backdrop or focus trap; reduced-motion mode displays content directly.

Covers reuse the existing PNG/JPEG upload pipeline: up to 2 MiB, decoded, validated and normalized by the server. Uploaded assets are publicly addressable immediately. An unpublished draft keeps recommendation configuration private, not the asset URL itself.

## Starter content and unavailable skills

Until the first configuration publication, defaults resolve installed `@global` skills owned by the builtin publisher. Missing or ineligible skills are omitted. Saving a draft does not replace these public defaults. After first publication, an explicit snapshot is authoritative; publishing empty lists clears recommendations and never resurrects defaults.

The upgrade initializes one ordinary category label for selected previously unlabeled builtin skills, preserving existing labels and translations. New builtin imports also receive their initial label. Restarting or upgrading a skill version does not restore a manually removed label.

Every public read rechecks current eligibility. Private, hidden, non-downloadable skills and archived namespaces are excluded. Collections with no eligible skills expose no title or count. Drafts retain unavailable references for repair; configurations containing such references cannot be published.

## Deployment and API

The feature uses a separate `site.recommendations` row in the existing PostgreSQL `system_setting` table, storing draft and published snapshots under one optimistic-lock version. Flyway migration `V123` runs during upgrade. No new environment variables or services are required. Other settings retain their existing save behavior.

| Endpoint | Purpose |
| --- | --- |
| `GET /api/v1/site-recommendations` | Public collection summaries and picks; no draft |
| `GET /api/v1/site-recommendations/topics/{topicId}` | Full public collection; 404 when unavailable |
| `GET /api/v1/admin/system-config/recommendations` | Super-admin draft, publication and eligible skill summaries |
| `PUT /api/v1/admin/system-config/recommendations` | Save `{version, draft}` |
| `POST /api/v1/admin/system-config/recommendations/publish` | Publish the saved server draft with `{version}` |
| `GET /api/v1/admin/system-config/recommendations/candidates` | Search candidates using `q`, zero-based `page`, and `size` |

Browser writes retain the existing session/CSRF rules. Public responses use `Cache-Control: no-store`. Publishing invalidates this browser session's recommendation queries; other open pages receive changes when they query again.

For manual acceptance, cancel an edit and verify no draft changes; apply/save and verify the visitor homepage is unchanged; preview/publish and follow collection/skill links; then save from two administrator windows and verify the stale window retains edits after a conflict.

Collections can use six built-in editorial cover styles. The selection persists in draft and published snapshots; older settings retain a stable fallback without migration. Candidate searches accept an optional existing label slug through `label`, with filtering applied to both pagination and totals.
