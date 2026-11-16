package com.ktb.moyeota.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SettlementReminderWriter {

    private static final String REMINDER_TYPE = "SETTLEMENT_COMPLETION_REMINDER";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void insertNotifications(List<NewReminder> reminders, LocalDateTime now) {
        if (reminders.isEmpty()) {
            return;
        }
        SqlParameterSource[] params = reminders.stream()
                .map(reminder -> new MapSqlParameterSource()
                        .addValue("recipientId", reminder.recipientId())
                        .addValue("type", REMINDER_TYPE)
                        .addValue("settlementId", reminder.settlementId())
                        .addValue("chatRoomId", reminder.chatRoomId())
                        .addValue("content", reminder.content())
                        .addValue("now", now))
                .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                INSERT INTO notifications (recipient_id, type, event_id, target_chat_room_id, content, created_at)
                VALUES (:recipientId, :type, :settlementId, :chatRoomId, :content, :now)
                """, params);
    }

    public void remindNotifications(List<RemindedNotification> reminders, LocalDateTime now) {
        if (reminders.isEmpty()) {
            return;
        }
        SqlParameterSource[] params = reminders.stream()
                .map(reminder -> new MapSqlParameterSource()
                        .addValue("id", reminder.notificationId())
                        .addValue("content", reminder.content())
                        .addValue("now", now))
                .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                UPDATE notifications
                SET content = :content, created_at = :now, read_at = NULL
                WHERE id = :id
                """, params);
    }

    public void reopenJobs(List<Long> jobIds, LocalDateTime now) {
        if (jobIds.isEmpty()) {
            return;
        }
        SqlParameterSource[] params = jobIds.stream()
                .map(jobId -> new MapSqlParameterSource().addValue("id", jobId).addValue("now", now))
                .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                UPDATE notification_push_jobs
                SET status = 'PENDING', attempts = 0, next_attempt_at = :now, last_error = NULL, sent_at = NULL,
                    updated_at = :now
                WHERE id = :id
                  AND status IN ('SENT', 'FAILED', 'SKIPPED')
                """, params);
    }

    public void insertMissingJobs(Collection<Long> recipientIds, Collection<Long> settlementIds, LocalDateTime now) {
        jdbcTemplate.update("""
                INSERT INTO notification_push_jobs (notification_id, status, attempts, next_attempt_at, created_at,
                                                    updated_at)
                SELECT n.id, 'PENDING', 0, :now, :now, :now
                FROM notifications n
                WHERE n.recipient_id IN (:recipientIds)
                  AND n.type = :type
                  AND n.event_id IN (:settlementIds)
                  AND n.created_at = :now
                  AND NOT EXISTS (SELECT 1 FROM notification_push_jobs j WHERE j.notification_id = n.id)
                ORDER BY n.id
                """, scope(recipientIds, settlementIds, now));
    }

    public List<OpenedJob> findOpenedJobs(Collection<Long> recipientIds, Collection<Long> settlementIds,
                                          LocalDateTime now) {
        return jdbcTemplate.query("""
                SELECT n.event_id, j.id
                FROM notification_push_jobs j
                JOIN notifications n ON n.id = j.notification_id
                WHERE n.recipient_id IN (:recipientIds)
                  AND n.type = :type
                  AND n.event_id IN (:settlementIds)
                  AND j.status = 'PENDING'
                  AND j.updated_at = :now
                ORDER BY j.id
                """, scope(recipientIds, settlementIds, now),
                (rs, rowNum) -> new OpenedJob(rs.getLong(1), rs.getLong(2)));
    }

    private static MapSqlParameterSource scope(Collection<Long> recipientIds, Collection<Long> settlementIds,
                                               LocalDateTime now) {
        return new MapSqlParameterSource()
                .addValue("recipientIds", recipientIds)
                .addValue("settlementIds", settlementIds)
                .addValue("type", REMINDER_TYPE)
                .addValue("now", now);
    }

    public record NewReminder(Long recipientId, Long settlementId, Long chatRoomId, String content) {
    }

    public record RemindedNotification(Long notificationId, String content) {
    }

    public record OpenedJob(Long settlementId, Long jobId) {
    }
}
