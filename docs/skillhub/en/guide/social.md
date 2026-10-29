# Stars, ratings and notifications

## Overview

SkillHub provides a rich set of social features that allow team members to interact, share, and recommend skill packages.

![Concept Diagram](/diagrams/social-concept.png)

**Core Features**:

- **Star**: Bookmark your favorite skill packages for easy access later
- **Rating**: Rate skill packages (1-5 stars) to help others assess quality
- **Download Statistics**: Track download counts to highlight popular skill packages
- **Notification System**: Receive timely notifications for review and governance events, and more

**Social Metrics**:

| Metric | Description |
|------|------|
| **Star Count** | Number of users who have bookmarked the skill package |
| **Average Rating** | Mean score across all user ratings |
| **Download Count** | Total number of downloads |
| **Updated time** | Update time shown in the current list |

## Use Cases

**Case 1: Bookmarking Frequently Used Skill Packages**

A developer finds a useful skill package and clicks the star button to bookmark it.

![Screenshot](/screenshots/skill-detail-star.png)

**Case 2: Rating and Recommending**

After using a skill package, a developer gives it a rating and review to help other team members.

**Case 3: Viewing Notifications**

A developer receives a notification that their submission has been approved, or another supported governance event occurs.

![Screenshot](/screenshots/notifications.png)

**Case 4: Browsing Popular Skill Packages**

View skill packages with the most stars and highest ratings to discover best practices.

## Step-by-Step Guide

**Starring a Skill Package**:

1. Navigate to the skill package detail page
2. Click the "Star" button
3. The skill package will appear in your "My Stars" list
4. Click again to unstar

**Rating a Skill Package**:

1. Navigate to the skill package detail page
2. Click the star icons to select a rating (1-5 stars)
3. The rating takes effect immediately and impacts the skill package's average rating
4. You can update your rating at any time

**Writing a Review**:

1. Select "Write a review" on a published skill's detail page
2. Choose 1-5 stars and enter up to 2,000 characters
3. You can edit or clear the text; clearing it keeps the star rating and remains available if the skill is later unpublished
4. A review hidden by an administrator stays hidden after author edits and becomes public only after an administrator restores it

**Viewing Notifications**:

1. Click the notification icon in the top navigation bar
2. View the list of unread notifications
3. Click a notification to navigate to the relevant page
4. Mark as read or mark all as read

The notification list and unread count refresh through ordinary HTTP requests every 10 seconds and
immediately when the window regains focus. The legacy `GET /api/v1/notifications/sse` endpoint has
been removed. Custom clients should poll `GET /api/v1/notifications` and
`GET /api/v1/notifications/unread-count` instead.

**Viewing My Stars**:

1. Navigate to `/dashboard/stars`
2. View all starred skill packages
3. Browse pages; use the search page for the starred-only filter
4. Quickly access frequently used skill packages

## Confirm your identity first

Stars, ratings and notifications belong to the signed-in account. Anonymous star/rating actions prompt for login; recheck state after switching accounts. A star does not install a skill, and removing a star does not delete local files. Rating buttons are disabled while submitting; successful requests refresh the personal rating and skill data. Hovered stars are only a preview.

## Manage notifications and follow up

Open `/notifications` and filter by all, publish, review, promotion or report; pages contain up to 20 items. Clicking an unread item marks it read and opens its target. Reading a notification does not approve a review: complete that action on the target page.

You can mark all notifications read; deletion is available only for read items. At `/settings/notifications`, toggle categories for in-app delivery. This form configures `IN_APP`, not email or SMS subscriptions; categories default to enabled when no preference is stored.

## Verify results and troubleshoot

| Action or symptom | Observable result / next step |
| --- | --- |
| Star saved | The button changes to starred; confirm it in My Stars |
| Rating changed | Wait for completion and check your rating; hover is not submission |
| Review cleared | Text is removed but the score remains; this is separate from unstarring |
| Your review is absent publicly | Check whether it is hidden; author edits do not restore visibility |
| Unread count looks stale | Wait for polling or refocus the window, then check requests and login if it persists |
| Notification target is inaccessible | Recheck current resource state and permissions; historical notifications do not restore access |

## API Reference

**Star a Skill Package**:
```bash
PUT /api/v1/skills/{skillId}/star
```

**Unstar a Skill Package**:
```bash
DELETE /api/v1/skills/{skillId}/star
```

**Check Star Status**:
```bash
GET /api/v1/skills/{skillId}/star
```


**Rate a Skill Package**:
```bash
PUT /api/v1/skills/{skillId}/rating
Content-Type: application/json

{
  "score": 5
}
```

**Parameter Reference**:
| Parameter | Type | Description |
|------|------|------|
| skillId | number | Skill package ID (path parameter) |
| score | number | Rating (1-5, required) |

**Get My Stars**:
```bash
GET /api/v1/me/stars?page=0&size=20
```

**Get My Rating**:
```bash
GET /api/v1/skills/{skillId}/rating
```

**Review APIs**:

```bash
# Public review list
GET /api/v1/skills/{skillId}/reviews?page=0&size=20

# Read, create, or update the current user's review
GET /api/v1/skills/{skillId}/reviews/me
PUT /api/v1/skills/{skillId}/reviews/me

# Clear review text while retaining the star rating
DELETE /api/v1/skills/{skillId}/reviews/me

# SKILL_ADMIN or SUPER_ADMIN moderation
POST /api/v1/admin/skill-reviews/{reviewId}/hide
POST /api/v1/admin/skill-reviews/{reviewId}/restore
```


Responses use the standard API envelope. `GET .../star` returns a boolean in `data`; `GET .../rating` returns `score` and `rated`. Mutations return an empty `data`, not a rating timestamp. IDs are numeric.

## Notes

> **Rating Rules**: Each user can rate each skill package only once. Ratings can be updated but not deleted.

> **Review Rules**: Only published skills can be reviewed, and the public list contains visible reviews only. Refresh and retry after a concurrent-update conflict.

- **Star Count**: A skill package's star count is displayed in search results and on the detail page
- **Average Rating**: Average ratings help compare skills; the search page has no dedicated rating sort
- **Notification Settings**: Users can disable certain notification types in their settings
- **Download Statistics**: Published-version downloads record counts; review-preview downloads do not count as public downloads
