package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelService;
import com.iflytek.skillhub.dto.AdminLabelCreateRequest;
import com.iflytek.skillhub.dto.AdminLabelUpdateRequest;
import com.iflytek.skillhub.dto.LabelDefinitionResponse;
import com.iflytek.skillhub.dto.LabelSortOrderItemRequest;
import com.iflytek.skillhub.dto.LabelSortOrderUpdateRequest;
import com.iflytek.skillhub.dto.LabelTranslationItemRequest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LabelAdminAppServiceTest {

    private final LabelDefinitionService labelDefinitionService = mock(LabelDefinitionService.class);
    private final SkillLabelService skillLabelService = mock(SkillLabelService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RbacService rbacService = mock(RbacService.class);
    private final LabelSearchSyncService labelSearchSyncService = mock(LabelSearchSyncService.class);
    private final LabelAdminAppService service = new LabelAdminAppService(
            labelDefinitionService,
            skillLabelService,
            auditLogService,
            rbacService,
            labelSearchSyncService
    );

    @Test
    void create_returnsDefinitionResponseAndRecordsAudit() {
        LabelDefinition created = label(10L, "official", LabelType.RECOMMENDED, true, 1);
        when(rbacService.getUserRoleCodes("admin")).thenReturn(Set.of("SUPER_ADMIN"));
        when(labelDefinitionService.create(eq("official"), eq(LabelType.RECOMMENDED), eq(true), eq(1), any(), eq("admin"), eq(Set.of("SUPER_ADMIN"))))
                .thenReturn(created);
        when(labelDefinitionService.listTranslations(10L))
                .thenReturn(List.of(new LabelTranslation(10L, "en", "Official")));

        LabelDefinitionResponse response = service.create(
                new AdminLabelCreateRequest(
                        "official",
                        LabelType.RECOMMENDED,
                        true,
                        1,
                        List.of(new LabelTranslationItemRequest("en", "Official"))
                ),
                "admin",
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        assertThat(response.slug()).isEqualTo("official");
        assertThat(response.translations()).extracting(com.iflytek.skillhub.dto.LabelTranslationResponse::displayName)
                .containsExactly("Official");
        verify(auditLogService).record(eq("admin"), eq("LABEL_CREATE"), eq("LABEL"), eq(10L), any(), eq("127.0.0.1"), eq("JUnit"), eq("{\"slug\":\"official\"}"));
        verify(labelSearchSyncService, never()).rebuildSkills(any());
    }

    @Test
    void update_snapshotsAffectedSkillsBeforeRebuild() {
        LabelDefinition existing = label(10L, "official", LabelType.RECOMMENDED, true, 1);
        LabelDefinition updated = label(10L, "official", LabelType.PRIVILEGED, false, 3);
        when(rbacService.getUserRoleCodes("admin")).thenReturn(Set.of("SUPER_ADMIN"));
        when(labelDefinitionService.getBySlug("official")).thenReturn(existing);
        when(skillLabelService.listByLabelId(10L)).thenReturn(List.of(
                new SkillLabel(100L, 10L, "owner-1"),
                new SkillLabel(200L, 10L, "owner-2"),
                new SkillLabel(100L, 10L, "owner-1")
        ));
        when(labelDefinitionService.update(eq("official"), eq(LabelType.PRIVILEGED), eq(false), eq(3), any(), eq(Set.of("SUPER_ADMIN"))))
                .thenReturn(updated);
        when(labelDefinitionService.listTranslations(10L))
                .thenReturn(List.of(new LabelTranslation(10L, "en", "Official")));

        LabelDefinitionResponse response = service.update(
                "official",
                new AdminLabelUpdateRequest(
                        LabelType.PRIVILEGED,
                        false,
                        3,
                        List.of(new LabelTranslationItemRequest("en", "Official"))
                ),
                "admin",
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        assertThat(response.type()).isEqualTo("PRIVILEGED");
        verify(labelSearchSyncService).rebuildSkills(List.of(100L, 200L));
        verify(auditLogService).record(eq("admin"), eq("LABEL_UPDATE"), eq("LABEL"), eq(10L), any(), eq("127.0.0.1"), eq("JUnit"), eq("{\"slug\":\"official\"}"));
    }

    @Test
    void delete_snapshotsAffectedSkillsBeforeCascadeDelete() {
        LabelDefinition existing = label(10L, "official", LabelType.RECOMMENDED, true, 1);
        when(rbacService.getUserRoleCodes("admin")).thenReturn(Set.of("SUPER_ADMIN"));
        when(labelDefinitionService.getBySlug("official")).thenReturn(existing);
        when(skillLabelService.listByLabelId(10L)).thenReturn(List.of(
                new SkillLabel(100L, 10L, "owner-1"),
                new SkillLabel(300L, 10L, "owner-3")
        ));

        service.delete("official", "admin", new AuditRequestContext("127.0.0.1", "JUnit"));

        verify(labelDefinitionService).delete("official", Set.of("SUPER_ADMIN"));
        verify(labelSearchSyncService).rebuildSkills(List.of(100L, 300L));
        verify(auditLogService).record(eq("admin"), eq("LABEL_DELETE"), eq("LABEL"), eq(10L), any(), eq("127.0.0.1"), eq("JUnit"), eq("{\"slug\":\"official\"}"));
    }

    @Test
    void updateSortOrder_mapsSlugsToDomainUpdates() {
        LabelDefinition official = label(10L, "official", LabelType.RECOMMENDED, true, 0);
        LabelDefinition featured = label(11L, "featured", LabelType.RECOMMENDED, true, 1);
        when(rbacService.getUserRoleCodes("admin")).thenReturn(Set.of("SUPER_ADMIN"));
        when(labelDefinitionService.getBySlug("official")).thenReturn(official);
        when(labelDefinitionService.getBySlug("featured")).thenReturn(featured);
        when(labelDefinitionService.updateSortOrders(any(), eq(Set.of("SUPER_ADMIN"))))
                .thenReturn(List.of(featured, official));
        when(labelDefinitionService.listTranslations(10L)).thenReturn(List.of());
        when(labelDefinitionService.listTranslations(11L)).thenReturn(List.of());

        List<LabelDefinitionResponse> responses = service.updateSortOrder(
                new LabelSortOrderUpdateRequest(List.of(
                        new LabelSortOrderItemRequest("official", 2),
                        new LabelSortOrderItemRequest("featured", 0)
                )),
                "admin",
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        assertThat(responses).hasSize(2);
        verify(labelDefinitionService).updateSortOrders(any(), eq(Set.of("SUPER_ADMIN")));
        verify(auditLogService).record(eq("admin"), eq("LABEL_SORT_ORDER_UPDATE"), eq("LABEL"), eq(null), any(), eq("127.0.0.1"), eq("JUnit"), eq("{\"count\":2}"));
    }

    private LabelDefinition label(Long id, String slug, LabelType type, boolean visibleInFilter, int sortOrder) {
        LabelDefinition labelDefinition = new LabelDefinition(slug, type, visibleInFilter, sortOrder, "admin");
        ReflectionTestUtils.setField(labelDefinition, "id", id);
        ReflectionTestUtils.setField(labelDefinition, "createdAt", Instant.parse("2026-03-20T00:00:00Z"));
        return labelDefinition;
    }
}
