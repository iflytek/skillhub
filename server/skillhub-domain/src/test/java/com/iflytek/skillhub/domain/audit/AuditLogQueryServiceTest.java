package com.iflytek.skillhub.domain.audit;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditLogQueryServiceTest {

    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AuditLogQueryService service = new AuditLogQueryService(auditLogRepository);

    @Test
    void list_delegatesToRepository() {
        Page<AuditLog> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(auditLogRepository.search(eq("actor-1"), eq("CREATE"), any())).thenReturn(page);

        Page<AuditLog> result = service.list(0, 10, "actor-1", "CREATE");

        assertThat(result.getTotalElements()).isZero();
    }
}
