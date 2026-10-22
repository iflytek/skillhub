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
                instant(r, "created_at"), instant(r, "accepted_at"));
    }

    @Override
    public Optional<NotificationEmailDelivery> find(long id) {
        return jdbc.query("SELECT * FROM notification_email_delivery WHERE id=?", this::delivery, id)
                .stream()
                .findFirst();
    }

    @Override
    public NotificationEmailDelivery lock(long id) {
        return jdbc.query("SELECT * FROM notification_email_delivery WHERE id=? FOR UPDATE", this::delivery, id)
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
        return jdbc.update("""
                INSERT INTO notification_email_item(delivery_id,recipient_id,event_key,category,event_type,skill_id,
                entity_id,occurred_at,enqueued_at) VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT(recipient_id,event_key) DO NOTHING
                """, deliveryId, command.recipientId(), command.eventKey(), command.category().name(),
                       command.eventType(), skillId, command.entityId(), ts(command.occurredAt()), ts(now))
                == 1;
    }

    @Override
    public List<NotificationEmailItem> items(long deliveryId) {
        return jdbc.query("SELECT * FROM notification_email_item WHERE delivery_id=? ORDER BY id",
                (r, n)
                        -> new NotificationEmailItem(r.getLong("id"), r.getLong("delivery_id"),
                                r.getString("recipient_id"), r.getString("event_key"),
                                NotificationCategory.valueOf(r.getString("category")), r.getString("event_type"),
                                (Long) r.getObject("skill_id"), (Long) r.getObject("entity_id"),
                                instant(r, "occurred_at"), instant(r, "enqueued_at"), r.getString("status")),
                deliveryId);
    }

    @Override
    public void suppressItem(long id) {
        jdbc.update("UPDATE notification_email_item SET status='SUPPRESSED' WHERE id=?", id);
    }

    @Override
    public Optional<NotificationEmailDelivery> claim(Instant now, Instant until, String token) {
        return jdbc.query("""
                WITH candidate AS (
                SELECT id FROM notification_email_delivery WHERE status IN ('PENDING','RETRY')
                AND due_at<=? AND next_attempt_at<=? ORDER BY next_attempt_at,id FOR UPDATE SKIP LOCKED LIMIT 1)
                UPDATE notification_email_delivery d SET status='CLAIMED',lease_token=?,lease_until=?
                FROM candidate c WHERE d.id=c.id RETURNING d.*
                """, this::delivery, ts(now), ts(now), token, ts(until)).stream().findFirst();
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
                lease_token=NULL,lease_until=NULL WHERE id=? AND status='SENDING' AND lease_token=?
                """, status, code, ts(next), ts(accepted), id, token) == 1;
    }

    @Override
    public void suppress(long id, String token, String reason) {
        jdbc.update("""
                UPDATE notification_email_delivery SET status='SUPPRESSED',result_code=?,lease_token=NULL,lease_until=NULL
                WHERE id=? AND status='CLAIMED' AND lease_token=?
                """, reason, id, token);
        jdbc.update("UPDATE notification_email_item SET status='SUPPRESSED' WHERE delivery_id=?", id);
    }

    @Override
    public void recoverExpired(Instant now) {
        jdbc.update("""
                UPDATE notification_email_delivery SET status='RETRY',lease_token=NULL,lease_until=NULL,next_attempt_at=?
                WHERE status='CLAIMED' AND lease_until<=?
                """, ts(now), ts(now));
        jdbc.update("""
                UPDATE notification_email_delivery SET status='UNKNOWN',result_code='LEASE_EXPIRED_AFTER_AUTHORIZATION',
                lease_token=NULL,lease_until=NULL WHERE status='SENDING' AND lease_until<=?
                """, ts(now));
    }

    @Override
    public List<NotificationEmailDelivery> list(String status, int offset, int limit) {
        return jdbc.query("SELECT * FROM notification_email_delivery WHERE (CAST(? AS VARCHAR) IS NULL OR status=?) "
                        + "ORDER BY id DESC LIMIT ? OFFSET ?",
                this::delivery, status, status, limit, offset);
    }

    @Override
    public long count(String status) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT count(*) FROM notification_email_delivery WHERE (CAST(? AS VARCHAR) IS NULL OR status=?)",
                Long.class, status, status));
    }

    @Override
    public void retry(long id, Instant now) {
        jdbc.update("""
                UPDATE notification_email_delivery SET status='RETRY',attempts=0,next_attempt_at=?,lease_token=NULL,lease_until=NULL,
                result_code='ADMIN_RETRY' WHERE id=? AND status IN ('FAILED','UNKNOWN')
                """, ts(now), id);
    }

    @Override
    public int cleanup(Instant sentBefore, Instant failedBefore) {
        return jdbc.update("""
                DELETE FROM notification_email_delivery WHERE
                (status IN ('SENT','SUPPRESSED') AND created_at<?) OR (status IN ('FAILED','UNKNOWN') AND created_at<?)
                """, ts(sentBefore), ts(failedBefore));
    }
}
