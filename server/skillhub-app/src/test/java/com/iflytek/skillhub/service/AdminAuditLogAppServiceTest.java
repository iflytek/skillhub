package com.iflytek.skillhub.service;

import com.iflytek.skillhub.dto.AuditLogItemResponse;
import com.iflytek.skillhub.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAuditLogAppServiceTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final AdminAuditLogAppService service = new AdminAuditLogAppService(jdbcTemplate);

    @Test
    void listAuditLogs_returnsJdbcBackedPage() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(contains("FROM audit_log"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new AuditLogItemResponse(
                        1L,
                        "USER_STATUS_CHANGE",
                        "user-1",
                        "alice",
                        "{\"status\":\"DISABLED\"}",
                        "127.0.0.1",
                        "req-1",
                        "USER",
                        "42",
                        Instant.parse("2026-03-13T01:00:00Z")
                )));

        PageResponse<?> response = service.listAuditLogs(
                0,
                20,
                "user-1",
                "USER_STATUS_CHANGE",
                "req-1",
                "127.0.0.1",
                "USER",
                "42",
                Instant.parse("2026-03-13T00:00:00Z"),
                Instant.parse("2026-03-14T00:00:00Z"));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        verify(jdbcTemplate).queryForObject(contains("al.actor_user_id = :userId"), any(MapSqlParameterSource.class), eq(Long.class));
        verify(jdbcTemplate).query(contains("al.action = :action"), any(MapSqlParameterSource.class), any(RowMapper.class));
        verify(jdbcTemplate).query(
                contains("al.request_id = :requestId"),
                any(MapSqlParameterSource.class),
                any(RowMapper.class));
        verify(jdbcTemplate).query(
                contains("CAST(al.target_id AS TEXT) = :resourceId"),
                any(MapSqlParameterSource.class),
                any(RowMapper.class));
    }
}
