package com.iflytek.skillhub.service;

import com.iflytek.skillhub.dto.AuditLogItemResponse;
import com.iflytek.skillhub.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.mockito.ArgumentCaptor;
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

    @Test
    void listAuditLogs_withNullAction_passesNullActions() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbcTemplate.query(contains("FROM audit_log"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        PageResponse<?> response = service.listAuditLogs(
                0, 20, null, null, null, null, null, null, null, null);

        assertThat(response.total()).isEqualTo(0);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void listAuditLogsByActions_withNullTotal_returnsZeroTotal() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(null);
        when(jdbcTemplate.query(contains("FROM audit_log"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        PageResponse<?> response = service.listAuditLogsByActions(
                0, 20, null, null, null, null, null, null, null, null);

        assertThat(response.total()).isEqualTo(0);
    }

    @Test
    void listAuditLogsByActions_rowMapper_mapsResultSet() throws Exception {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);

        ArgumentCaptor<RowMapper<?>> rowMapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(contains("FROM audit_log"), any(MapSqlParameterSource.class), rowMapperCaptor.capture()))
                .thenReturn(List.of());

        service.listAuditLogsByActions(0, 20, null, null, null, null, null, null, null, null);

        RowMapper<?> rowMapper = rowMapperCaptor.getValue();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("action")).thenReturn("CREATE");
        when(rs.getString("actor_user_id")).thenReturn("user-1");
        when(rs.getString("display_name")).thenReturn("alice");
        when(rs.getString("detail_json")).thenReturn("{\"foo\":\"bar\"}");
        when(rs.getString("target_type")).thenReturn("SKILL");
        when(rs.getObject("target_id")).thenReturn(42L);
        when(rs.getString("client_ip")).thenReturn("127.0.0.1");
        when(rs.getString("request_id")).thenReturn("req-1");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf("2026-03-13 01:00:00"));

        AuditLogItemResponse item = (AuditLogItemResponse) rowMapper.mapRow(rs, 1);

        assertThat(item.id()).isEqualTo(1L);
        assertThat(item.action()).isEqualTo("CREATE");
        assertThat(item.details()).isEqualTo("{\"foo\":\"bar\"}");
        assertThat(item.resourceType()).isEqualTo("SKILL");
        assertThat(item.resourceId()).isEqualTo("42");
        assertThat(item.timestamp()).isEqualTo(Timestamp.valueOf("2026-03-13 01:00:00").toInstant());
    }

    @Test
    void listAuditLogsByActions_rowMapper_withNullDetailJson_returnsTargetTypeAndId() throws Exception {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);

        ArgumentCaptor<RowMapper<?>> rowMapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(contains("FROM audit_log"), any(MapSqlParameterSource.class), rowMapperCaptor.capture()))
                .thenReturn(List.of());

        service.listAuditLogsByActions(0, 20, null, null, null, null, null, null, null, null);

        RowMapper<?> rowMapper = rowMapperCaptor.getValue();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("action")).thenReturn("CREATE");
        when(rs.getString("actor_user_id")).thenReturn("user-1");
        when(rs.getString("display_name")).thenReturn("alice");
        when(rs.getString("detail_json")).thenReturn(null);
        when(rs.getString("target_type")).thenReturn("SKILL");
        when(rs.getObject("target_id")).thenReturn(42L);
        when(rs.getString("client_ip")).thenReturn("127.0.0.1");
        when(rs.getString("request_id")).thenReturn("req-1");
        when(rs.getTimestamp("created_at")).thenReturn(null);

        AuditLogItemResponse item = (AuditLogItemResponse) rowMapper.mapRow(rs, 1);

        assertThat(item.details()).isEqualTo("SKILL:42");
        assertThat(item.timestamp()).isNull();
    }

    @Test
    void listAuditLogsByActions_rowMapper_withNullDetailJsonAndNullTarget_returnsNullDetails() throws Exception {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);

        ArgumentCaptor<RowMapper<?>> rowMapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(contains("FROM audit_log"), any(MapSqlParameterSource.class), rowMapperCaptor.capture()))
                .thenReturn(List.of());

        service.listAuditLogsByActions(0, 20, null, null, null, null, null, null, null, null);

        RowMapper<?> rowMapper = rowMapperCaptor.getValue();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("action")).thenReturn("CREATE");
        when(rs.getString("actor_user_id")).thenReturn("user-1");
        when(rs.getString("display_name")).thenReturn("alice");
        when(rs.getString("detail_json")).thenReturn(null);
        when(rs.getString("target_type")).thenReturn(null);
        when(rs.getObject("target_id")).thenReturn(null);
        when(rs.getString("client_ip")).thenReturn("127.0.0.1");
        when(rs.getString("request_id")).thenReturn("req-1");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf("2026-03-13 01:00:00"));

        AuditLogItemResponse item = (AuditLogItemResponse) rowMapper.mapRow(rs, 1);

        assertThat(item.details()).isNull();
    }

    @Test
    void renderDetails_withDetailJson_returnsDetailJson() {
        String result = (String) ReflectionTestUtils.invokeMethod(service, "renderDetails", "{\"foo\":\"bar\"}", "SKILL", 42L);
        assertThat(result).isEqualTo("{\"foo\":\"bar\"}");
    }

    @Test
    void renderDetails_withNullDetailJsonAndTarget_returnsTargetString() {
        String result = (String) ReflectionTestUtils.invokeMethod(service, "renderDetails", null, "SKILL", 42L);
        assertThat(result).isEqualTo("SKILL:42");
    }

    @Test
    void renderDetails_withNullEverything_returnsNull() {
        String result = (String) ReflectionTestUtils.invokeMethod(service, "renderDetails", null, null, null);
        assertThat(result).isNull();
    }

    @Test
    void toInstant_withNull_returnsNull() {
        Instant result = (Instant) ReflectionTestUtils.invokeMethod(service, "toInstant", (Timestamp) null);
        assertThat(result).isNull();
    }

    @Test
    void toInstant_withTimestamp_returnsInstant() {
        Timestamp ts = Timestamp.valueOf("2026-03-13 01:00:00");
        Instant result = (Instant) ReflectionTestUtils.invokeMethod(service, "toInstant", ts);
        assertThat(result).isEqualTo(ts.toInstant());
    }

    @Test
    void toResourceId_withNull_returnsNull() {
        String result = (String) ReflectionTestUtils.invokeMethod(service, "toResourceId", (Object) null);
        assertThat(result).isNull();
    }

    @Test
    void toResourceId_withValue_returnsString() {
        String result = (String) ReflectionTestUtils.invokeMethod(service, "toResourceId", 42L);
        assertThat(result).isEqualTo("42");
    }
}
