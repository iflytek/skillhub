package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.audit.OrganizationAuditAction;
import com.iflytek.skillhub.domain.audit.OrganizationAuditEvent;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBinding;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.dto.OrganizationCreateRequest;
import com.iflytek.skillhub.dto.OrganizationResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.EnterpriseIdentityQueryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformOrganizationAdminAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMembershipRepository membershipRepository;

    @Mock
    private OrganizationRoleBindingRepository roleBindingRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private EnterpriseIdentityQueryRepository queryRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private RequestIdAccessor requestIdAccessor;

    private PlatformOrganizationAdminAppService service;

    @BeforeEach
    void setUp() {
        service = new PlatformOrganizationAdminAppService(
                organizationRepository,
                membershipRepository,
                roleBindingRepository,
                userAccountRepository,
                queryRepository,
                auditLogService,
                requestIdAccessor,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createBootstrapsExplicitOwnerWithoutMakingActorAMember() {
        given(requestIdAccessor.current()).willReturn("request-1");
        UserAccount owner = new UserAccount(
                "owner-1",
                "Owner One",
                "owner@example.com",
                null
        );
        given(userAccountRepository.findById("owner-1")).willReturn(Optional.of(owner));
        given(organizationRepository.findBySlug("acme")).willReturn(Optional.empty());
        given(organizationRepository.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(membershipRepository.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(roleBindingRepository.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        OrganizationResponse response = service.create(
                new OrganizationCreateRequest("acme", "Acme", "owner-1"),
                "platform-admin"
        );

        ArgumentCaptor<OrganizationMembership> membershipCaptor =
                ArgumentCaptor.forClass(OrganizationMembership.class);
        ArgumentCaptor<OrganizationRoleBinding> bindingCaptor =
                ArgumentCaptor.forClass(OrganizationRoleBinding.class);
        ArgumentCaptor<OrganizationAuditEvent> auditCaptor =
                ArgumentCaptor.forClass(OrganizationAuditEvent.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        verify(roleBindingRepository).save(bindingCaptor.capture());
        verify(auditLogService).record(auditCaptor.capture());

        assertThat(response.slug()).isEqualTo("acme");
        assertThat(response.authorityVersion()).isEqualTo(1);
        assertThat(membershipCaptor.getValue().getUserId()).isEqualTo("owner-1");
        assertThat(membershipCaptor.getValue().getStatus().name()).isEqualTo("ACTIVE");
        assertThat(bindingCaptor.getValue().getUserId()).isEqualTo("owner-1");
        assertThat(bindingCaptor.getValue().getRole()).isEqualTo(OrganizationRole.ORG_OWNER);
        assertThat(bindingCaptor.getValue().getCreatedBy()).isEqualTo("platform-admin");
        assertThat(membershipCaptor.getValue().getUserId()).isNotEqualTo("platform-admin");
        assertThat(auditCaptor.getValue().organizationId()).isEqualTo(response.id());
        assertThat(auditCaptor.getValue().action())
                .isEqualTo(OrganizationAuditAction.ORGANIZATION_CREATED);
        assertThat(auditCaptor.getValue().requestId()).isEqualTo("request-1");
    }

    @Test
    void createRejectsAnAccountThatCannotLogInBeforeAnyOrganizationWrite() {
        UserAccount owner = new UserAccount(
                "owner-1",
                "Owner One",
                "owner@example.com",
                null
        );
        owner.setStatus(UserStatus.DISABLED);
        given(userAccountRepository.findById("owner-1")).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.create(
                new OrganizationCreateRequest("acme", "Acme", "owner-1"),
                "platform-admin"
        ))
                .isInstanceOf(DomainConflictException.class)
                .hasMessage("error.organization.owner.not-eligible");

        verify(organizationRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(membershipRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(roleBindingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
