package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.delivery.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL row locks serialize inbox attachment, sealing and leases across application instances. */
@Repository
public class JdbcNotificationEmailDeliveryRepository implements NotificationEmailDeliveryRepository {
    private final JdbcTemplate jdbc;
    public JdbcNotificationEmailDeliveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Timestamp ts(Instant time) {
        return time == null ? null : Timestamp.from(time);
    }

    private static Instant instant(ResultSet row, String name) throws SQLException {
        Timestamp value = row.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }

    private NotificationEmailDelivery delivery(ResultSet r, int ignored) throws SQLException {
        return new NotificationEmailDelivery(r.getLong("id"), r.getString("recipient_id"), r.getString("kind"),
                r.getString("window_key"), r.getLong("email_revision"), r.getString("status"), r.getInt("attempts"),
                instant(r, "due_at"), r.getString("lease_token"), instant(r, "lease_until"), r.getString("result_code"),
                instant(r, "created_at"), instant(r, "accepted_at"), r.getBoolean("is_recovery_root"),
                (Long) r.getObject("merged_into_id"), instant(r, "recovery_cutoff_at"),
                r.getLong("source_high_watermark"), r.getLong("source_cursor"), r.getBoolean("sources_complete"),
                r.getLong("item_cursor"), r.getBoolean("items_complete"), r.getString("preview_item_ids"),
                r.getLong("item_count"), instant(r, "retry_started_at"), instant(r, "retry_deadline"));
    }

    @Override
    public Optional<NotificationEmailDelivery> find(long id) {
        return jdbc.query("""
                SELECT * FROM notification_email_delivery d WHERE id=? AND purge_started_at IS NULL
                AND NOT EXISTS (SELECT 1 FROM notification_email_delivery p
                  WHERE p.id=d.merged_into_id AND p.purge_started_at IS NOT NULL)
                """, this::delivery, id)
                .stream()
                .findFirst();
    }

    @Override
    public NotificationEmailDelivery lock(long id) {
        return jdbc.query(
                        "SELECT * FROM notification_email_delivery WHERE id=? AND purge_started_at IS NULL FOR UPDATE",
                        this::delivery, id)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public NotificationEmailDelivery openBatch(
            String user, String kind, String key, long revision, Instant due, Instant now) {
        jdbc.update("""
                INSERT INTO notification_email_delivery(recipient_id,kind,window_key,email_revision,due_at,next_attempt_at,created_at)
                VALUES (?,?,?,?,?,?,?) ON CONFLICT(recipient_id,window_key) DO NOTHING
                """, user, kind, key, revision, ts(due), ts(due), ts(now));
        return jdbc
                .query("SELECT * FROM notification_email_delivery WHERE recipient_id=? AND window_key=? FOR UPDATE",
                        this::delivery, user, key)
                .getFirst();
    }

    @Override
    public boolean addItem(long deliveryId, NotificationEmailCommand command, Long skillId, Instant now) {
        int inserted = jdbc.update("""
                INSERT INTO notification_email_item(delivery_id,recipient_id,event_key,category,event_type,skill_id,
                entity_id,occurred_at,enqueued_at) VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT(recipient_id,event_key) DO NOTHING
                """, deliveryId, command.recipientId(), command.eventKey(), command.category().name(),
                command.eventType(), skillId, command.entityId(), ts(command.occurredAt()), ts(now));
        if (inserted == 1) {
            jdbc.update("UPDATE notification_email_delivery SET item_count=item_count+1 WHERE id=?", deliveryId);
        }
        return inserted == 1;
    }

    @Override
    public void suppressItem(long id) {
        jdbc.update("UPDATE notification_email_item SET status='SUPPRESSED' WHERE id=?", id);
    }

    @Override
    public Set<Long> cancelledItemIds(List<Long> ids) {
        if (ids.isEmpty())
            return Set.of();
        if (ids.size() > 100)
            throw new IllegalArgumentException("Cancellation checks require a bounded item page");
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return new HashSet<>(jdbc.queryForList("""
                SELECT i.id FROM notification_email_item i WHERE i.id IN (
                """ + placeholders + """
                ) AND i.id<=GREATEST(
                 coalesce((SELECT through_item_id FROM notification_email_cancel_barrier WHERE scope_key='GLOBAL'),0),
                 coalesce((SELECT through_item_id FROM notification_email_cancel_barrier
                   WHERE scope_key='USER:'||i.recipient_id||':'||i.category),0))
                """, Long.class, ids.toArray()));
    }

    @Override
    public int drainCancelledItems() {
        var barriers = jdbc.queryForList("""
                SELECT * FROM notification_email_cancel_barrier WHERE cursor_id<through_item_id
                ORDER BY updated_at,scope_key LIMIT 1 FOR UPDATE SKIP LOCKED
                """);
        if (barriers.isEmpty())
            return drainSuppressedItems();
        var barrier = barriers.getFirst();
        long through = ((Number) barrier.get("through_item_id")).longValue();
        long cursor = ((Number) barrier.get("cursor_id")).longValue();
        var ids = jdbc.queryForList("""
                SELECT id FROM notification_email_item WHERE id>? AND id<=?
                AND (CAST(? AS VARCHAR) IS NULL OR (recipient_id=? AND category=?)) ORDER BY id LIMIT 100
                """, Long.class, cursor, through, barrier.get("recipient_id"),
                barrier.get("recipient_id"), barrier.get("category"));
        for (long id : ids) {
            // A committed SENDING payload is immutable. Its eventual TEMP retry is stopped by the barrier,
            // even when this cursor has already passed it; completed SENT details retain their original items.
            jdbc.update("""
                    UPDATE notification_email_item i SET status='SUPPRESSED' WHERE i.id=? AND i.status='ACTIVE'
                    AND EXISTS (SELECT 1 FROM notification_email_delivery d
                     LEFT JOIN notification_email_delivery p ON p.id=d.merged_into_id
                     WHERE d.id=i.delivery_id AND coalesce(p.status,d.status) NOT IN ('SENDING','SENT'))
                    """, id);
        }
        long next = ids.size() < 100 ? through : ids.getLast();
        jdbc.update("UPDATE notification_email_cancel_barrier SET cursor_id=? WHERE scope_key=?", next,
                barrier.get("scope_key"));
        return ids.size();
    }

    /** A terminal parent's state is authoritative immediately; physical item writes yield after one page. */
    private int drainSuppressedItems() {
        var roots = jdbc.query("""
                SELECT * FROM notification_email_delivery WHERE status='SUPPRESSED' AND NOT items_complete
                AND merged_into_id IS NULL ORDER BY terminal_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, this::delivery);
        if (roots.isEmpty())
            return 0;
        var root = roots.getFirst();
        var page = itemPage(root.id(), root.itemCursor(), 100);
        for (var item : page) {
            jdbc.update(
                    "UPDATE notification_email_item SET status='SUPPRESSED' WHERE id=? AND status='ACTIVE'", item.id());
        }
        long next = page.isEmpty() ? root.itemCursor() : page.getLast().id();
        jdbc.update(
                "UPDATE notification_email_delivery SET item_cursor=?,items_complete=? WHERE id=? AND status='SUPPRESSED'",
                next, page.size() < 100, root.id());
        return page.size();
    }

    private NotificationEmailItem item(ResultSet r, int ignored) throws SQLException {
        return new NotificationEmailItem(r.getLong("id"), r.getLong("delivery_id"), r.getString("recipient_id"),
                r.getString("event_key"), NotificationCategory.valueOf(r.getString("category")),
                r.getString("event_type"), (Long) r.getObject("skill_id"), (Long) r.getObject("entity_id"),
                instant(r, "occurred_at"), instant(r, "enqueued_at"), r.getString("status"));
    }

    @Override
    public List<NotificationEmailItem> itemPage(long id, long after, int limit) {
        return jdbc.query("""
                SELECT i.* FROM notification_email_item i JOIN notification_email_delivery d ON d.id=i.delivery_id
                WHERE (d.id=? OR d.merged_into_id=?) AND i.id>? ORDER BY i.id LIMIT ?
                """, this::item, id, id, after, limit);
    }

    @Override
    public List<NotificationEmailItem> previewItems(long id, List<Long> ids) {
        if (ids.isEmpty())
            return List.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>(List.of(id, id));
        args.addAll(ids);
        return jdbc.query(
                "SELECT i.* FROM notification_email_item i JOIN notification_email_delivery d ON d.id=i.delivery_id "
                        + "WHERE (d.id=? OR d.merged_into_id=?) AND i.id IN (" + placeholders + ") ORDER BY i.id",
                this::item, args.toArray());
    }

    @Override
    public boolean lockRecipient(String user) {
        return !jdbc.queryForList(
                            "SELECT user_id FROM notification_email_contact WHERE user_id=? FOR UPDATE SKIP LOCKED",
                            String.class, user)
                        .isEmpty();
    }

    @Override
    public Optional<NotificationEmailDelivery> activeRoot(String user) {
        return jdbc
                .query("SELECT * FROM notification_email_delivery WHERE recipient_id=? AND is_recovery_root "
                                + "AND status IN ('PREPARING','PENDING','CLAIMED','SENDING','RETRY') ORDER BY id LIMIT "
                                + "1",
                        this::delivery, user)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<NotificationEmailDelivery> claim(Instant now, Instant until, String token) {
        return claim(now, until, token, null, Set.of());
    }

    @Override
    public Optional<NotificationEmailDelivery> claim(
            Instant now, Instant until, String token, String kind, Set<String> excluded) {
        var users = jdbc.queryForList("""
                SELECT recipient_id FROM notification_email_delivery
                WHERE status IN ('PREPARING','PENDING','RETRY') AND due_at<=? AND next_attempt_at<=?
                AND (CAST(? AS VARCHAR) IS NULL OR kind=?)
                GROUP BY recipient_id ORDER BY min(next_attempt_at),min(id) LIMIT 100
                """, String.class, ts(now), ts(now), kind, kind);
        for (String user : users) {
            if (excluded.contains(user) || !lockRecipient(user))
                continue;
            var root = activeRoot(user).orElse(null);
            if (root != null && (kind == null || "DIGEST".equals(kind))) {
                if (Set.of("PREPARING", "PENDING", "RETRY").contains(root.status()) && !root.dueAt().isAfter(now)) {
                    var due =
                            jdbc.queryForObject("SELECT next_attempt_at<=? FROM notification_email_delivery WHERE id=?",
                                    Boolean.class, ts(now), root.id());
                    if (Boolean.TRUE.equals(due)) {
                        if (root.retryDeadline() != null && !root.retryDeadline().isAfter(now)) {
                            expireRecovery(root.id(), now);
                        } else {
                            jdbc.update("UPDATE notification_email_delivery SET "
                                            + "status='CLAIMED',lease_token=?,lease_until=? WHERE id=?",
                                    token, ts(until), root.id());
                            return find(root.id());
                        }
                    }
                }
            }
            var candidates = jdbc.query("""
                    SELECT * FROM notification_email_delivery WHERE recipient_id=?
                    AND status IN ('PREPARING','PENDING','RETRY') AND due_at<=? AND next_attempt_at<=?
                    AND (CAST(? AS VARCHAR) IS NULL OR kind=?) ORDER BY next_attempt_at,id LIMIT 100 FOR UPDATE SKIP LOCKED
                    """, this::delivery, user, ts(now), ts(now), kind, kind);
            for (var candidate : candidates) {
                if ("DIGEST".equals(candidate.kind()) && root != null && candidate.id() != root.id())
                    continue;
                if (candidate.retryDeadline() != null && !candidate.retryDeadline().isAfter(now)) {
                    jdbc.update("UPDATE notification_email_delivery SET "
                                    + "status='FAILED',result_code='RETRY_WINDOW_EXPIRED',terminal_at=? WHERE id=?",
                            ts(now), candidate.id());
                    continue;
                }
                jdbc.update("UPDATE notification_email_delivery SET status='CLAIMED',lease_token=?,lease_until=? WHERE "
                                + "id=?",
                        token, ts(until), candidate.id());
                return find(candidate.id());
            }
        }
        return Optional.empty();
    }

    @Override
    public NotificationEmailDelivery beginPreparation(long id, String token, Instant now) {
        var row = lock(id);
        if (row == null || !"CLAIMED".equals(row.status()) || !Objects.equals(token, row.leaseToken()))
            return null;
        if (!row.recoveryRoot()) {
            Long high = jdbc.queryForObject(
                    "SELECT coalesce(max(id),0) FROM notification_email_delivery WHERE recipient_id=?", Long.class,
                    row.recipientId());
            jdbc.update("""
                    UPDATE notification_email_delivery SET is_recovery_root=TRUE,status='PREPARING',
                    recovery_cutoff_at=?,source_high_watermark=?,source_cursor=0,sources_complete=FALSE,
                    item_cursor=0,items_complete=FALSE,preview_item_ids='' WHERE id=?
                    """, ts(now), high, id);
        } else {
            if (row.recoveryCutoffAt() == null) {
                Long high = jdbc.queryForObject(
                        "SELECT coalesce(max(id),0) FROM notification_email_delivery WHERE recipient_id=?", Long.class,
                        row.recipientId());
                jdbc.update("UPDATE notification_email_delivery SET recovery_cutoff_at=?,source_high_watermark=? WHERE "
                                + "id=?",
                        ts(now), high, id);
            }
            jdbc.update("UPDATE notification_email_delivery SET status='PREPARING' WHERE id=?", id);
        }
        return find(id).orElseThrow();
    }

    @Override
    public boolean mergeSourcePage(NotificationEmailDelivery root, Instant now) {
        var sources = jdbc.query("""
                SELECT * FROM notification_email_delivery WHERE recipient_id=? AND email_revision=? AND kind='DIGEST'
                AND NOT is_recovery_root AND merged_into_id IS NULL AND id>? AND id<=? AND due_at<=?
                AND status IN ('PENDING','RETRY','CLAIMED') AND coalesce(result_code,'')<>'ADMIN_RETRY'
                ORDER BY id LIMIT 100 FOR UPDATE
                """, this::delivery, root.recipientId(), root.emailRevision(), root.sourceCursor(),
                root.sourceHighWatermark(), ts(root.recoveryCutoffAt()));
        long cursor = root.sourceCursor(), added = 0;
        Instant deadline = root.retryDeadline(), started = root.retryStartedAt();
        for (var source : sources) {
            cursor = source.id();
            if (source.retryDeadline() != null && !source.retryDeadline().isAfter(now)) {
                jdbc.update("UPDATE notification_email_delivery SET "
                                + "status='FAILED',result_code='RETRY_WINDOW_EXPIRED',terminal_at=?,lease_token=NULL,lease_"
                                + "until=NULL WHERE id=?",
                        ts(now), source.id());
                continue;
            }
            if (source.retryDeadline() != null && (deadline == null || source.retryDeadline().isBefore(deadline))) {
                deadline = source.retryDeadline();
                started = source.retryStartedAt();
            }
            jdbc.update("UPDATE notification_email_delivery SET "
                            + "status='MERGED',merged_into_id=?,lease_token=NULL,lease_until=NULL WHERE id=?",
                    root.id(), source.id());
            added += source.itemCount();
        }
        boolean complete = sources.size() < 100;
        jdbc.update("UPDATE notification_email_delivery SET "
                        + "source_cursor=?,sources_complete=?,item_count=item_count+?,retry_started_at=?,retry_deadline=? "
                        + "WHERE id=?",
                cursor, complete, added, ts(started), ts(deadline), root.id());
        return complete;
    }

    @Override
    public void savePreparation(long id, String token, long cursor, boolean complete, String preview, boolean ready) {
        jdbc.update("""
                UPDATE notification_email_delivery SET status=?,item_cursor=?,items_complete=?,preview_item_ids=?,
                lease_token=NULL,lease_until=NULL WHERE id=? AND status='PREPARING' AND lease_token=?
                """, ready ? "PENDING" : "PREPARING", cursor, complete, preview, id, token);
    }

    @Override
    public void temporaryFailure(long id, String token, Instant now, Instant next, int hours) {
        var row = lock(id);
        if (row == null || !"SENDING".equals(row.status()) || !Objects.equals(token, row.leaseToken()))
            return;
        Instant start = row.retryStartedAt() == null ? now : row.retryStartedAt();
        Instant deadline = row.retryDeadline() == null ? start.plusSeconds(hours * 3600L) : row.retryDeadline();
        boolean expired = !deadline.isAfter(now);
        Instant due = next.isBefore(deadline) ? next : deadline;
        jdbc.update("""
                UPDATE notification_email_delivery SET status=?,result_code=?,retry_started_at=?,retry_deadline=?,
                next_attempt_at=?,terminal_at=?,lease_token=NULL,lease_until=NULL WHERE id=?
                """, expired ? "FAILED" : "RETRY", expired ? "RETRY_WINDOW_EXPIRED" : "TEMPORARY_FAILURE", ts(start),
                ts(deadline), ts(due), expired ? ts(now) : null, id);
        if (row.recoveryRoot() && !expired) {
            // The next attempt fixes a new range; existing merged sources and their immutable items stay attached.
            jdbc.update("""
                    UPDATE notification_email_delivery SET recovery_cutoff_at=NULL,sources_complete=FALSE,
                    source_cursor=0,item_cursor=0,items_complete=FALSE,preview_item_ids='' WHERE id=?
                    """, id);
        }
    }

    @Override
    public void expireRecovery(long id, Instant now) {
        jdbc.update("UPDATE notification_email_delivery SET "
                        + "status='FAILED',result_code='RETRY_WINDOW_EXPIRED',terminal_at=?,lease_token=NULL,lease_until="
                        + "NULL WHERE id=? AND retry_deadline<=? AND status IN ('PREPARING','PENDING','CLAIMED','RETRY')",
                ts(now), id, ts(now));
    }

    @Override
    public boolean authorize(long id, String token, Instant now, Instant until) {
        return jdbc.update("""
                UPDATE notification_email_delivery SET status='SENDING',attempts=attempts+1,lease_until=?
                WHERE id=? AND status='CLAIMED' AND lease_token=? AND lease_until>?
                """, ts(until), id, token, ts(now)) == 1;
    }

    @Override
    public boolean finish(long id, String token, String status, String code, Instant next, Instant accepted) {
        return jdbc.update("""
                UPDATE notification_email_delivery SET status=?,result_code=?,next_attempt_at=?,accepted_at=?,
                terminal_at=?,lease_token=NULL,lease_until=NULL WHERE id=? AND status='SENDING' AND lease_token=?
                """, status, code, ts(next), ts(accepted), ts(next), id, token) == 1;
    }

    @Override
    public void suppress(long id, String token, String reason, Instant now) {
        jdbc.update("""
                UPDATE notification_email_delivery SET status='SUPPRESSED',result_code=?,terminal_at=?,lease_token=NULL,lease_until=NULL,
                item_cursor=0,items_complete=FALSE
                WHERE id=? AND status IN ('CLAIMED','PREPARING') AND lease_token=?
                """, reason, ts(now), id, token);
    }

    @Override
    public void recoverExpired(Instant now) {
        // Do not lock batches before their user gate: preparation locks a root followed by its sources.
        var expired = jdbc.query("""
                SELECT * FROM notification_email_delivery
                WHERE status IN ('CLAIMED','PREPARING','SENDING') AND lease_until<=?
                ORDER BY lease_until,id LIMIT 100
                """, this::delivery, ts(now));
        for (var candidate : expired) {
            if (!lockRecipient(candidate.recipientId()))
                continue;
            jdbc.update("""
                    UPDATE notification_email_delivery SET status='RETRY',lease_token=NULL,lease_until=NULL,next_attempt_at=?
                    WHERE id=? AND status IN ('CLAIMED','PREPARING') AND lease_until<=?
                    """, ts(now), candidate.id(), ts(now));
            jdbc.update("""
                    UPDATE notification_email_delivery SET status='UNKNOWN',result_code='LEASE_EXPIRED_AFTER_AUTHORIZATION',
                    terminal_at=?,lease_token=NULL,lease_until=NULL WHERE id=? AND status='SENDING' AND lease_until<=?
                    """, ts(now), candidate.id(), ts(now));
        }
    }

    @Override
    public List<NotificationEmailDelivery> list(String status, int offset, int limit) {
        return jdbc.query("SELECT * FROM notification_email_delivery d WHERE purge_started_at IS NULL "
                        + "AND NOT EXISTS (SELECT 1 FROM notification_email_delivery p "
                        + "WHERE p.id=d.merged_into_id AND p.purge_started_at IS NOT NULL) "
                        + "AND (CAST(? AS VARCHAR) IS NULL OR status=?) "
                        + "ORDER BY id DESC LIMIT ? OFFSET ?",
                this::delivery, status, status, limit, offset);
    }

    @Override
    public long count(String status) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT count(*) FROM notification_email_delivery d WHERE purge_started_at IS NULL "
                        + "AND NOT EXISTS (SELECT 1 FROM notification_email_delivery p "
                        + "WHERE p.id=d.merged_into_id AND p.purge_started_at IS NOT NULL) "
                        + "AND (CAST(? AS VARCHAR) IS NULL OR status=?)",
                Long.class, status, status));
    }

    @Override
    public void retry(long id, Instant now) {
        jdbc.update("""
                UPDATE notification_email_delivery SET status='RETRY',attempts=0,next_attempt_at=?,lease_token=NULL,lease_until=NULL,
                result_code='ADMIN_RETRY',retry_started_at=NULL,retry_deadline=NULL,terminal_at=NULL,
                recovery_cutoff_at=NULL,sources_complete=FALSE,source_cursor=0,item_cursor=0,items_complete=FALSE,preview_item_ids='' WHERE id=? AND purge_started_at IS NULL AND status IN ('FAILED','UNKNOWN')
                """, ts(now), id);
    }

    @Override
    public int cleanup(Instant sentBefore, Instant failedBefore) {
        // Lock roots first, as manual retry does. Once purging starts it cannot be retried or read partially.
        var roots = jdbc.queryForList("""
                SELECT d.id FROM notification_email_delivery d WHERE d.merged_into_id IS NULL
                AND (d.purge_started_at IS NOT NULL OR (
                  ((d.status IN ('SENT','SUPPRESSED') AND coalesce(d.terminal_at,d.created_at)<?)
                    OR (d.status IN ('FAILED','UNKNOWN') AND coalesce(d.terminal_at,d.created_at)<?))
                  AND NOT EXISTS (SELECT 1 FROM notification_email_delivery s WHERE s.merged_into_id=d.id
                    AND s.status<>'MERGED')
                  AND NOT EXISTS (SELECT 1 FROM notification_email_item i JOIN notification_email_delivery s ON s.id=i.delivery_id
                    WHERE (s.id=d.id OR s.merged_into_id=d.id) AND i.enqueued_at>=
                      CASE WHEN d.status IN ('SENT','SUPPRESSED') THEN CAST(? AS TIMESTAMPTZ) ELSE CAST(? AS TIMESTAMPTZ) END)))
                ORDER BY d.next_attempt_at,d.id LIMIT 10 FOR UPDATE OF d SKIP LOCKED
                """, Long.class, ts(sentBefore), ts(failedBefore), ts(sentBefore), ts(failedBefore));
        int removed = 0;
        for (long root : roots) {
            // Only expired, marked roots reuse next_attempt_at for fair cleanup rotation, never sendable batches.
            jdbc.update("""
                    UPDATE notification_email_delivery SET purge_started_at=coalesce(purge_started_at,clock_timestamp()),
                    next_attempt_at=clock_timestamp() WHERE id=?
                    """, root);
            jdbc.update("""
                    DELETE FROM notification_email_item WHERE id IN (
                      SELECT i.id FROM notification_email_item i JOIN notification_email_delivery d ON d.id=i.delivery_id
                      WHERE d.id=? OR d.merged_into_id=? ORDER BY i.id LIMIT 100)
                    """, root, root);
            removed += jdbc.update("""
                    DELETE FROM notification_email_delivery WHERE id IN (
                      SELECT s.id FROM notification_email_delivery s WHERE s.merged_into_id=? AND s.status='MERGED'
                      AND NOT EXISTS (SELECT 1 FROM notification_email_item i WHERE i.delivery_id=s.id)
                      AND NOT EXISTS (SELECT 1 FROM notification_email_delivery c WHERE c.merged_into_id=s.id)
                      ORDER BY s.id LIMIT 100)
                    """, root);
            // Explicit emptiness checks ensure FK cascades have no unbounded work left to do.
            removed += jdbc.update("""
                    DELETE FROM notification_email_delivery d WHERE d.id=? AND d.purge_started_at IS NOT NULL
                    AND NOT EXISTS (SELECT 1 FROM notification_email_item i WHERE i.delivery_id=d.id)
                    AND NOT EXISTS (SELECT 1 FROM notification_email_delivery s WHERE s.merged_into_id=d.id)
                    """, root);
        }
        return removed;
    }
}
