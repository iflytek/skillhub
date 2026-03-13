package com.iflytek.skillhub.auth.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.local.LocalCredential;
import com.iflytek.skillhub.auth.local.LocalCredentialRepository;
import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountMergeServiceTest {

    @Mock
    private AccountMergeRequestRepository mergeRequestRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private LocalCredentialRepository localCredentialRepository;
    @Mock
    private IdentityBindingRepository identityBindingRepository;
    @Mock
    private UserRoleBindingRepository userRoleBindingRepository;
    @Mock
    private ApiTokenRepository apiTokenRepository;
    @Mock
    private NamespaceMemberRepository namespaceMemberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountMergeService service;

    @BeforeEach
    void setUp() {
        service = new AccountMergeService(
            mergeRequestRepository,
            userAccountRepository,
            localCredentialRepository,
            identityBindingRepository,
            userRoleBindingRepository,
            apiTokenRepository,
            namespaceMemberRepository,
            passwordEncoder
        );
    }

    @Test
    void initiate_withLocalUsername_createsPendingRequest() {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "secondary@example.com", null);
        LocalCredential secondaryCredential = new LocalCredential("usr_secondary", "secondary", "hash");
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(localCredentialRepository.findByUsernameIgnoreCase("secondary")).willReturn(Optional.of(secondaryCredential));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(mergeRequestRepository.existsBySecondaryUserIdAndStatus("usr_secondary", AccountMergeRequest.STATUS_PENDING))
            .willReturn(false);
        given(localCredentialRepository.findByUserId("usr_primary")).willReturn(Optional.empty());
        given(localCredentialRepository.findByUserId("usr_secondary")).willReturn(Optional.of(secondaryCredential));
        given(passwordEncoder.encode(any())).willReturn("encoded-token");
        given(mergeRequestRepository.save(any(AccountMergeRequest.class))).willAnswer(invocation -> invocation.getArgument(0));

        var result = service.initiate("usr_primary", "secondary");

        assertThat(result.secondaryUserId()).isEqualTo("usr_secondary");
        assertThat(result.verificationToken()).isNotBlank();
        verify(mergeRequestRepository).save(any(AccountMergeRequest.class));
    }

    @Test
    void verify_marksRequestVerifiedWhenTokenMatches() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "", null);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(passwordEncoder.matches("raw-token", "encoded")).willReturn(true);
        given(mergeRequestRepository.save(any(AccountMergeRequest.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.verify("usr_primary", 7L, "raw-token");

        assertThat(request.getStatus()).isEqualTo(AccountMergeRequest.STATUS_VERIFIED);
        verify(mergeRequestRepository).save(request);
    }

    @Test
    void confirm_migratesBindingsRolesTokensAndMemberships() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "", null);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);
        Role role = mock(Role.class);
        given(role.getCode()).willReturn("AUDITOR");
        UserRoleBinding secondaryRole = new UserRoleBinding("usr_secondary", role);
        IdentityBinding binding = new IdentityBinding("usr_secondary", "github", "gh_123", "secondary");
        ApiToken token = new ApiToken("usr_secondary", "cli", "sk_123", "hash", "[]");
        NamespaceMember secondaryMembership = new NamespaceMember(1L, "usr_secondary", NamespaceRole.ADMIN);

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(mergeRequestRepository.save(any(AccountMergeRequest.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(identityBindingRepository.findByUserId("usr_secondary")).willReturn(List.of(binding));
        given(apiTokenRepository.findByUserId("usr_secondary")).willReturn(List.of(token));
        given(userRoleBindingRepository.findByUserId("usr_primary")).willReturn(List.of());
        given(userRoleBindingRepository.findByUserId("usr_secondary")).willReturn(List.of(secondaryRole));
        given(namespaceMemberRepository.findByUserId("usr_secondary")).willReturn(List.of(secondaryMembership));
        given(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "usr_primary")).willReturn(Optional.empty());
        given(localCredentialRepository.findByUserId("usr_primary")).willReturn(Optional.empty());
        given(localCredentialRepository.findByUserId("usr_secondary")).willReturn(Optional.empty());

        service.confirm("usr_primary", 7L);

        assertThat(binding.getUserId()).isEqualTo("usr_primary");
        assertThat(token.getUserId()).isEqualTo("usr_primary");
        assertThat(token.getSubjectId()).isEqualTo("usr_primary");
        assertThat(secondaryMembership.getUserId()).isEqualTo("usr_primary");
        assertThat(secondary.getStatus()).isEqualTo(com.iflytek.skillhub.domain.user.UserStatus.MERGED);
        assertThat(secondary.getMergedToUserId()).isEqualTo("usr_primary");
        assertThat(request.getStatus()).isEqualTo(AccountMergeRequest.STATUS_COMPLETED);
        assertThat(request.getVerificationToken()).isNull();
        verify(userRoleBindingRepository).save(any(UserRoleBinding.class));
        verify(userRoleBindingRepository).deleteAll(List.of(secondaryRole));
    }

    @Test
    void verify_rejectsInvalidToken() throws Exception {
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(passwordEncoder.matches("bad-token", "encoded")).willReturn(false);

        assertThatThrownBy(() -> service.verify("usr_primary", 7L, "bad-token"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.invalidToken");

        verify(identityBindingRepository, never()).saveAll(any());
    }

    private AccountMergeRequest request(String primaryUserId, String secondaryUserId, String token) throws Exception {
        AccountMergeRequest request = new AccountMergeRequest(
            primaryUserId,
            secondaryUserId,
            token,
            LocalDateTime.now().plusMinutes(10)
        );
        Field idField = AccountMergeRequest.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(request, 7L);
        return request;
    }
}
