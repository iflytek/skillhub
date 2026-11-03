# Favorites, Ratings, and Notifications

This page explains how a signed-in account saves Skills, submits feedback, and manages in-app notifications. Favorites, ratings, subscriptions, and notifications serve different purposes. None grants resource access or executes a Skill. Platform review actions and deployment-side email configuration are outside this page.

## Prerequisites and Object Boundaries

Confirm the current account and target Skill on the same registry. Favorites and ratings require access to the Skill. Creating or updating a written review additionally requires an active Skill with a published version. Hiding a Skill or changing permissions can make a previous entry inaccessible.

| Action | Stored information | Intended purpose | Notes |
| --- | --- | --- | --- |
| Favorite | A relationship between the account and Skill | Locate the Skill under My Favorites | Does not install the Skill or subscribe to updates. |
| Rating and written review | A score and optional text from the account | Record usage feedback | A written review is not a publication review decision. |
| Subscription | An update subscription relationship | Receive eligible version events | A favorite does not imply a subscription. |
| Notification | An event record addressed to the account | Track publication, review, promotion, or reports | Read status does not mean the associated task is complete. |

Sources: [favorite and rating authorization](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSocialAppService.java) and [written review rules](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillReviewAppService.java).

## Save a Skill and Submit Feedback

1. Favorite the Skill on its detail page. Wait for the request to finish and inspect the button state, then confirm the entry at `/dashboard/stars`. Removing a favorite removes the relationship, not installed files. The search page also provides an Only Favorites filter.
2. Select a score on the detail page, wait for submission, and inspect the saved personal rating. Hovering is only a preview. Each account has one rating per Skill; subsequent submissions update it. There is currently no score-deletion endpoint.
3. Use Write Review when text is required, then submit the score and text. Public lists show visible reviews. Authors can edit or clear their text. Clearing text preserves the score, and existing text can still be cleared after the Skill loses its published version.
4. Text hidden by a platform Skill Administrator (`SKILL_ADMIN`) or Super Administrator (`SUPER_ADMIN`) remains hidden after author edits. Restoration requires the corresponding platform permission. Average scores and download counts inform selection but do not prove execution quality.

The following requests show response fields. The Skill ID must be a real numeric ID from the instance; `{skillId}` cannot be sent unchanged.

```http
GET /api/v1/skills/{skillId}/star
GET /api/v1/skills/{skillId}/rating
```

```json
{"data": {"score": 5, "rated": true}}
```

Reading, submitting, and clearing personal text use `GET`, `PUT`, and `DELETE /api/v1/skills/{skillId}/reviews/me`; the public list uses `GET /api/v1/skills/{skillId}/reviews`. Clearing removes text only. Authorized platform roles handle hiding and restoration through the management entry.

The first request returns a boolean `data`. The JSON above is only an excerpt for the rating request, not a complete response. Mutation responses have empty `data` and do not return a rating timestamp. Sources: [favorites](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillStarController.java) and [ratings](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillRatingController.java).

## Receive and Process In-App Notifications

![Relationship between business events, account preferences, and in-app notifications](/diagrams/notification-delivery-flow-en.svg)

*Figure: Recipient rules and category preferences determine notification creation. Reading a notification does not complete the associated business action.*

1. Open the notification entry in the header or `/notifications`. Locate the event by category and time. The title describes an event, not the current state of the target object.
2. Open an unread entry and confirm the associated object and version. The client requests read status and navigates to the target. Review decisions must still be made on the review detail page.
3. Adjust category switches at `/settings/notifications`. Saved preferences affect subsequent notification creation and do not delete existing records. Events omitted while a category was disabled are not recreated when it is enabled.
4. Confirm the business outcome before marking entries or all notifications as read. Only read notifications have a delete action. Deletion does not undo publication, review, or report resolution.

| Category | Triggering events | Recipients | Notes |
| --- | --- | --- | --- |
| Publish (`PUBLISH`) | Skill publication, subscribed version updates, or yanked published versions | Eligible publishers and subscribers | Subscription delivery rechecks account, namespace, and resource access conditions and excludes the corresponding actor. |
| Review (`REVIEW`) | Skill or profile submission, and Skill approval or rejection | Relevant reviewers or submitters | Profile and Skill review do not share identical authorization. |
| Promotion (`PROMOTION`) | Promotion submission, approval, or rejection | Platform Skill reviewers or submitters | Notifications do not grant access to a global Skill. |
| Report (`REPORT`) | Report submission and resolution | Platform Skill reviewers or reporters | The historical target may no longer be visible. |

These are the categories and representative events covered here. Exact triggers and recipients follow the [event listener](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/listener/NotificationEventListener.java) and [subscription eligibility](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SubscriptionRecipientEligibility.java). Relevant event recipient lists are deduplicated, but the notification service does not provide a global exactly-once guarantee for every event.

## Limits, Refresh, and Retention

| Setting or limit | Default or range | Effective behavior | Notes |
| --- | --- | --- | --- |
| Score and text length | Score 1–5; text up to 2000 characters | Validated on submission | Out-of-range input is rejected; score-only updates require no text. |
| Notification pagination | Web page size 20; API `size` 1–100 | Applies per request | The current page count is not the total record count. |
| In-app preferences | All four categories enabled when unset; only `IN_APP` | Saved values affect later notification creation | This version provides no email, SMS, or messaging channel. |
| Web refresh | Poll every 10 seconds; refresh on window focus | Fixed frontend behavior | Does not guarantee immediate display after an event. |
| Read record retention | `skillhub.notification.cleanup.read-retention-days=30` | Recreate or roll the registry after deployment configuration changes | Measured from `createdAt`, not from the read time. |
| Unread record retention | `skillhub.notification.cleanup.unread-retention-days=90` | Recreate or roll the registry after deployment configuration changes | Measured from `createdAt`; records are not permanent. |
| Cleanup schedule | Daily at 02:00 | Registry scheduler | Code does not fix the time zone; the instance scheduler zone applies. Eligible records are removed by a later cleanup run, not at a guaranteed exact expiry time. |

Sources: [review input](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillReviewRequest.java), [notification API](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/NotificationController.java), [preferences](https://github.com/iflytek/skillhub/blob/main/server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/service/NotificationPreferenceService.java), [polling](https://github.com/iflytek/skillhub/blob/main/web/src/features/notification/use-notifications.ts), and [cleanup](https://github.com/iflytek/skillhub/blob/main/server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/service/NotificationCleanupTask.java).

## Completion Criteria and Failure Handling

A favorite appears in the personal list. A completed rating request shows the saved personal value. Notifications show the expected category and time, and read status changes after the request completes. The target object's business state must be checked separately.

| Symptom | Likely cause | Diagnosis | Action |
| --- | --- | --- | --- |
| Favorite or rating does not persist | Expired login, denied access, or failed request | Check the current account, detail access, and browser network status | Sign in and reload state before retrying; do not repeatedly click to generate duplicate requests. |
| Review visible only to its author or platform roles | Hidden review | Compare personal moderation state and reason with the public list | Revise according to the reason or contact the corresponding platform role; do not use editing to bypass hidden state. |
| Expected notification missing | Disabled category, ineligible recipient, or asynchronous processing | Inspect preferences, subscription, and the event's business outcome | Confirm the event is addressed to the account; do not republish or resubmit solely to create a notification. |
| Unread count has not changed | Request or polling refresh pending | Inspect list and unread-count requests; focus the window again | Wait for completion and recheck; do not interpret an old count as a business failure. |
| Target inaccessible | Deleted or hidden resource, or changed access | Check the target path, account, and current namespace membership | Apply current permissions; do not treat a historical notification as an access credential. |
| Historical notification missing | Manual deletion or retention cleanup | Compare creation time, read state, and instance retention settings | Inspect the business object for its result; do not use the notification list as a permanent audit archive. |

## Boundaries and Next Steps

The former `GET /api/v1/notifications/sse` endpoint was removed. Custom clients must poll `GET /api/v1/notifications` and `GET /api/v1/notifications/unread-count`. Notification processing is asynchronous; the refresh interval is not a delivery deadline. Continue with [version management](./skill-publish.html) after publication and the [review workflow](./review.html) for review tasks. Account and namespace changes follow the [account](./account-access.html) and [namespace](./namespace.html) rules.
