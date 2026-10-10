package com.iflytek.skillhub.domain.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecurityScanOutboxTest {
    @Mock SecurityAuditRepository auditRepository;
    @Mock SkillVersionRepository versionRepository;
    @Mock ScanTaskProducer producer;
    @Mock ScanTaskOutboxRepository outboxRepository;

    @Test
    void triggerPersistsAuditStateAndOutboxWithoutPublishingInsideTransaction() throws Exception {
        SkillVersion version = new SkillVersion(9L, "1.0.0", "publisher");
        Field id = SkillVersion.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(version, 42L);
        given(versionRepository.findById(42L)).willReturn(Optional.of(version));
        SecurityScanService service = new SecurityScanService(auditRepository, versionRepository, producer,
                new ObjectMapper(), "upload", true, outboxRepository);

        service.triggerScan(42L, List.of(new PackageEntry("SKILL.md", new byte[0], 0, "text/markdown")), "publisher");

        ArgumentCaptor<ScanTaskOutbox> outbox = ArgumentCaptor.forClass(ScanTaskOutbox.class);
        verify(outboxRepository).save(outbox.capture());
        verify(producer, never()).publishScanTask(org.mockito.ArgumentMatchers.any());
        assertThat(outbox.getValue().getVersionId()).isEqualTo(42L);
        assertThat(outbox.getValue().getStatus()).isEqualTo(ScanTaskOutboxStatus.PENDING);
    }

    @Test
    void softDeleteRemovesPendingOutboxEvenWhenNoActiveAuditExists() {
        given(auditRepository.findAllActiveBySkillVersionId(42L)).willReturn(List.of());
        SecurityScanService service = new SecurityScanService(auditRepository, versionRepository, producer,
                new ObjectMapper(), "upload", true, outboxRepository);

        service.softDeleteByVersionId(42L);

        verify(outboxRepository).deleteByVersionId(42L);
        verify(auditRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
