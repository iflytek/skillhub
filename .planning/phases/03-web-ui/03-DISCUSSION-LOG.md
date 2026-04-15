# Phase 3: Web UI - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.  
> Decisions are captured in **03-CONTEXT.md**.

**Date:** 2026-04-15  
**Phase:** 3 — Web UI  
**Areas discussed:** Entry & navigation, Public URL & routing, Contributor blind spots, Detail & reorder, Contributor management

---

## Entry & navigation

| Option | Description | Selected |
|--------|-------------|----------|
| Dashboard card only | New card on main dashboard | |
| Menu / other only | No dashboard card | |
| Dashboard card + user menu | Card on dashboard and link in user menu | ✓ |

**User's choice:** Dashboard card **and** secondary link in **user menu**.  
**Notes:** User initially emphasized user-menu link; confirmed they also want the dashboard card.

| Option | Description | Selected |
|--------|-------------|----------|
| `/dashboard/collections/...` | Dashboard-prefixed routes | |
| `/collections/...` | Top-level authenticated routes | |
| Claude decides | Match existing router conventions | ✓ |

**User's choice:** Claude decides (planner default: `/dashboard/collections/...`).

| Option | Description | Selected |
|--------|-------------|----------|
| Card grid | Card-based list | ✓ |
| Table | Dense list | |
| Claude decides | Match nearby pages | |

**User's choice:** Card grid.

---

## Public URL & routing

| Option | Description | Selected |
|--------|-------------|----------|
| `/u/{ownerKey}/c/{collectionSlug}` | As Phase 1 groundwork | ✓ |
| Other | User describes | |
| Claude decides | Align with backend | |

**User's choice:** `/u/{ownerKey}/c/{collectionSlug}` (or equivalent segments).

| Option | Description | Selected |
|--------|-------------|----------|
| Filtered list | VIS-03 per viewer | ✓ |
| Hide until login | Metadata only for anonymous | |

**User's choice:** Filtered list for anonymous on public collections.

| Option | Description | Selected |
|--------|-------------|----------|
| Copy + notice | Toast/helper for private share | ✓ |
| Copy only | No messaging | |
| No copy for private | Contributors-only flows | |

**User's choice:** Copy works with notice for private collections.

| Option | Description | Selected |
|--------|-------------|----------|
| Generic 404 | Same for missing and unauthorized | ✓ |
| Auth redirect | Login for private anon | |
| Claude decides | Match skill/space patterns | |

**User's choice:** Generic not-found for missing and unauthorized.

---

## Contributor blind spots

| Option | Description | Selected |
|--------|-------------|----------|
| Count + helper | Non-leaking disclosure | ✓ |
| Silent omit | No mention | |
| Placeholder rows | “Restricted skill” rows | |

**User's choice:** Count + helper text.

| Option | Description | Selected |
|--------|-------------|----------|
| Owner full list, no banner | Default owner UX | ✓ |
| Owner + hint | Educational banner | |

**User's choice:** Owner sees full list without extra banner.

| Option | Description | Selected |
|--------|-------------|----------|
| Reorder visible subset | Server maps to global order | ✓ |
| Global order UI | Show hidden gaps | |
| Claude decides | Simplest correct | |

**User's choice:** Reorder among visible skills only.

---

## Detail & reorder

| Option | Description | Selected |
|--------|-------------|----------|
| Drag-and-drop | dnd-style | |
| Up/down buttons | Per-row controls | ✓ |
| Claude decides | Match codebase | |

**User's choice:** Up/down buttons.

| Option | Description | Selected |
|--------|-------------|----------|
| Prominent Add skill CTA | Explained empty state | ✓ |
| Minimal empty | Single line | |

**User's choice:** Prominent Add skill CTA when collection has no skills.

| Option | Description | Selected |
|--------|-------------|----------|
| Title + namespace | Disambiguation | ✓ |
| Title only | Minimal row | |
| Claude decides | Match other pages | |

**User's choice:** Title + namespace on each skill row.

| Option | Description | Selected |
|--------|-------------|----------|
| Inline disable/hide | No contributor banner | ✓ |
| Contributor banner | Persistent explanation | |

**User's choice:** Inline disabled/hidden controls; no contributor banner.

---

## Contributor management

| Option | Description | Selected |
|--------|-------------|----------|
| Mirror namespace members | Reuse lookup UX | ✓ |
| Simple email/username | Minimal field | |
| Claude decides | Best picker | |

**User's choice:** Mirror namespace members page patterns.

| Option | Description | Selected |
|--------|-------------|----------|
| Confirm modal | Guard removal | ✓ |
| No confirm | Immediate remove | |

**User's choice:** Confirm before removing contributor.

| Option | Description | Selected |
|--------|-------------|----------|
| Detail page section | Same route | ✓ |
| Sub-route | `/contributors` child | |
| Claude decides | Fewest clicks | |

**User's choice:** Section on collection detail page.

| Option | Description | Selected |
|--------|-------------|----------|
| Explain + CTA | Verbose empty | |
| Minimal empty | Button-first | ✓ |

**User's choice:** Minimal empty contributors list.

---

## Claude's Discretion

- Authenticated route prefix under **D-03** (planner picks idiomatic `dashboard/*` structure).
- Optional small owner hint/banner unless proven unnecessary (**D-10**).
- Any reuse of drag-and-drop if an existing shared pattern appears during planning (**D-12**).

## Deferred Ideas

(None recorded.)
