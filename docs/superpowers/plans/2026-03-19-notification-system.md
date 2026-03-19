# Notification System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an independent in-app notification subsystem with SSE real-time push, user preference control, and extensibility for future channels.

**Architecture:** New `skillhub-notification` Maven module for domain/service/SSE. Event listener and recipient resolver live in `skillhub-app`. Existing domain services publish new events; the notification listener consumes them, resolves recipients, filters by preferences, persists, and pushes via SSE.

**Tech Stack:** Java 21, Spring Boot 3.2.3, PostgreSQL 16, Flyway, Spring SseEmitter, React 19, TanStack Router/Query, i18next

---

## File Structure

### Backend — New Files

```
server/skillhub-notification/
├── pom.xml
└── src/main/java/com/iflytek/skillhub/notification/
    ├── domain/
    │   ├── Notification.java
    │   ├── NotificationCategory.java
    │   ├── NotificationChannel.java
    │   ├── NotificationStatus.java
    │   ├── NotificationPreference.java
    │   ├── NotificationRepository.java
    │   └── NotificationPreferenceRepository.java
    ├── service/
    │   ├── NotificationService.java
    │   ├── NotificationPreferenceService.java
    │   └── NotificationDispatcher.java
    └── sse/
        └── SseEmitterManager.java

server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/
├── NotificationJpaRepository.java
└── NotificationPreferenceJpaRepository.java

server/skillhub-app/src/main/resources/db/migration/
└── V28__notification_system.sql

server/skillhub-app/src/main/java/com/iflytek/skillhub/
├── listener/
│   ├── NotificationEventListener.java
│   └── RecipientResolver.java
└── controller/
    └── NotificationController.java

server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/
├── ReviewSubmittedEvent.java
├── ReviewApprovedEvent.java
├── ReviewRejectedEvent.java
├── PromotionSubmittedEvent.java
├── PromotionApprovedEvent.java
├── PromotionRejectedEvent.java
├── ReportSubmittedEvent.java
└── ReportResolvedEvent.java
```

### Backend — Modified Files

```
server/pom.xml                                          -- add skillhub-notification module
server/skillhub-app/pom.xml                             -- add skillhub-notification dependency
server/skillhub-infra/pom.xml                           -- add skillhub-notification dependency
server/skillhub-domain/.../review/ReviewService.java    -- publish Review events
server/skillhub-domain/.../review/PromotionService.java -- publish Promotion events
server/skillhub-domain/.../report/SkillReportService.java -- inject EventPublisher, publish Report events
server/skillhub-domain/.../namespace/NamespaceMemberRepository.java -- add findByNamespaceIdAndRoleIn
server/skillhub-infra/.../jpa/NamespaceMemberJpaRepository.java     -- implement new method
```

### Frontend — New Files

```
web/src/features/notification/
├── use-notifications.ts
├── use-notification-preferences.ts
├── use-notification-sse.ts
├── notification-bell.tsx
├── notification-dropdown.tsx
└── notification-preference-form.tsx

web/src/pages/
├── notifications.tsx
└── settings/notification-settings.tsx
```

### Frontend — Modified Files

```
web/src/api/client.ts           -- add notificationApi
web/src/api/types.ts            -- add notification types
web/src/shared/components/user-menu.tsx  -- add bell component next to avatar
web/src/i18n/locales/zh.json   -- add notification i18n keys
web/src/i18n/locales/en.json   -- add notification i18n keys
web/src/routes/                 -- add notification routes
```

---

## Tasks

### Task 1: Maven Module Setup + Database Migration

**Files:**
- Create: `server/skillhub-notification/pom.xml`
- Modify: `server/pom.xml` — add `<module>skillhub-notification</module>`
- Modify: `server/skillhub-app/pom.xml` — add skillhub-notification dependency
- Modify: `server/skillhub-infra/pom.xml` — add skillhub-notification dependency
- Create: `server/skillhub-app/src/main/resources/db/migration/V28__notification_system.sql`

- [ ] **Step 1: Create skillhub-notification pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.iflytek.skillhub</groupId>
        <artifactId>skillhub-parent</artifactId>
        <version>0.1.0-beta.7</version>
    </parent>
    <artifactId>skillhub-notification</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.iflytek.skillhub</groupId>
            <artifactId>skillhub-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Add module to parent pom.xml**

In `server/pom.xml`, add `<module>skillhub-notification</module>` to the `<modules>` section.

- [ ] **Step 3: Add dependency to skillhub-app pom.xml**

```xml
<dependency>
    <groupId>com.iflytek.skillhub</groupId>
    <artifactId>skillhub-notification</artifactId>
</dependency>
```

- [ ] **Step 4: Add dependency to skillhub-infra pom.xml**

```xml
<dependency>
    <groupId>com.iflytek.skillhub</groupId>
    <artifactId>skillhub-notification</artifactId>
</dependency>
```

- [ ] **Step 5: Create Flyway migration V28__notification_system.sql**

```sql
CREATE TABLE notification (
    id              BIGSERIAL PRIMARY KEY,
    recipient_id    VARCHAR(128) NOT NULL,
    category        VARCHAR(32)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body_json       TEXT,
    entity_type     VARCHAR(64),
    entity_id       BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'UNREAD',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    read_at         TIMESTAMPTZ
);

CREATE INDEX idx_notification_recipient_created ON notification(recipient_id, created_at DESC);
CREATE INDEX idx_notification_recipient_status ON notification(recipient_id, status, created_at DESC);

CREATE TABLE notification_preference (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(128) NOT NULL,
    category        VARCHAR(32)  NOT NULL,
    channel         VARCHAR(32)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE(user_id, category, channel)
);
```

- [ ] **Step 6: Verify build compiles**

Run: `cd server && mvn compile -pl skillhub-notification -am`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(notification): add Maven module and database migration"
```

---

### Task 2: Notification Domain Entities + Enums + Repositories

**Files:**
- Create: `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/domain/NotificationCategory.java`
- Create: `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/domain/NotificationChannel.java`
- Create: `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/domain/NotificationStatus.java`
- Create: `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/domain/Notification.java`
- Create: `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/domain/NotificationPreference.java`
- Create: `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/domain/NotificationRepository.java`
- Create: `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/domain/NotificationPreferenceRepository.java`

- [ ] **Step 1: Create enums**

`NotificationCategory.java`:
```java
package com.iflytek.skillhub.notification.domain;

public enum NotificationCategory {
    PUBLISH, REVIEW, PROMOTION, REPORT
}
```

`NotificationChannel.java`:
```java
package com.iflytek.skillhub.notification.domain;

public enum NotificationChannel {
    IN_APP
    // Future: EMAIL, FEISHU, DINGTALK
}
```

`NotificationStatus.java`:
```java
package com.iflytek.skillhub.notification.domain;

public enum NotificationStatus {
    UNREAD, READ
}
```

- [ ] **Step 2: Create Notification entity**

```java
package com.iflytek.skillhub.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false, length = 128)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCategory category;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "body_json", columnDefinition = "TEXT")
    private String bodyJson;

    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.UNREAD;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {}

    public Notification(String recipientId, NotificationCategory category, String eventType,
                        String title, String bodyJson, String entityType, Long entityId,
                        Instant createdAt) {
        this.recipientId = recipientId;
        this.category = category;
        this.eventType = eventType;
        this.title = title;
        this.bodyJson = bodyJson;
        this.entityType = entityType;
        this.entityId = entityId;
        this.createdAt = createdAt;
    }

    public void markRead(Instant readAt) {
        this.status = NotificationStatus.READ;
        this.readAt = readAt;
    }

    // Getters
    public Long getId() { return id; }
    public String getRecipientId() { return recipientId; }
    public NotificationCategory getCategory() { return category; }
    public String getEventType() { return eventType; }
    public String getTitle() { return title; }
    public String getBodyJson() { return bodyJson; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public NotificationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
```

- [ ] **Step 3: Create NotificationPreference entity**

```java
package com.iflytek.skillhub.notification.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "notification_preference",
       uniqueConstraints = @UniqueConstraint(columns = {"user_id", "category", "channel"}))
public class NotificationPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationChannel channel;

    @Column(nullable = false)
    private boolean enabled = true;

    protected NotificationPreference() {}

    public NotificationPreference(String userId, NotificationCategory category,
                                   NotificationChannel channel, boolean enabled) {
        this.userId = userId;
        this.category = category;
        this.channel = channel;
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public NotificationCategory getCategory() { return category; }
    public NotificationChannel getChannel() { return channel; }
    public boolean isEnabled() { return enabled; }
}
```

- [ ] **Step 4: Create repository interfaces**

`NotificationRepository.java`:
```java
package com.iflytek.skillhub.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository {
    Notification save(Notification notification);
    Optional<Notification> findById(Long id);
    Page<Notification> findByRecipientId(String recipientId, Pageable pageable);
    Page<Notification> findByRecipientIdAndCategory(String recipientId, NotificationCategory category, Pageable pageable);
    long countByRecipientIdAndStatus(String recipientId, NotificationStatus status);
    int markAllReadByRecipientId(String recipientId, Instant readAt);
    int deleteByStatusAndCreatedAtBefore(NotificationStatus status, Instant before);
}
```

`NotificationPreferenceRepository.java`:
```java
package com.iflytek.skillhub.notification.domain;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository {
    NotificationPreference save(NotificationPreference preference);
    List<NotificationPreference> findByUserId(String userId);
    Optional<NotificationPreference> findByUserIdAndCategoryAndChannel(
            String userId, NotificationCategory category, NotificationChannel channel);
}
```

- [ ] **Step 5: Verify build compiles**

Run: `cd server && mvn compile -pl skillhub-notification -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(notification): add domain entities, enums, and repository interfaces"
```

---

### Task 3: JPA Repository Implementations

**Files:**
- Create: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/NotificationJpaRepository.java`
- Create: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/NotificationPreferenceJpaRepository.java`

- [ ] **Step 1: Create NotificationJpaRepository**

```java
package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.notification.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long>, NotificationRepository {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndCategoryOrderByCreatedAtDesc(String recipientId, NotificationCategory category, Pageable pageable);

    long countByRecipientIdAndStatus(String recipientId, NotificationStatus status);

    @Override
    default Page<Notification> findByRecipientId(String recipientId, Pageable pageable) {
        return findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
    }

    @Override
    default Page<Notification> findByRecipientIdAndCategory(String recipientId, NotificationCategory category, Pageable pageable) {
        return findByRecipientIdAndCategoryOrderByCreatedAtDesc(recipientId, category, pageable);
    }

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :readAt WHERE n.recipientId = :recipientId AND n.status = 'UNREAD'")
    int markAllReadByRecipientId(String recipientId, Instant readAt);

    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.status = :status AND n.createdAt < :before")
    int deleteByStatusAndCreatedAtBefore(NotificationStatus status, Instant before);
}
```

- [ ] **Step 2: Create NotificationPreferenceJpaRepository**

```java
package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.notification.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceJpaRepository extends JpaRepository<NotificationPreference, Long>, NotificationPreferenceRepository {
    List<NotificationPreference> findByUserId(String userId);
    Optional<NotificationPreference> findByUserIdAndCategoryAndChannel(
            String userId, NotificationCategory category, NotificationChannel channel);
}
```

- [ ] **Step 3: Verify build compiles**

Run: `cd server && mvn compile -pl skillhub-infra -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(notification): add JPA repository implementations"
```

---

### Task 4: New Domain Events

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/ReviewSubmittedEvent.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/ReviewApprovedEvent.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/ReviewRejectedEvent.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/PromotionSubmittedEvent.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/PromotionApprovedEvent.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/PromotionRejectedEvent.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/ReportSubmittedEvent.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/ReportResolvedEvent.java`

- [ ] **Step 1: Create Review events**

`ReviewSubmittedEvent.java`:
```java
package com.iflytek.skillhub.domain.event;

public record ReviewSubmittedEvent(Long reviewId, Long skillId, Long versionId, String submitterId, Long namespaceId) {}
```

`ReviewApprovedEvent.java`:
```java
package com.iflytek.skillhub.domain.event;

public record ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId) {}
```

`ReviewRejectedEvent.java`:
```java
package com.iflytek.skillhub.domain.event;

public record ReviewRejectedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId, String reason) {}
```

- [ ] **Step 2: Create Promotion events**

`PromotionSubmittedEvent.java`:
```java
package com.iflytek.skillhub.domain.event;

public record PromotionSubmittedEvent(Long promotionId, Long skillId, Long versionId, String submitterId) {}
```

`PromotionApprovedEvent.java`:
```java
package com.iflytek.skillhub.domain.event;

public record PromotionApprovedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId) {}
```

`PromotionRejectedEvent.java`:
```java
package com.iflytek.skillhub.domain.event;

public record PromotionRejectedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId, String reason) {}
```

- [ ] **Step 3: Create Report events**

`ReportSubmittedEvent.java`:
```java
package com.iflytek.skillhub.domain.event;

public record ReportSubmittedEvent(Long reportId, Long skillId, String reporterId) {}
```

`ReportResolvedEvent.java`:
```java
package com.iflytek.skillhub.domain.event;

public record ReportResolvedEvent(Long reportId, Long skillId, String handlerId, String reporterId, String action) {}
```

- [ ] **Step 4: Verify build compiles**

Run: `cd server && mvn compile -pl skillhub-domain`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(notification): add domain events for review, promotion, and report"
```

---

### Task 5: Publish Events from Existing Services

**Files:**
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewService.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionService.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/report/SkillReportService.java`
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceMemberRepository.java`
- Modify: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/NamespaceMemberJpaRepository.java`

**Context:** `ReviewService` and `PromotionService` already inject `ApplicationEventPublisher`. `SkillReportService` does NOT — it needs to be added.

- [ ] **Step 1: Add publishEvent calls to ReviewService**

In `ReviewService.submitReview()` — after the review task is saved, add:
```java
eventPublisher.publishEvent(new ReviewSubmittedEvent(
    task.getId(), skillVersion.getSkillId(), skillVersion.getId(),
    task.getSubmittedBy(), task.getNamespaceId()));
```

In `ReviewService.approveReview()` — after status is set to APPROVED, add:
```java
eventPublisher.publishEvent(new ReviewApprovedEvent(
    task.getId(), skill.getId(), version.getId(),
    reviewerId, task.getSubmittedBy()));
```

In `ReviewService.rejectReview()` — after status is set to REJECTED, add:
```java
eventPublisher.publishEvent(new ReviewRejectedEvent(
    task.getId(), skill.getId(), version.getId(),
    reviewerId, task.getSubmittedBy(), comment));
```

Add imports:
```java
import com.iflytek.skillhub.domain.event.ReviewSubmittedEvent;
import com.iflytek.skillhub.domain.event.ReviewApprovedEvent;
import com.iflytek.skillhub.domain.event.ReviewRejectedEvent;
```

- [ ] **Step 2: Add publishEvent calls to PromotionService**

In `PromotionService.submitPromotion()` — after the promotion request is saved, add:
```java
eventPublisher.publishEvent(new PromotionSubmittedEvent(
    request.getId(), request.getSourceSkillId(), request.getSourceVersionId(),
    request.getSubmittedBy()));
```

In `PromotionService.approvePromotion()` — after status is set to APPROVED, add:
```java
eventPublisher.publishEvent(new PromotionApprovedEvent(
    request.getId(), request.getSourceSkillId(),
    reviewerId, request.getSubmittedBy()));
```

In `PromotionService.rejectPromotion()` — after status is set to REJECTED, add:
```java
eventPublisher.publishEvent(new PromotionRejectedEvent(
    request.getId(), request.getSourceSkillId(),
    reviewerId, request.getSubmittedBy(), comment));
```

Add imports:
```java
import com.iflytek.skillhub.domain.event.PromotionSubmittedEvent;
import com.iflytek.skillhub.domain.event.PromotionApprovedEvent;
import com.iflytek.skillhub.domain.event.PromotionRejectedEvent;
```

- [ ] **Step 3: Inject ApplicationEventPublisher into SkillReportService and add publishEvent calls**

Add field and constructor parameter:
```java
private final ApplicationEventPublisher eventPublisher;
```

In `SkillReportService.submitReport()` — after report is saved, add:
```java
eventPublisher.publishEvent(new ReportSubmittedEvent(
    report.getId(), report.getSkillId(), report.getReporterId()));
```

In `SkillReportService.resolveReport()` — after report is resolved, add:
```java
eventPublisher.publishEvent(new ReportResolvedEvent(
    report.getId(), report.getSkillId(), handlerId, report.getReporterId(), "resolved"));
```

In `SkillReportService.dismissReport()` — after report is dismissed, add:
```java
eventPublisher.publishEvent(new ReportResolvedEvent(
    report.getId(), report.getSkillId(), handlerId, report.getReporterId(), "dismissed"));
```

Add imports:
```java
import org.springframework.context.ApplicationEventPublisher;
import com.iflytek.skillhub.domain.event.ReportSubmittedEvent;
import com.iflytek.skillhub.domain.event.ReportResolvedEvent;
```

- [ ] **Step 4: Add findByNamespaceIdAndRoleIn to NamespaceMemberRepository**

Add to `NamespaceMemberRepository.java`:
```java
List<NamespaceMember> findByNamespaceIdAndRoleIn(Long namespaceId, Collection<NamespaceRole> roles);
```

Add import: `import java.util.Collection;`

Implement in `NamespaceMemberJpaRepository.java` — Spring Data JPA derives the query automatically, just ensure the interface extends with the method.

- [ ] **Step 5: Run existing tests to verify no regressions**

Run: `cd server && mvn test -pl skillhub-domain`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(notification): publish domain events from review, promotion, and report services"
```

---

### Task 6: NotificationService

**Files:**
- Create: `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/service/NotificationService.java`
- Create: `server/skillhub-notification/src/test/java/com/iflytek/skillhub/notification/service/NotificationServiceTest.java`

- [ ] **Step 1: Write NotificationServiceTest**

```java
package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.notification.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    private Clock clock;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-03-19T10:00:00Z"), ZoneOffset.UTC);
        service = new NotificationService(notificationRepository, clock);
    }

    @Test
    void createNotification_shouldSaveAndReturn() {
        Notification notification = new Notification("user-1", NotificationCategory.REVIEW,
                "review.approved", "notification.review.approved",
                "{\"skillName\":\"test\"}", "skill", 1L, Instant.now(clock));
        when(notificationRepository.save(any())).thenReturn(notification);

        Notification result = service.create("user-1", NotificationCategory.REVIEW,
                "review.approved", "notification.review.approved",
                "{\"skillName\":\"test\"}", "skill", 1L);

        assertNotNull(result);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void getUnreadCount_shouldReturnCount() {
        when(notificationRepository.countByRecipientIdAndStatus("user-1", NotificationStatus.UNREAD))
                .thenReturn(5L);

        long count = service.getUnreadCount("user-1");

        assertEquals(5L, count);
    }

    @Test
    void markRead_shouldUpdateNotification() {
        Notification notification = new Notification("user-1", NotificationCategory.REVIEW,
                "review.approved", "title", null, "skill", 1L, Instant.now(clock));
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenReturn(notification);

        service.markRead(1L, "user-1");

        assertEquals(NotificationStatus.READ, notification.getStatus());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_shouldRejectWrongUser() {
        Notification notification = new Notification("user-1", NotificationCategory.REVIEW,
                "review.approved", "title", null, "skill", 1L, Instant.now(clock));
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        assertThrows(Exception.class, () -> service.markRead(1L, "user-2"));
    }

    @Test
    void markAllRead_shouldCallRepository() {
        when(notificationRepository.markAllReadByRecipientId(eq("user-1"), any())).thenReturn(3);

        int count = service.markAllRead("user-1");

        assertEquals(3, count);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && mvn test -pl skillhub-notification -Dtest=NotificationServiceTest`
Expected: FAIL (NotificationService class not found)

- [ ] **Step 3: Implement NotificationService**

```java
package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.notification.domain.*;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional
    public Notification create(String recipientId, NotificationCategory category,
                                String eventType, String title, String bodyJson,
                                String entityType, Long entityId) {
        Notification notification = new Notification(
                recipientId, category, eventType, title, bodyJson,
                entityType, entityId, Instant.now(clock));
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(String recipientId, NotificationCategory category, Pageable pageable) {
        if (category != null) {
            return notificationRepository.findByRecipientIdAndCategory(recipientId, category, pageable);
        }
        return notificationRepository.findByRecipientId(recipientId, pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String recipientId) {
        return notificationRepository.countByRecipientIdAndStatus(recipientId, NotificationStatus.UNREAD);
    }

    @Transactional
    public void markRead(Long notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new DomainNotFoundException("error.notification.notFound", notificationId));
        if (!notification.getRecipientId().equals(userId)) {
            throw new DomainForbiddenException("error.notification.noPermission");
        }
        notification.markRead(Instant.now(clock));
        notificationRepository.save(notification);
    }

    @Transactional
    public int markAllRead(String userId) {
        return notificationRepository.markAllReadByRecipientId(userId, Instant.now(clock));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd server && mvn test -pl skillhub-notification -Dtest=NotificationServiceTest`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(notification): add NotificationService with tests"
```

---

### Task 7: NotificationPreferenceService

**Files:**
- Create: `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/service/NotificationPreferenceService.java`
- Create: `server/skillhub-notification/src/test/java/com/iflytek/skillhub/notification/service/NotificationPreferenceServiceTest.java`

- [ ] **Step 1: Write NotificationPreferenceServiceTest**

```java
package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.notification.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock private NotificationPreferenceRepository preferenceRepository;
    private NotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(preferenceRepository);
    }

    @Test
    void isEnabled_shouldReturnTrueByDefault() {
        when(preferenceRepository.findByUserIdAndCategoryAndChannel(
                "user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP))
                .thenReturn(Optional.empty());

        assertTrue(service.isEnabled("user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP));
    }

    @Test
    void isEnabled_shouldReturnFalseWhenDisabled() {
        NotificationPreference pref = new NotificationPreference(
                "user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP, false);
        when(preferenceRepository.findByUserIdAndCategoryAndChannel(
                "user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(pref));

        assertFalse(service.isEnabled("user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP));
    }

    @Test
    void getPreferences_shouldReturnAllCategoriesWithDefaults() {
        when(preferenceRepository.findByUserId("user-1")).thenReturn(List.of());

        List<NotificationPreferenceService.PreferenceView> prefs = service.getPreferences("user-1");

        assertEquals(NotificationCategory.values().length, prefs.size());
        assertTrue(prefs.stream().allMatch(NotificationPreferenceService.PreferenceView::enabled));
    }

    @Test
    void updatePreference_shouldCreateNewWhenNotExists() {
        when(preferenceRepository.findByUserIdAndCategoryAndChannel(
                "user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP))
                .thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updatePreference("user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP, false);

        verify(preferenceRepository).save(any(NotificationPreference.class));
    }

    @Test
    void updatePreference_shouldUpdateExisting() {
        NotificationPreference pref = new NotificationPreference(
                "user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP, true);
        when(preferenceRepository.findByUserIdAndCategoryAndChannel(
                "user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(pref));
        when(preferenceRepository.save(any())).thenReturn(pref);

        service.updatePreference("user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP, false);

        assertFalse(pref.isEnabled());
        verify(preferenceRepository).save(pref);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && mvn test -pl skillhub-notification -Dtest=NotificationPreferenceServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement NotificationPreferenceService**

```java
package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.notification.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    public record PreferenceView(NotificationCategory category, NotificationChannel channel, boolean enabled) {}

    public boolean isEnabled(String userId, NotificationCategory category, NotificationChannel channel) {
        return preferenceRepository.findByUserIdAndCategoryAndChannel(userId, category, channel)
                .map(NotificationPreference::isEnabled)
                .orElse(true); // default: enabled
    }

    @Transactional(readOnly = true)
    public List<PreferenceView> getPreferences(String userId) {
        Map<NotificationCategory, Boolean> saved = preferenceRepository.findByUserId(userId).stream()
                .filter(p -> p.getChannel() == NotificationChannel.IN_APP)
                .collect(Collectors.toMap(NotificationPreference::getCategory, NotificationPreference::isEnabled));

        return Arrays.stream(NotificationCategory.values())
                .map(cat -> new PreferenceView(cat, NotificationChannel.IN_APP, saved.getOrDefault(cat, true)))
                .toList();
    }

    @Transactional
    public void updatePreference(String userId, NotificationCategory category,
                                  NotificationChannel channel, boolean enabled) {
        NotificationPreference pref = preferenceRepository
                .findByUserIdAndCategoryAndChannel(userId, category, channel)
                .orElse(null);
        if (pref == null) {
            pref = new NotificationPreference(userId, category, channel, enabled);
        } else {
            pref.setEnabled(enabled);
        }
        preferenceRepository.save(pref);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd server && mvn test -pl skillhub-notification -Dtest=NotificationPreferenceServiceTest`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(notification): add NotificationPreferenceService with tests"
```

---

### Task 8: SseEmitterManager

**Files:**
- Create: `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/sse/SseEmitterManager.java`

- [ ] **Step 1: Implement SseEmitterManager**

```java
package com.iflytek.skillhub.notification.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SseEmitterManager {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterManager.class);
    private static final long SSE_TIMEOUT = 60_000L;
    private static final int MAX_EMITTERS_PER_USER = 5;
    private static final int MAX_TOTAL_EMITTERS = 1000;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final AtomicInteger totalCount = new AtomicInteger(0);

    public SseEmitter register(String userId) {
        if (totalCount.get() >= MAX_TOTAL_EMITTERS) {
            throw new IllegalStateException("SSE connection limit reached");
        }

        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        if (userEmitters.size() >= MAX_EMITTERS_PER_USER) {
            // Remove oldest emitter
            SseEmitter oldest = userEmitters.remove(0);
            oldest.complete();
            totalCount.decrementAndGet();
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        userEmitters.add(emitter);
        totalCount.incrementAndGet();

        Runnable cleanup = () -> {
            userEmitters.remove(emitter);
            totalCount.decrementAndGet();
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Send connected event
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            cleanup.run();
        }

        return emitter;
    }

    public void push(String userId, Object data) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) return;

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(data));
            } catch (IOException e) {
                // Silent ignore — notification is persisted, user will see on refresh
                log.debug("Failed to push SSE to user {}", userId);
            }
        }
    }

    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        emitters.forEach((userId, userEmitters) -> {
            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException e) {
                    // Will trigger onError cleanup
                }
            }
        });
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `cd server && mvn compile -pl skillhub-notification -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat(notification): add SseEmitterManager for real-time push"
```

---

### Task 9: NotificationDispatcher

**Files:**
- Create: `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/service/NotificationDispatcher.java`

- [ ] **Step 1: Implement NotificationDispatcher**

```java
package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.notification.domain.*;
import com.iflytek.skillhub.notification.sse.SseEmitterManager;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationDispatcher {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final SseEmitterManager sseEmitterManager;

    public NotificationDispatcher(NotificationService notificationService,
                                   NotificationPreferenceService preferenceService,
                                   SseEmitterManager sseEmitterManager) {
        this.notificationService = notificationService;
        this.preferenceService = preferenceService;
        this.sseEmitterManager = sseEmitterManager;
    }

    public void dispatch(String recipientId, NotificationCategory category,
                          String eventType, String title, String bodyJson,
                          String entityType, Long entityId) {
        // Check IN_APP preference
        if (!preferenceService.isEnabled(recipientId, category, NotificationChannel.IN_APP)) {
            return;
        }

        // Persist notification
        Notification notification = notificationService.create(
                recipientId, category, eventType, title, bodyJson, entityType, entityId);

        // Push via SSE
        sseEmitterManager.push(recipientId, Map.of(
                "id", notification.getId(),
                "category", notification.getCategory().name(),
                "eventType", notification.getEventType(),
                "title", notification.getTitle(),
                "bodyJson", notification.getBodyJson() != null ? notification.getBodyJson() : "",
                "entityType", notification.getEntityType() != null ? notification.getEntityType() : "",
                "entityId", notification.getEntityId() != null ? notification.getEntityId() : 0,
                "createdAt", notification.getCreatedAt().toString()
        ));
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `cd server && mvn compile -pl skillhub-notification -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat(notification): add NotificationDispatcher with preference filtering and SSE push"
```

---

### Task 10: RecipientResolver + NotificationEventListener

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/listener/RecipientResolver.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/listener/NotificationEventListener.java`

**Context:** These live in `skillhub-app` because they need access to both `skillhub-notification` services and `skillhub-auth` for role resolution. `UserRoleBindingRepository` is in `skillhub-auth`. A new method `findByRoleCode(String)` needs to be added to it.

- [ ] **Step 1: Add findByRoleCode to UserRoleBindingRepository**

In `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/repository/UserRoleBindingRepository.java`, add:
```java
List<UserRoleBinding> findByRoleCode(String roleCode);
```

Spring Data JPA derives the query automatically.

- [ ] **Step 2: Implement RecipientResolver**

```java
package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class RecipientResolver {

    private final NamespaceMemberRepository namespaceMemberRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;

    public RecipientResolver(NamespaceMemberRepository namespaceMemberRepository,
                              UserRoleBindingRepository userRoleBindingRepository) {
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
    }

    /**
     * Find namespace ADMIN and OWNER users for a given namespace.
     */
    public List<String> resolveNamespaceAdmins(Long namespaceId) {
        return namespaceMemberRepository
                .findByNamespaceIdAndRoleIn(namespaceId, Set.of(NamespaceRole.OWNER, NamespaceRole.ADMIN))
                .stream()
                .map(NamespaceMember::getUserId)
                .toList();
    }

    /**
     * Find all platform users with SKILL_ADMIN role.
     */
    public List<String> resolvePlatformSkillAdmins() {
        return userRoleBindingRepository.findByRoleCode("SKILL_ADMIN")
                .stream()
                .map(binding -> binding.getUserId())
                .toList();
    }
}
```

- [ ] **Step 3: Implement NotificationEventListener**

```java
package com.iflytek.skillhub.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.event.*;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationDispatcher dispatcher;
    private final RecipientResolver recipientResolver;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(NotificationDispatcher dispatcher,
                                      RecipientResolver recipientResolver,
                                      SkillRepository skillRepository,
                                      SkillVersionRepository skillVersionRepository,
                                      ObjectMapper objectMapper) {
        this.dispatcher = dispatcher;
        this.recipientResolver = recipientResolver;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.objectMapper = objectMapper;
    }

    @Async
    @TransactionalEventListener
    public void onSkillPublished(SkillPublishedEvent event) {
        Skill skill = skillRepository.findById(event.skillId()).orElse(null);
        if (skill == null) return;
        Map<String, Object> body = buildSkillBody(skill, event.versionId());
        dispatch(event.publisherId(), NotificationCategory.PUBLISH, "skill.published",
                "notification.skill.published", body, "skill", event.skillId());
    }

    @Async
    @TransactionalEventListener
    public void onReviewSubmitted(ReviewSubmittedEvent event) {
        Skill skill = skillRepository.findById(event.skillId()).orElse(null);
        if (skill == null) return;
        Map<String, Object> body = buildSkillBody(skill, event.versionId());
        body.put("reviewId", event.reviewId());
        List<String> recipients = recipientResolver.resolveNamespaceAdmins(event.namespaceId());
        for (String recipientId : recipients) {
            dispatch(recipientId, NotificationCategory.REVIEW, "review.submitted",
                    "notification.review.submitted", body, "skill", event.skillId());
        }
    }

    @Async
    @TransactionalEventListener
    public void onReviewApproved(ReviewApprovedEvent event) {
        Skill skill = skillRepository.findById(event.skillId()).orElse(null);
        if (skill == null) return;
        Map<String, Object> body = buildSkillBody(skill, event.versionId());
        body.put("reviewId", event.reviewId());
        body.put("reviewer", event.reviewerId());
        dispatch(event.submitterId(), NotificationCategory.REVIEW, "review.approved",
                "notification.review.approved", body, "skill", event.skillId());
    }

    @Async
    @TransactionalEventListener
    public void onReviewRejected(ReviewRejectedEvent event) {
        Skill skill = skillRepository.findById(event.skillId()).orElse(null);
        if (skill == null) return;
        Map<String, Object> body = buildSkillBody(skill, event.versionId());
        body.put("reviewId", event.reviewId());
        body.put("reviewer", event.reviewerId());
        body.put("reason", event.reason());
        dispatch(event.submitterId(), NotificationCategory.REVIEW, "review.rejected",
                "notification.review.rejected", body, "skill", event.skillId());
    }

    @Async
    @TransactionalEventListener
    public void onPromotionSubmitted(PromotionSubmittedEvent event) {
        Skill skill = skillRepository.findById(event.skillId()).orElse(null);
        if (skill == null) return;
        Map<String, Object> body = buildSkillBody(skill, event.versionId());
        body.put("promotionId", event.promotionId());
        List<String> recipients = recipientResolver.resolvePlatformSkillAdmins();
        for (String recipientId : recipients) {
            dispatch(recipientId, NotificationCategory.PROMOTION, "promotion.submitted",
                    "notification.promotion.submitted", body, "skill", event.skillId());
        }
    }

    @Async
    @TransactionalEventListener
    public void onPromotionApproved(PromotionApprovedEvent event) {
        Skill skill = skillRepository.findById(event.skillId()).orElse(null);
        if (skill == null) return;
        Map<String, Object> body = Map.of("skillName", skill.getDisplayName(), "skillSlug", skill.getSlug(),
                "promotionId", event.promotionId(), "reviewer", event.reviewerId());
        dispatch(event.submitterId(), NotificationCategory.PROMOTION, "promotion.approved",
                "notification.promotion.approved", toJson(body), "skill", event.skillId());
    }

    @Async
    @TransactionalEventListener
    public void onPromotionRejected(PromotionRejectedEvent event) {
        Skill skill = skillRepository.findById(event.skillId()).orElse(null);
        if (skill == null) return;
        Map<String, Object> body = Map.of("skillName", skill.getDisplayName(), "skillSlug", skill.getSlug(),
                "promotionId", event.promotionId(), "reviewer", event.reviewerId(), "reason", event.reason());
        dispatch(event.submitterId(), NotificationCategory.PROMOTION, "promotion.rejected",
                "notification.promotion.rejected", toJson(body), "skill", event.skillId());
    }

    @Async
    @TransactionalEventListener
    public void onReportSubmitted(ReportSubmittedEvent event) {
        Skill skill = skillRepository.findById(event.skillId()).orElse(null);
        if (skill == null) return;
        Map<String, Object> body = Map.of("skillName", skill.getDisplayName(), "skillSlug", skill.getSlug(),
                "reportId", event.reportId());
        List<String> recipients = recipientResolver.resolvePlatformSkillAdmins();
        for (String recipientId : recipients) {
            dispatch(recipientId, NotificationCategory.REPORT, "report.submitted",
                    "notification.report.submitted", toJson(body), "skill", event.skillId());
        }
    }

    @Async
    @TransactionalEventListener
    public void onReportResolved(ReportResolvedEvent event) {
        Skill skill = skillRepository.findById(event.skillId()).orElse(null);
        if (skill == null) return;
        Map<String, Object> body = Map.of("skillName", skill.getDisplayName(), "skillSlug", skill.getSlug(),
                "reportId", event.reportId(), "action", event.action());
        dispatch(event.reporterId(), NotificationCategory.REPORT, "report.resolved",
                "notification.report.resolved", toJson(body), "skill", event.skillId());
    }

    // --- helpers ---

    private Map<String, Object> buildSkillBody(Skill skill, Long versionId) {
        Map<String, Object> body = new HashMap<>();
        body.put("skillName", skill.getDisplayName());
        body.put("skillSlug", skill.getSlug());
        if (versionId != null) {
            skillVersionRepository.findById(versionId)
                    .ifPresent(v -> body.put("version", v.getVersion()));
        }
        return body;
    }

    private void dispatch(String recipientId, NotificationCategory category,
                           String eventType, String title, Map<String, Object> body,
                           String entityType, Long entityId) {
        dispatch(recipientId, category, eventType, title, toJson(body), entityType, entityId);
    }

    private void dispatch(String recipientId, NotificationCategory category,
                           String eventType, String title, String bodyJson,
                           String entityType, Long entityId) {
        try {
            dispatcher.dispatch(recipientId, category, eventType, title, bodyJson, entityType, entityId);
        } catch (Exception e) {
            log.error("Failed to dispatch notification {} to {}", eventType, recipientId, e);
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification body", e);
            return "{}";
        }
    }
}
```

- [ ] **Step 4: Verify build compiles**

Run: `cd server && mvn compile -pl skillhub-app -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(notification): add RecipientResolver and NotificationEventListener"
```

---

### Task 11: NotificationController (API + SSE Endpoint)

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/NotificationController.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/NotificationResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/NotificationPreferenceResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/NotificationPreferenceUpdateRequest.java`

- [ ] **Step 1: Create DTOs**

`NotificationResponse.java`:
```java
package com.iflytek.skillhub.dto;

public record NotificationResponse(
    Long id,
    String category,
    String eventType,
    String title,
    String bodyJson,
    String entityType,
    Long entityId,
    String status,
    String createdAt,
    String readAt
) {}
```

`NotificationPreferenceResponse.java`:
```java
package com.iflytek.skillhub.dto;

public record NotificationPreferenceResponse(
    String category,
    String channel,
    boolean enabled
) {}
```

`NotificationPreferenceUpdateRequest.java`:
```java
package com.iflytek.skillhub.dto;

import java.util.List;

public record NotificationPreferenceUpdateRequest(
    List<PreferenceItem> preferences
) {
    public record PreferenceItem(String category, String channel, boolean enabled) {}
}
```

- [ ] **Step 2: Implement NotificationController**

```java
package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.domain.NotificationChannel;
import com.iflytek.skillhub.notification.service.NotificationPreferenceService;
import com.iflytek.skillhub.notification.service.NotificationService;
import com.iflytek.skillhub.notification.sse.SseEmitterManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/notifications", "/api/web/notifications"})
public class NotificationController extends BaseApiController {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final SseEmitterManager sseEmitterManager;

    public NotificationController(NotificationService notificationService,
                                   NotificationPreferenceService preferenceService,
                                   SseEmitterManager sseEmitterManager,
                                   ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.notificationService = notificationService;
        this.preferenceService = preferenceService;
        this.sseEmitterManager = sseEmitterManager;
    }

    @GetMapping
    public ApiResponse<Page<NotificationResponse>> list(
            @RequestAttribute("userId") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category) {
        NotificationCategory cat = category != null ? NotificationCategory.valueOf(category) : null;
        Page<NotificationResponse> result = notificationService
                .list(userId, cat, PageRequest.of(page, size))
                .map(n -> new NotificationResponse(
                        n.getId(), n.getCategory().name(), n.getEventType(),
                        n.getTitle(), n.getBodyJson(), n.getEntityType(), n.getEntityId(),
                        n.getStatus().name(),
                        n.getCreatedAt() != null ? n.getCreatedAt().toString() : null,
                        n.getReadAt() != null ? n.getReadAt().toString() : null));
        return ok("response.success", result);
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@RequestAttribute("userId") String userId) {
        long count = notificationService.getUnreadCount(userId);
        return ok("response.success", Map.of("count", count));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id,
                                       @RequestAttribute("userId") String userId) {
        notificationService.markRead(id, userId);
        return ok("response.success", null);
    }

    @PutMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(@RequestAttribute("userId") String userId) {
        int count = notificationService.markAllRead(userId);
        return ok("response.success", Map.of("count", count));
    }

    @GetMapping("/sse")
    public SseEmitter sse(@RequestAttribute("userId") String userId) {
        return sseEmitterManager.register(userId);
    }
}
```

- [ ] **Step 3: Create NotificationPreferenceController**

```java
package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.domain.NotificationChannel;
import com.iflytek.skillhub.notification.service.NotificationPreferenceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/notification-preferences", "/api/web/notification-preferences"})
public class NotificationPreferenceController extends BaseApiController {

    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService,
                                             ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ApiResponse<List<NotificationPreferenceResponse>> getPreferences(
            @RequestAttribute("userId") String userId) {
        List<NotificationPreferenceResponse> result = preferenceService.getPreferences(userId)
                .stream()
                .map(p -> new NotificationPreferenceResponse(
                        p.category().name(), p.channel().name(), p.enabled()))
                .toList();
        return ok("response.success", result);
    }

    @PutMapping
    public ApiResponse<Void> updatePreferences(
            @RequestAttribute("userId") String userId,
            @RequestBody NotificationPreferenceUpdateRequest request) {
        for (var item : request.preferences()) {
            preferenceService.updatePreference(
                    userId,
                    NotificationCategory.valueOf(item.category()),
                    NotificationChannel.valueOf(item.channel()),
                    item.enabled());
        }
        return ok("response.success", null);
    }
}
```

- [ ] **Step 4: Verify build compiles**

Run: `cd server && mvn compile -pl skillhub-app -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(notification): add NotificationController and preference API"
```

---

### Task 12: Notification Cleanup Scheduled Task

**Files:**
- Create: `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/service/NotificationCleanupTask.java`

- [ ] **Step 1: Implement cleanup task**

```java
package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.notification.domain.NotificationRepository;
import com.iflytek.skillhub.notification.domain.NotificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class NotificationCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupTask.class);

    private final NotificationRepository notificationRepository;
    private final Clock clock;
    private final int readRetentionDays;
    private final int unreadRetentionDays;

    public NotificationCleanupTask(NotificationRepository notificationRepository,
                                    Clock clock,
                                    @Value("${skillhub.notification.cleanup.read-retention-days:30}") int readRetentionDays,
                                    @Value("${skillhub.notification.cleanup.unread-retention-days:90}") int unreadRetentionDays) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
        this.readRetentionDays = readRetentionDays;
        this.unreadRetentionDays = unreadRetentionDays;
    }

    @Scheduled(cron = "0 0 2 * * *") // Daily at 2:00 AM
    public void cleanup() {
        Instant now = Instant.now(clock);
        int readDeleted = notificationRepository.deleteByStatusAndCreatedAtBefore(
                NotificationStatus.READ, now.minus(Duration.ofDays(readRetentionDays)));
        int unreadDeleted = notificationRepository.deleteByStatusAndCreatedAtBefore(
                NotificationStatus.UNREAD, now.minus(Duration.ofDays(unreadRetentionDays)));
        log.info("Notification cleanup: deleted {} read, {} unread", readDeleted, unreadDeleted);
    }
}
```

- [ ] **Step 2: Add configuration to application-local.yml**

```yaml
skillhub:
  notification:
    cleanup:
      read-retention-days: 30
      unread-retention-days: 90
```

- [ ] **Step 3: Verify build compiles**

Run: `cd server && mvn compile -pl skillhub-notification -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(notification): add scheduled cleanup task for old notifications"
```

---

### Task 13: Run All Backend Tests

- [ ] **Step 1: Run full backend test suite**

Run: `cd server && mvn test`
Expected: All tests PASS

- [ ] **Step 2: Fix any failures and commit**

```bash
git add -A && git commit -m "fix(notification): resolve test failures"
```

---

### Task 14: Frontend — API Client + Types + i18n

**Files:**
- Modify: `web/src/api/types.ts` — add notification types
- Modify: `web/src/api/client.ts` — add notificationApi
- Modify: `web/src/i18n/locales/zh.json` — add notification i18n keys
- Modify: `web/src/i18n/locales/en.json` — add notification i18n keys

- [ ] **Step 1: Add types to web/src/api/types.ts**

```typescript
export interface NotificationItem {
  id: number
  category: 'PUBLISH' | 'REVIEW' | 'PROMOTION' | 'REPORT'
  eventType: string
  title: string
  bodyJson?: string
  entityType?: string
  entityId?: number
  status: 'UNREAD' | 'READ'
  createdAt: string
  readAt?: string
}

export interface NotificationPreferenceItem {
  category: string
  channel: string
  enabled: boolean
}

export interface NotificationUnreadCount {
  count: number
}
```

- [ ] **Step 2: Add notificationApi to web/src/api/client.ts**

```typescript
export const notificationApi = {
  list: async (params: { page?: number; size?: number; category?: string }) => {
    const searchParams = new URLSearchParams()
    if (params.page !== undefined) searchParams.set('page', String(params.page))
    if (params.size !== undefined) searchParams.set('size', String(params.size))
    if (params.category) searchParams.set('category', params.category)
    return fetchApi<{ content: NotificationItem[]; totalElements: number; page: number; size: number }>(
      `${WEB_API_PREFIX}/notifications?${searchParams}`)
  },
  getUnreadCount: () =>
    fetchApi<NotificationUnreadCount>(`${WEB_API_PREFIX}/notifications/unread-count`),
  markRead: (id: number) =>
    fetchApi<void>(`${WEB_API_PREFIX}/notifications/${id}/read`, { method: 'PUT' }),
  markAllRead: () =>
    fetchApi<{ count: number }>(`${WEB_API_PREFIX}/notifications/read-all`, { method: 'PUT' }),
  getPreferences: () =>
    fetchApi<NotificationPreferenceItem[]>(`${WEB_API_PREFIX}/notification-preferences`),
  updatePreferences: (preferences: NotificationPreferenceItem[]) =>
    fetchApi<void>(`${WEB_API_PREFIX}/notification-preferences`, {
      method: 'PUT',
      body: JSON.stringify({ preferences }),
    }),
}
```

- [ ] **Step 3: Add i18n keys to zh.json**

Add to the root of `zh.json`:
```json
"notification": {
  "title": "通知",
  "bell": "通知",
  "markAllRead": "全部标记已读",
  "viewAll": "查看全部通知",
  "empty": "暂无通知",
  "unread": "未读",
  "tabs": {
    "all": "全部",
    "publish": "发布",
    "review": "审核",
    "promotion": "提升",
    "report": "举报"
  },
  "skill.published": "技能 {{skillName}} 已发布",
  "review.submitted": "技能 {{skillName}} {{version}} 提交了审核",
  "review.approved": "技能 {{skillName}} {{version}} 审核已通过",
  "review.rejected": "技能 {{skillName}} {{version}} 审核被拒绝",
  "promotion.submitted": "技能 {{skillName}} 提交了提升申请",
  "promotion.approved": "技能 {{skillName}} 提升申请已通过",
  "promotion.rejected": "技能 {{skillName}} 提升申请被拒绝",
  "report.submitted": "技能 {{skillName}} 收到新举报",
  "report.resolved": "技能 {{skillName}} 的举报已处理",
  "preferences": {
    "title": "通知设置",
    "description": "选择你希望接收的通知类型",
    "publish": "发布通知",
    "publishDesc": "技能发布成功时通知",
    "review": "审核通知",
    "reviewDesc": "审核提交、通过或拒绝时通知",
    "promotion": "提升通知",
    "promotionDesc": "提升申请提交、通过或拒绝时通知",
    "report": "举报通知",
    "reportDesc": "举报提交或处理完成时通知",
    "saved": "通知设置已保存"
  }
}
```

- [ ] **Step 4: Add i18n keys to en.json**

Add matching English keys:
```json
"notification": {
  "title": "Notifications",
  "bell": "Notifications",
  "markAllRead": "Mark all as read",
  "viewAll": "View all notifications",
  "empty": "No notifications",
  "unread": "Unread",
  "tabs": {
    "all": "All",
    "publish": "Publish",
    "review": "Review",
    "promotion": "Promotion",
    "report": "Report"
  },
  "skill.published": "Skill {{skillName}} has been published",
  "review.submitted": "Skill {{skillName}} {{version}} submitted for review",
  "review.approved": "Skill {{skillName}} {{version}} review approved",
  "review.rejected": "Skill {{skillName}} {{version}} review rejected",
  "promotion.submitted": "Skill {{skillName}} promotion request submitted",
  "promotion.approved": "Skill {{skillName}} promotion approved",
  "promotion.rejected": "Skill {{skillName}} promotion rejected",
  "report.submitted": "Skill {{skillName}} received a new report",
  "report.resolved": "Report for skill {{skillName}} has been resolved",
  "preferences": {
    "title": "Notification Settings",
    "description": "Choose which notifications you want to receive",
    "publish": "Publish notifications",
    "publishDesc": "Notify when a skill is published",
    "review": "Review notifications",
    "reviewDesc": "Notify on review submission, approval, or rejection",
    "promotion": "Promotion notifications",
    "promotionDesc": "Notify on promotion request, approval, or rejection",
    "report": "Report notifications",
    "reportDesc": "Notify on report submission or resolution",
    "saved": "Notification settings saved"
  }
}
```

- [ ] **Step 5: Verify frontend typecheck**

Run: `cd web && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(notification): add frontend API client, types, and i18n keys"
```

---

### Task 15: Frontend — Notification Bell + Dropdown + SSE Hook

**Files:**
- Create: `web/src/features/notification/use-notifications.ts`
- Create: `web/src/features/notification/use-notification-sse.ts`
- Create: `web/src/features/notification/notification-bell.tsx`
- Create: `web/src/features/notification/notification-dropdown.tsx`
- Modify: `web/src/shared/components/user-menu.tsx` — add bell next to avatar

- [ ] **Step 1: Create use-notifications.ts hook**

```typescript
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationApi } from '@/api/client'

export function useUnreadCount() {
  return useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => notificationApi.getUnreadCount(),
    refetchInterval: 60_000, // fallback polling every 60s
  })
}

export function useRecentNotifications() {
  return useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: () => notificationApi.list({ page: 0, size: 5 }),
  })
}

export function useMarkRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => notificationApi.markRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

export function useMarkAllRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => notificationApi.markAllRead(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}
```

- [ ] **Step 2: Create use-notification-sse.ts hook**

```typescript
import { useEffect, useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { WEB_API_PREFIX } from '@/api/client'

export function useNotificationSse(enabled: boolean) {
  const queryClient = useQueryClient()
  const eventSourceRef = useRef<EventSource | null>(null)

  useEffect(() => {
    if (!enabled) return

    const es = new EventSource(`${WEB_API_PREFIX}/notifications/sse`, { withCredentials: true })
    eventSourceRef.current = es

    es.addEventListener('notification', () => {
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count'] })
      queryClient.invalidateQueries({ queryKey: ['notifications', 'recent'] })
    })

    es.onerror = () => {
      // On reconnect, refresh unread count
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count'] })
    }

    return () => {
      es.close()
      eventSourceRef.current = null
    }
  }, [enabled, queryClient])
}
```

- [ ] **Step 3: Create notification-bell.tsx**

```tsx
import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from '@tanstack/react-router'
import { Bell } from 'lucide-react'
import { useUnreadCount, useRecentNotifications, useMarkRead, useMarkAllRead } from './use-notifications'
import { useNotificationSse } from './use-notification-sse'
import { NotificationDropdown } from './notification-dropdown'
import { cn } from '@/shared/lib/utils'

export function NotificationBell() {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const { data: unreadData } = useUnreadCount()
  const unreadCount = unreadData?.count ?? 0

  useNotificationSse(true)

  useEffect(() => {
    if (!open) return
    const handleClick = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open])

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        aria-label={t('notification.bell')}
        className="relative p-1.5 text-foreground hover:opacity-80 transition-opacity focus:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:rounded-md"
        onClick={() => setOpen(prev => !prev)}
      >
        <Bell className="w-5 h-5" />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] flex items-center justify-center rounded-full bg-destructive text-destructive-foreground text-[10px] font-medium px-1">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>
      {open && <NotificationDropdown onClose={() => setOpen(false)} />}
    </div>
  )
}
```

- [ ] **Step 4: Create notification-dropdown.tsx**

```tsx
import { useTranslation } from 'react-i18next'
import { Link } from '@tanstack/react-router'
import { useRecentNotifications, useMarkRead, useMarkAllRead } from './use-notifications'

interface NotificationDropdownProps {
  onClose: () => void
}

function formatRelativeTime(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  return `${days}天前`
}

export function NotificationDropdown({ onClose }: NotificationDropdownProps) {
  const { t } = useTranslation()
  const { data } = useRecentNotifications()
  const markRead = useMarkRead()
  const markAllRead = useMarkAllRead()
  const notifications = data?.content ?? []

  const handleItemClick = (id: number) => {
    markRead.mutate(id)
    onClose()
  }

  return (
    <div className="absolute right-0 top-full z-50 w-80 pt-2">
      <div className="overflow-hidden rounded-md border bg-popover text-popover-foreground shadow-md">
        <div className="flex items-center justify-between px-3 py-2 border-b">
          <span className="text-sm font-medium">{t('notification.title')}</span>
          <button
            type="button"
            className="text-xs text-muted-foreground hover:text-foreground"
            onClick={() => markAllRead.mutate()}
          >
            {t('notification.markAllRead')}
          </button>
        </div>
        <div className="max-h-80 overflow-y-auto">
          {notifications.length === 0 ? (
            <div className="px-3 py-6 text-center text-sm text-muted-foreground">
              {t('notification.empty')}
            </div>
          ) : (
            notifications.map(n => {
              const body = n.bodyJson ? JSON.parse(n.bodyJson) : {}
              return (
                <button
                  key={n.id}
                  type="button"
                  className={`w-full text-left px-3 py-2.5 hover:bg-accent transition-colors border-b last:border-b-0 ${n.status === 'UNREAD' ? 'bg-accent/30' : ''}`}
                  onClick={() => handleItemClick(n.id)}
                >
                  <div className="text-sm">{t(n.title, body)}</div>
                  <div className="text-xs text-muted-foreground mt-0.5">
                    {formatRelativeTime(n.createdAt)}
                  </div>
                </button>
              )
            })
          )}
        </div>
        <Link
          to="/dashboard/notifications"
          className="block text-center text-xs text-muted-foreground hover:text-foreground py-2 border-t"
          onClick={onClose}
        >
          {t('notification.viewAll')}
        </Link>
      </div>
    </div>
  )
}
```

- [ ] **Step 5: Add NotificationBell to navigation**

In `web/src/shared/components/user-menu.tsx`, import and render `<NotificationBell />` before the user menu button. The bell should appear to the left of the user avatar in the nav bar.

- [ ] **Step 6: Verify frontend typecheck and build**

Run: `cd web && npx tsc --noEmit && npm run build`
Expected: No errors

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(notification): add bell component, dropdown, and SSE integration"
```

---

### Task 16: Frontend — Notification Page

**Files:**
- Create: `web/src/pages/notifications.tsx`
- Modify: routing config to add `/dashboard/notifications` route

- [ ] **Step 1: Create notifications.tsx page**

```tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { notificationApi } from '@/api/client'
import { useMarkRead, useMarkAllRead } from '@/features/notification/use-notifications'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { cn } from '@/shared/lib/utils'

const CATEGORIES = ['all', 'PUBLISH', 'REVIEW', 'PROMOTION', 'REPORT'] as const

export default function NotificationsPage() {
  const { t } = useTranslation()
  const [category, setCategory] = useState<string>('all')
  const [page, setPage] = useState(0)
  const markRead = useMarkRead()
  const markAllRead = useMarkAllRead()

  const { data, isLoading } = useQuery({
    queryKey: ['notifications', 'list', category, page],
    queryFn: () => notificationApi.list({
      page,
      size: 20,
      category: category === 'all' ? undefined : category,
    }),
  })

  const notifications = data?.content ?? []
  const totalElements = data?.totalElements ?? 0

  return (
    <div className="space-y-6">
      <DashboardPageHeader title={t('notification.title')}>
        <button
          type="button"
          className="text-sm text-muted-foreground hover:text-foreground"
          onClick={() => markAllRead.mutate()}
        >
          {t('notification.markAllRead')}
        </button>
      </DashboardPageHeader>

      <div className="flex gap-2">
        {CATEGORIES.map(cat => (
          <button
            key={cat}
            type="button"
            className={cn(
              'px-3 py-1.5 text-sm rounded-md transition-colors',
              category === cat
                ? 'bg-primary text-primary-foreground'
                : 'bg-muted text-muted-foreground hover:text-foreground'
            )}
            onClick={() => { setCategory(cat); setPage(0) }}
          >
            {t(`notification.tabs.${cat === 'all' ? 'all' : cat.toLowerCase()}`)}
          </button>
        ))}
      </div>

      <div className="space-y-2">
        {isLoading ? (
          <div className="text-center py-8 text-muted-foreground">Loading...</div>
        ) : notifications.length === 0 ? (
          <div className="text-center py-8 text-muted-foreground">{t('notification.empty')}</div>
        ) : (
          notifications.map(n => {
            const body = n.bodyJson ? JSON.parse(n.bodyJson) : {}
            return (
              <div
                key={n.id}
                className={cn(
                  'p-4 rounded-md border cursor-pointer hover:bg-accent/50 transition-colors',
                  n.status === 'UNREAD' && 'bg-accent/20 border-primary/20'
                )}
                onClick={() => markRead.mutate(n.id)}
              >
                <div className="flex items-start justify-between">
                  <div>
                    <div className="text-sm">{t(n.title, body)}</div>
                    <div className="text-xs text-muted-foreground mt-1">{new Date(n.createdAt).toLocaleString()}</div>
                  </div>
                  {n.status === 'UNREAD' && (
                    <span className="w-2 h-2 rounded-full bg-primary mt-1.5 flex-shrink-0" />
                  )}
                </div>
              </div>
            )
          })
        )}
      </div>

      {totalElements > 20 && (
        <Pagination
          currentPage={page}
          totalPages={Math.ceil(totalElements / 20)}
          onPageChange={setPage}
        />
      )}
    </div>
  )
}
```

- [ ] **Step 2: Add route for /dashboard/notifications**

Add the route to the routing configuration following the existing pattern for dashboard sub-pages.

- [ ] **Step 3: Add "通知" link to user-menu.tsx**

Add a menu item linking to `/dashboard/notifications` in the user menu dropdown.

- [ ] **Step 4: Verify frontend typecheck and build**

Run: `cd web && npx tsc --noEmit && npm run build`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(notification): add notification list page with category tabs"
```

---

### Task 17: Frontend — Notification Preference Settings

**Files:**
- Create: `web/src/features/notification/use-notification-preferences.ts`
- Create: `web/src/features/notification/notification-preference-form.tsx`
- Create: `web/src/pages/settings/notification-settings.tsx`
- Modify: routing config to add `/settings/notifications` route
- Modify: `web/src/shared/components/user-menu.tsx` — add "通知设置" menu item

- [ ] **Step 1: Create use-notification-preferences.ts hook**

```typescript
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationApi } from '@/api/client'
import type { NotificationPreferenceItem } from '@/api/types'

export function useNotificationPreferences() {
  return useQuery({
    queryKey: ['notification-preferences'],
    queryFn: () => notificationApi.getPreferences(),
  })
}

export function useUpdateNotificationPreferences() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (preferences: NotificationPreferenceItem[]) =>
      notificationApi.updatePreferences(preferences),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notification-preferences'] })
    },
  })
}
```

- [ ] **Step 2: Create notification-preference-form.tsx**

```tsx
import { useTranslation } from 'react-i18next'
import { useNotificationPreferences, useUpdateNotificationPreferences } from './use-notification-preferences'

export function NotificationPreferenceForm() {
  const { t } = useTranslation()
  const { data: preferences, isLoading } = useNotificationPreferences()
  const updatePreferences = useUpdateNotificationPreferences()

  if (isLoading || !preferences) return null

  const handleToggle = (category: string, enabled: boolean) => {
    const updated = preferences.map(p =>
      p.category === category ? { ...p, enabled } : p
    )
    updatePreferences.mutate(updated)
  }

  const categories = [
    { key: 'PUBLISH', label: t('notification.preferences.publish'), desc: t('notification.preferences.publishDesc') },
    { key: 'REVIEW', label: t('notification.preferences.review'), desc: t('notification.preferences.reviewDesc') },
    { key: 'PROMOTION', label: t('notification.preferences.promotion'), desc: t('notification.preferences.promotionDesc') },
    { key: 'REPORT', label: t('notification.preferences.report'), desc: t('notification.preferences.reportDesc') },
  ]

  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-lg font-medium">{t('notification.preferences.title')}</h3>
        <p className="text-sm text-muted-foreground">{t('notification.preferences.description')}</p>
      </div>
      <div className="space-y-3">
        {categories.map(cat => {
          const pref = preferences.find(p => p.category === cat.key)
          const enabled = pref?.enabled ?? true
          return (
            <div key={cat.key} className="flex items-center justify-between p-3 rounded-md border">
              <div>
                <div className="text-sm font-medium">{cat.label}</div>
                <div className="text-xs text-muted-foreground">{cat.desc}</div>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={enabled}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${enabled ? 'bg-primary' : 'bg-muted'}`}
                onClick={() => handleToggle(cat.key, !enabled)}
              >
                <span className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${enabled ? 'translate-x-6' : 'translate-x-1'}`} />
              </button>
            </div>
          )
        })}
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Create notification-settings.tsx page**

```tsx
import { NotificationPreferenceForm } from '@/features/notification/notification-preference-form'

export default function NotificationSettingsPage() {
  return (
    <div className="max-w-2xl mx-auto py-6">
      <NotificationPreferenceForm />
    </div>
  )
}
```

- [ ] **Step 4: Add route and menu item**

Add `/settings/notifications` route. Add "通知设置" / "Notification Settings" link to user-menu.tsx under the settings section.

Add i18n key to both locale files:
```json
"notificationSettings": "通知设置"  // zh
"notificationSettings": "Notification Settings"  // en
```

- [ ] **Step 5: Verify frontend typecheck and build**

Run: `cd web && npx tsc --noEmit && npm run build`
Expected: No errors

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(notification): add notification preference settings page"
```

---

### Task 18: Final Integration Verification

- [ ] **Step 1: Run full backend tests**

Run: `cd server && mvn test`
Expected: All tests PASS

- [ ] **Step 2: Run frontend typecheck and build**

Run: `cd web && npx tsc --noEmit && npm run build`
Expected: No errors

- [ ] **Step 3: Start local dev environment and smoke test**

Run: `make dev-all`

Manual verification:
1. Login as `local-user`, check bell icon appears in nav bar
2. Publish a skill, verify notification appears
3. Check notification dropdown shows the notification
4. Click notification, verify it marks as read
5. Visit `/dashboard/notifications`, verify list page works
6. Visit `/settings/notifications`, verify preference toggles work
7. Login as `local-admin`, verify review notifications appear

- [ ] **Step 4: Final commit if any fixes needed**

```bash
git add -A && git commit -m "fix(notification): integration fixes"
```
