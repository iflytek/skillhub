# Notification System Design

## Goal

Build an independent in-app notification subsystem for SkillHub that delivers near-real-time notifications for skill lifecycle events (publish, review, promotion, report), with HTTP polling, user preference control, and extensibility for future third-party channels.

## Scope

- **In scope**: In-app notifications, 10-second HTTP polling, notification preferences (category × channel), bell icon + dropdown + notification page, data cleanup
- **Out of scope**: External channels (email, Feishu, DingTalk), migration of existing governance notifications, external webhook delivery

## Architecture

```
Domain Services (existing)
    │ ApplicationEventPublisher
    ▼
Domain Events (existing + new)
    │
    ├── SearchIndexListener (existing)
    ├── StarCounterListener (existing)
    └── NotificationModule (NEW)
         ├── NotificationEventListener
         ├── RecipientResolver
         ├── NotificationPreferenceService (preference CRUD)
         └── NotificationService (preference filter + persist + HTTP reads)
```

The notification module consumes domain events via `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async("skillhubEventExecutor")`, following the same pattern as existing listeners. The async executor pool (max 4 threads) is sufficient for the added load since notification processing is a lightweight database insert. Clients discover persisted changes through HTTP polling.

## Data Model

### notification

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL PK | |
| recipient_id | VARCHAR(128) NOT NULL | Recipient user ID |
| category | VARCHAR(32) NOT NULL | PUBLISH / REVIEW / PROMOTION / REPORT |
| event_type | VARCHAR(64) NOT NULL | e.g. skill.published, review.approved |
| title | VARCHAR(200) NOT NULL | Human-readable title |
| body_json | TEXT | Structured payload (see body_json schema below) |
| entity_type | VARCHAR(64) | skill / review / report (for navigation) |
| entity_id | BIGINT | Associated entity ID |
| status | VARCHAR(20) NOT NULL DEFAULT 'UNREAD' | UNREAD / READ |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| read_at | TIMESTAMPTZ | |

Indexes:
- `(recipient_id, created_at DESC)` — notification list
- `(recipient_id, status, created_at DESC)` — unread filter

### notification_preference

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL PK | |
| user_id | VARCHAR(128) NOT NULL | |
| category | VARCHAR(32) NOT NULL | PUBLISH / REVIEW / PROMOTION / REPORT |
| channel | VARCHAR(32) NOT NULL | IN_APP (future: EMAIL, FEISHU, DINGTALK) |
| enabled | BOOLEAN NOT NULL DEFAULT TRUE | |
| UNIQUE(user_id, category, channel) | | |

Behavior: When no explicit preference exists, default to `enabled = true` for all categories on `IN_APP` channel. No pre-inserted rows needed.

### body_json Schema

The `title` column stores an i18n message key (e.g. `notification.review.approved`). The `body_json` column stores interpolation parameters as JSON. The frontend renders the title via `t(title, JSON.parse(bodyJson))`.

Common envelope:
```json
{
  "skillName": "generate-commit",
  "skillSlug": "generate-commit",
  "namespace": "team-ai",
  "version": "1.0.0",
  "actor": "admin"
}
```

Additional fields per event type:
- `review.*`: `reviewId`, `reason` (for rejected)
- `promotion.*`: `promotionId`, `reason` (for rejected)
- `report.*`: `reportId`, `action` (for resolved: "resolved" / "dismissed" / "hidden" / "archived")

## Domain Events

### Existing (reuse)

- `SkillPublishedEvent(skillId, versionId, publisherId)`

### New Events

```java
// Review flow
ReviewSubmittedEvent(reviewId, skillId, versionId, submitterId, namespaceId)
ReviewApprovedEvent(reviewId, skillId, versionId, reviewerId, submitterId)
ReviewRejectedEvent(reviewId, skillId, versionId, reviewerId, submitterId, reason)

// Promotion flow
PromotionSubmittedEvent(promotionId, skillId, versionId, submitterId)
PromotionApprovedEvent(promotionId, skillId, reviewerId, submitterId)
PromotionRejectedEvent(promotionId, skillId, reviewerId, submitterId, reason)

// Report flow
ReportSubmittedEvent(reportId, skillId, reporterId)
ReportResolvedEvent(reportId, skillId, handlerId, reporterId, action)
```

### Domain Service Modifications

The following existing services need `ApplicationEventPublisher` injected and `publishEvent()` calls added:

| Service | Method | Event to Publish |
|---------|--------|-----------------|
| `ReviewService.submitReview()` | After review record created | `ReviewSubmittedEvent` |
| `ReviewService.approveReview()` | After status set to APPROVED | `ReviewApprovedEvent` |
| `ReviewService.rejectReview()` | After status set to REJECTED | `ReviewRejectedEvent` |
| `PromotionService.submitPromotion()` | After promotion request created | `PromotionSubmittedEvent` |
| `PromotionService.approvePromotion()` | After promotion approved | `PromotionApprovedEvent` |
| `PromotionService.rejectPromotion()` | After promotion rejected | `PromotionRejectedEvent` |
| `SkillReportService.submitReport()` | After report created | `ReportSubmittedEvent` |
| `SkillReportService.resolveReport()` / `dismissReport()` | After report resolved | `ReportResolvedEvent` |

`SkillPublishService` already publishes `SkillPublishedEvent` — no change needed.
`SkillReportService` does not currently inject `ApplicationEventPublisher` — it needs to be added.

### New Repository Methods for Recipient Resolution

| Repository | New Method | Purpose |
|-----------|-----------|---------|
| `NamespaceMemberRepository` | `findByNamespaceIdAndRoleIn(Long nsId, Collection<NamespaceRole> roles)` | Find namespace ADMIN/OWNER for review.submitted |
| `UserRoleBindingRepository` | `findByRoleCode(String roleCode)` | Find platform SKILL_ADMIN users for promotion/report events |

### Event → Notification Mapping

| category | event_type | Trigger | Recipients |
|----------|-----------|---------|------------|
| PUBLISH | skill.published | Skill auto-published | Skill author |
| REVIEW | review.submitted | New version submitted for review | Namespace ADMIN/OWNER |
| REVIEW | review.approved | Review approved | Skill author (submitter) |
| REVIEW | review.rejected | Review rejected | Skill author (submitter) |
| PROMOTION | promotion.submitted | Promotion request submitted | Platform SKILL_ADMIN |
| PROMOTION | promotion.approved | Promotion approved | Requester |
| PROMOTION | promotion.rejected | Promotion rejected | Requester |
| REPORT | report.submitted | New report filed | Platform SKILL_ADMIN |
| REPORT | report.resolved | Report resolved | Reporter |

## Module Structure

New Maven module: `skillhub-notification`

Dependencies: `skillhub-notification` → `skillhub-domain` (domain events, entities, repositories). Both `NotificationEventListener` and `RecipientResolver` live in `skillhub-app` (following the existing pattern of `SkillStarEventListener` and `SkillRatingEventListener`), where they can access `skillhub-notification` services, `skillhub-domain` repositories, and `skillhub-auth` for role resolution. This avoids a cross-module dependency from `skillhub-notification` to `skillhub-auth`.

```
skillhub-notification/                    -- new module (depends on: skillhub-domain)
├── domain/
│   ├── Notification.java
│   ├── NotificationCategory.java        -- enum: PUBLISH, REVIEW, PROMOTION, REPORT
│   ├── NotificationChannel.java         -- enum: IN_APP (future: EMAIL, FEISHU...)
│   ├── NotificationPreference.java
│   ├── NotificationRepository.java
│   └── NotificationPreferenceRepository.java
└── service/
    ├── NotificationService.java         -- apply preference, create, list, mark read, batch read, unread count
    └── NotificationPreferenceService.java  -- preference CRUD + default fallback

skillhub-app/                             -- existing module
└── listener/
    ├── NotificationEventListener.java   -- consume domain events, call RecipientResolver + NotificationService
    └── RecipientResolver.java           -- resolve recipient list per event type (needs auth + domain repos)
```

## HTTP Polling

- The global bell polls `GET /api/notifications/unread-count` every 10 seconds while a user is authenticated.
- An active dropdown or notification page polls its paginated `GET /api/notifications` query every 10 seconds.
- Polling pauses while the browser tab is in the background.
- Window focus and network reconnect trigger a fresh request.
- Poll responses are authoritative; the unread badge uses the server count instead of incrementing a client-side event counter.
- Closing the dropdown unmounts its list query, so the full notification list is not polled when it is not visible.

## API Design

```
GET    /api/notifications                  -- List (paginated + category filter)
GET    /api/notifications/unread-count     -- Unread count (for bell badge)
PUT    /api/notifications/{id}/read        -- Mark single as read
PUT    /api/notifications/read-all         -- Mark all as read
GET    /api/notification-preferences       -- Get current user preferences
PUT    /api/notification-preferences       -- Batch update preferences
```

Response format follows existing SkillHub API conventions (code + data wrapper).

## Frontend

### Bell Component (global nav bar)
- Bell icon in nav bar, left of user avatar
- Red badge with unread count (> 99 shows "99+")
- Polls the unread count over HTTP every 10 seconds while the tab is visible
- Click to expand dropdown

### Dropdown List
- Shows latest 5 notifications
- Polls the visible list over HTTP every 10 seconds
- Each item: title + relative time ("3 minutes ago")
- Click item → navigate to entity page + mark as read
- Footer: "View all notifications" link
- Header: "Mark all as read" button

### Notification Page (`/dashboard/notifications`)
- Full notification list with pagination
- Polls the visible page over HTTP every 10 seconds
- Tab filter by category: All / Publish / Review / Promotion / Report
- Batch mark all as read
- Click to navigate

### Preference Settings (`/settings/notifications`)
- Grouped by category, each with toggle switch
- Currently shows IN_APP channel column only
- Future: expand to category × channel matrix when new channels are added

## Data Cleanup

- Scheduled task (`@Scheduled`) runs daily at 2:00 AM
- Read notifications: retain 30 days
- Unread notifications: retain 90 days
- Retention periods configurable
- Use `ShedLock` or database advisory lock to ensure single-instance execution in multi-pod deployments
- If using ShedLock: add `shedlock-spring` + `shedlock-provider-jdbc-template` as new Maven dependencies

## Configuration

```yaml
skillhub:
  notification:
    cleanup:
      read-retention-days: 30
      unread-retention-days: 90
```

## Relationship with Existing Governance Notifications

- Existing `GovernanceNotificationService` + `user_notification` table remain untouched
- New notification system runs independently in parallel
- Existing governance notification UI (inside governance center) unchanged
- Bell component reads only from new `notification` table
- Future unification (migrating old data to new table) is out of scope for this phase

## Extensibility

- New event types: add domain event record + mapping in `NotificationEventListener`
- Third-party integrations: add new `@TransactionalEventListener` beans that consume the same domain events
- Preference table already supports category × channel granularity, no schema change needed
