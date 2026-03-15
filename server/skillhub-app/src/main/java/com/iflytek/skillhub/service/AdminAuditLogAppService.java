package com.iflytek.skillhub.service;

import com.iflytek.skillhub.dto.AuditLogItemResponse;
import com.iflytek.skillhub.dto.PageResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class AdminAuditLogAppService {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public AdminAuditLogAppService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogItemResponse> listAuditLogs(int page,
                                                            int size,
                                                            String userId,
                                                            String action,
                                                            String requestId,
                                                            String ipAddress,
                                                            String resourceType,
                                                            String resourceId,
                                                            Instant startTime,
                                                            Instant endTime) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", Math.max(page, 0) * size);

        String whereClause = buildWhereClause(
                parameters,
                userId,
                action,
                requestId,
                ipAddress,
                resourceType,
                resourceId,
                startTime,
                endTime);
        Long total = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log al" + whereClause,
                parameters,
                Long.class
        );

        List<AuditLogItemResponse> items = namedParameterJdbcTemplate.query(
                """
                SELECT al.id,
                       al.action,
                       al.actor_user_id,
                       ua.display_name,
                       al.detail_json,
                       al.target_type,
                       al.target_id,
                       al.request_id,
                       al.client_ip,
                       al.created_at
                FROM audit_log al
                LEFT JOIN user_account ua ON ua.id = al.actor_user_id
                """ + whereClause + """
                 ORDER BY al.created_at DESC
                 LIMIT :limit OFFSET :offset
                """,
                parameters,
                (rs, rowNum) -> new AuditLogItemResponse(
                        rs.getLong("id"),
                        rs.getString("action"),
                        rs.getString("actor_user_id"),
                        rs.getString("display_name"),
                        renderDetails(
                                rs.getString("detail_json"),
                                rs.getString("target_type"),
                                rs.getObject("target_id")),
                        rs.getString("client_ip"),
                        rs.getString("request_id"),
                        rs.getString("target_type"),
                        toResourceId(rs.getObject("target_id")),
                        toInstant(rs.getTimestamp("created_at")))
        );

        return new PageResponse<>(items, total == null ? 0 : total, page, size);
    }

    private String buildWhereClause(MapSqlParameterSource parameters,
                                    String userId,
                                    String action,
                                    String requestId,
                                    String ipAddress,
                                    String resourceType,
                                    String resourceId,
                                    Instant startTime,
                                    Instant endTime) {
        StringBuilder clause = new StringBuilder(" WHERE 1 = 1");
        if (StringUtils.hasText(userId)) {
            clause.append(" AND al.actor_user_id = :userId");
            parameters.addValue("userId", userId.trim());
        }
        if (StringUtils.hasText(action)) {
            clause.append(" AND al.action = :action");
            parameters.addValue("action", action.trim());
        }
        if (StringUtils.hasText(requestId)) {
            clause.append(" AND al.request_id = :requestId");
            parameters.addValue("requestId", requestId.trim());
        }
        if (StringUtils.hasText(ipAddress)) {
            clause.append(" AND al.client_ip = :ipAddress");
            parameters.addValue("ipAddress", ipAddress.trim());
        }
        if (StringUtils.hasText(resourceType)) {
            clause.append(" AND al.target_type = :resourceType");
            parameters.addValue("resourceType", resourceType.trim());
        }
        if (StringUtils.hasText(resourceId)) {
            clause.append(" AND CAST(al.target_id AS TEXT) = :resourceId");
            parameters.addValue("resourceId", resourceId.trim());
        }
        if (startTime != null) {
            clause.append(" AND al.created_at >= :startTime");
            parameters.addValue("startTime", Timestamp.from(startTime));
        }
        if (endTime != null) {
            clause.append(" AND al.created_at <= :endTime");
            parameters.addValue("endTime", Timestamp.from(endTime));
        }
        return clause.toString();
    }

    private String renderDetails(String detailJson, String targetType, Object targetId) {
        if (StringUtils.hasText(detailJson)) {
            return detailJson;
        }
        if (!StringUtils.hasText(targetType) && targetId == null) {
            return null;
        }
        return targetType + ":" + targetId;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String toResourceId(Object targetId) {
        return targetId == null ? null : String.valueOf(targetId);
    }
}
