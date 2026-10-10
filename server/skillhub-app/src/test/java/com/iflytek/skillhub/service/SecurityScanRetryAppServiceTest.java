package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.security.ScanTask;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityAudit;
import com.iflytek.skillhub.domain.security.SecurityAuditRepository;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecurityScanRetryAppServiceTest {

    @Mock private SkillRepository skillRepository;
    @Mock private SkillVersionRepository skillVersionRepository;
    @Mock private SecurityAuditRepository securityAuditRepository;
    @Mock private SecurityScanService securityScanService;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private AuditLogService auditLogService;

    private SecurityScanRetryAppService service;
    private Skill skill;
    private SkillVersion version;

    @BeforeEach
    void setUp() {
        service = new SecurityScanRetryAppService(
                skillRepository,
                skillVersionRepository,
                securityAuditRepository,
                securityScanService,
                objectStorageService,
                auditLogService
        );
        skill = skill(8L, "owner-1");
        version = version(42L, SkillVersionStatus.SCAN_FAILED);
        given(skillRepository.findById(8L)).willReturn(Optional.of(skill));
    }

    @Test
    void retry_asOwnerCreatesNewAttemptAndAuditLog() {
        given(skillVersionRepository.findStatusByIdAndSkillId(42L, 8L))
                .willReturn(Optional.of(SkillVersionStatus.SCAN_FAILED));
        given(skillVersionRepository.findByIdForUpdate(42L)).willReturn(Optional.of(version));
        given(securityScanService.isEnabled()).willReturn(true);
        given(objectStorageService.exists("packages/8/42/bundle.zip")).willReturn(true);
        given(securityScanService.retryStoredBundleScan(version, "packages/8/42/bundle.zip", "owner-1"))
                .willReturn(new ScanTask("task-new", 42L, null, "packages/8/42/bundle.zip",
                        "owner-1", 1L, Map.of()));

        var result = service.retry(
                8L, 42L, "owner-1", Set.of(), Map.of(), new AuditRequestContext("127.0.0.1", "test"));

        assertThat(result.status()).isEqualTo("SCANNING");
        verify(securityScanService).retryStoredBundleScan(version, "packages/8/42/bundle.zip", "owner-1");
        verify(auditLogService).record(
                "owner-1", "RETRY_SECURITY_SCAN", "SKILL_VERSION", 42L,
                null, "127.0.0.1", "test", "{\"taskId\":\"task-new\",\"version\":\"1.0.0\"}");
    }

    @Test
    void retry_allowsNamespaceAdminAndPlatformSecurityAdmin() {
        given(skillVersionRepository.findStatusByIdAndSkillId(42L, 8L))
                .willReturn(Optional.of(SkillVersionStatus.SCAN_FAILED));
        given(skillVersionRepository.findByIdForUpdate(42L)).willReturn(Optional.of(version));
        given(securityScanService.isEnabled()).willReturn(true);
        given(objectStorageService.exists("packages/8/42/bundle.zip")).willReturn(true);
        given(securityScanService.retryStoredBundleScan(any(), any(), any()))
                .willReturn(new ScanTask("task-new", 42L, null, "bundle", "admin", 1L, Map.of()));

        service.retry(8L, 42L, "namespace-admin", Set.of(), Map.of(5L, NamespaceRole.ADMIN),
                new AuditRequestContext(null, null));
        version.setStatus(SkillVersionStatus.SCAN_FAILED);
        service.retry(8L, 42L, "security-admin", Set.of("SKILL_ADMIN"), Map.of(),
                new AuditRequestContext(null, null));

        verify(securityScanService, org.mockito.Mockito.times(2)).retryStoredBundleScan(any(), any(), any());
    }

    @Test
    void retry_rejectsUnauthorizedUserBeforeReadingVersionState() {
        assertThatThrownBy(() -> service.retry(
                8L, 42L, "viewer", Set.of(), Map.of(), new AuditRequestContext(null, null)))
                .isInstanceOf(DomainForbiddenException.class);

        verify(skillVersionRepository, never()).findByIdForUpdate(any());
        verify(skillVersionRepository, never()).findStatusByIdAndSkillId(any(), any());
    }

    @Test
    void retry_rejectsNonFailedVersion() {
        version.setStatus(SkillVersionStatus.PENDING_REVIEW);
        given(skillVersionRepository.findStatusByIdAndSkillId(42L, 8L))
                .willReturn(Optional.of(SkillVersionStatus.PENDING_REVIEW));

        assertThatThrownBy(() -> service.retry(
                8L, 42L, "owner-1", Set.of(), Map.of(), new AuditRequestContext(null, null)))
                .isInstanceOf(DomainBadRequestException.class);

        verify(securityScanService, never()).retryStoredBundleScan(any(), any(), any());
        verify(skillVersionRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void retry_rejectsMissingStoredBundle() {
        given(skillVersionRepository.findStatusByIdAndSkillId(42L, 8L))
                .willReturn(Optional.of(SkillVersionStatus.SCAN_FAILED));
        given(securityScanService.isEnabled()).willReturn(true);

        assertThatThrownBy(() -> service.retry(
                8L, 42L, "owner-1", Set.of(), Map.of(), new AuditRequestContext(null, null)))
                .isInstanceOf(DomainBadRequestException.class);

        verify(securityScanService, never()).retryStoredBundleScan(any(), any(), any());
        verify(skillVersionRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void retry_whenAttemptAlreadyStartedReturnsCurrentStateWithoutDuplicateTask() {
        version.setStatus(SkillVersionStatus.SCANNING);
        given(skillVersionRepository.findStatusByIdAndSkillId(42L, 8L))
                .willReturn(Optional.of(SkillVersionStatus.SCANNING));
        given(skillVersionRepository.findByIdForUpdate(42L)).willReturn(Optional.of(version));
        given(securityScanService.isEnabled()).willReturn(true);
        given(securityAuditRepository.findLatestActiveByVersionIdAndScannerType(42L, ScannerType.SKILL_SCANNER))
                .willReturn(Optional.of(new SecurityAudit(42L, ScannerType.SKILL_SCANNER, "task-existing")));

        var result = service.retry(
                8L, 42L, "owner-1", Set.of(), Map.of(), new AuditRequestContext(null, null));

        assertThat(result.status()).isEqualTo("SCANNING");
        verify(securityScanService, never()).retryStoredBundleScan(any(), any(), any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private Skill skill(Long id, String ownerId) {
        Skill value = new Skill(5L, "demo", ownerId, SkillVisibility.PRIVATE);
        setField(value, "id", id);
        return value;
    }

    private SkillVersion version(Long id, SkillVersionStatus status) {
        SkillVersion value = new SkillVersion(8L, "1.0.0", "owner-1");
        setField(value, "id", id);
        value.setStatus(status);
        return value;
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
