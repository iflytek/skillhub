package com.iflytek.skillhub.service;

import com.iflytek.skillhub.dto.AuditLogItemResponse;
import com.iflytek.skillhub.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAuditLogAppServiceTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private AdminAuditLogAppService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        service = new AdminAuditLogAppService(jdbcTemplate);
    }

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
        verify(jdbcTemplate).query(contains("al.action IN (:actions)"), any(MapSqlParameterSource.class), any(RowMapper.class));
        verify(jdbcTemplate).query(
                contains("al.request_id = :requestId"),
                any(MapSqlParameterSource.class),
                any(RowMapper.class));
        verify(jdbcTemplate).query(
                contains("CAST(al.target_id AS TEXT) = :resourceId"),
                any(MapSqlParameterSource.class),
                any(RowMapper.class));
    }

    /**
     * Regression for the 8-hour offset bug: row mapper must read created_at via
     * getObject(OffsetDateTime.class) so the returned Instant is independent of
     * the JVM default timezone.
     */
    @Test
    void rowMapper_readsCreatedAtAsInstant() throws Exception {
        RowMapper<AuditLogItemResponse> rowMapper = captureRowMapper();
        ResultSet rs = stubRowWithCreatedAt(
                OffsetDateTime.of(2026, 5, 29, 8, 53, 0, 0, ZoneOffset.UTC));

        AuditLogItemResponse item = rowMapper.mapRow(rs, 0);

        assertThat(item).isNotNull();
        assertThat(item.timestamp()).isEqualTo(Instant.parse("2026-05-29T08:53:00Z"));
        verify(rs, never()).getTimestamp(anyString());
    }

    @Test
    void rowMapper_normalisesNonUtcOffsetToInstant() throws Exception {
        RowMapper<AuditLogItemResponse> rowMapper = captureRowMapper();
        ResultSet rs = stubRowWithCreatedAt(
                OffsetDateTime.of(2026, 5, 29, 16, 53, 0, 0, ZoneOffset.ofHours(8)));

        AuditLogItemResponse item = rowMapper.mapRow(rs, 0);

        assertThat(item.timestamp()).isEqualTo(Instant.parse("2026-05-29T08:53:00Z"));
    }

    @Test
    void rowMapper_returnsNullTimestampWhenColumnIsNull() throws Exception {
        RowMapper<AuditLogItemResponse> rowMapper = captureRowMapper();
        ResultSet rs = stubRowWithCreatedAt(null);

        AuditLogItemResponse item = rowMapper.mapRow(rs, 0);

        assertThat(item.timestamp()).isNull();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "2026-03-13T00:00:00Z, 2026-03-14T00:00:00Z",
            "2026-03-13T00:00:00Z, NULL",
            "NULL,                 2026-03-14T00:00:00Z"
    })
    void buildWhereClause_bindsTimeRangeAsOffsetDateTime(String startStr, String endStr) {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbcTemplate.query(contains("FROM audit_log"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        Instant startTime = startStr == null ? null : Instant.parse(startStr);
        Instant endTime = endStr == null ? null : Instant.parse(endStr);

        service.listAuditLogs(0, 20, null, null, null, null, null, null, startTime, endTime);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(contains("FROM audit_log"), paramsCaptor.capture(), any(RowMapper.class));
        MapSqlParameterSource params = paramsCaptor.getValue();
        if (startTime != null) {
            assertThat(params.getValue("startTime"))
                    .isEqualTo(OffsetDateTime.ofInstant(startTime, ZoneOffset.UTC));
        } else {
            assertThat(params.hasValue("startTime")).isFalse();
        }
        if (endTime != null) {
            assertThat(params.getValue("endTime"))
                    .isEqualTo(OffsetDateTime.ofInstant(endTime, ZoneOffset.UTC));
        } else {
            assertThat(params.hasValue("endTime")).isFalse();
        }
    }

    @Test
    void rowMapper_isIndependentOfJvmDefaultTimezone() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            RowMapper<AuditLogItemResponse> rowMapper = captureRowMapper();
            ResultSet rs = stubRowWithCreatedAt(
                    OffsetDateTime.of(2026, 5, 29, 8, 53, 0, 0, ZoneOffset.UTC));

            AuditLogItemResponse item = rowMapper.mapRow(rs, 0);

            assertThat(item).isNotNull();
            assertThat(item.timestamp()).isEqualTo(Instant.parse("2026-05-29T08:53:00Z"));
            verify(rs, never()).getTimestamp(anyString());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @SuppressWarnings("unchecked")
    private RowMapper<AuditLogItemResponse> captureRowMapper() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        ArgumentCaptor<RowMapper<AuditLogItemResponse>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(contains("FROM audit_log"), any(MapSqlParameterSource.class), captor.capture()))
                .thenReturn(List.of());
        service.listAuditLogs(0, 20, null, null, null, null, null, null, null, null);
        return captor.getValue();
    }

    private static ResultSet stubRowWithCreatedAt(OffsetDateTime createdAt) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("action")).thenReturn("PROMOTION_SUBMIT");
        when(rs.getString("actor_user_id")).thenReturn("user-1");
        when(rs.getString("display_name")).thenReturn("alice");
        when(rs.getString("detail_json")).thenReturn("{}");
        when(rs.getString("target_type")).thenReturn("PROMOTION");
        when(rs.getObject("target_id")).thenReturn(42L);
        when(rs.getString("client_ip")).thenReturn("127.0.0.1");
        when(rs.getString("request_id")).thenReturn("req-1");
        when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(createdAt);
        return rs;
    }
}
