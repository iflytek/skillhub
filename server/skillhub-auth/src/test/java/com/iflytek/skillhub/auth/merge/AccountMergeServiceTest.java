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
import com.iflytek.skillhub.domain.user.UserStatus;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC);
        service = new AccountMergeService(
            mergeRequestRepository,
            userAccountRepository,
            localCredentialRepository,
            identityBindingRepository,
            userRoleBindingRepository,
            apiTokenRepository,
            namespaceMemberRepository,
            passwordEncoder,
            clock
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
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-03-18T00:30:00Z"));
        verify(mergeRequestRepository).save(any(AccountMergeRequest.class));
    }

    @Test
    void initiate_withOAuthIdentifier_createsPendingRequest() {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "secondary@example.com", null);
        IdentityBinding binding = new IdentityBinding("usr_secondary", "github", "gh_123", "secondary");
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(identityBindingRepository.findByProviderCodeAndSubject("github", "gh_123")).willReturn(Optional.of(binding));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(mergeRequestRepository.existsBySecondaryUserIdAndStatus("usr_secondary", AccountMergeRequest.STATUS_PENDING))
            .willReturn(false);
        given(localCredentialRepository.findByUserId("usr_primary")).willReturn(Optional.empty());
        given(localCredentialRepository.findByUserId("usr_secondary")).willReturn(Optional.empty());
        given(passwordEncoder.encode(any())).willReturn("encoded-token");
        given(mergeRequestRepository.save(any(AccountMergeRequest.class))).willAnswer(invocation -> invocation.getArgument(0));

        var result = service.initiate("usr_primary", "github:gh_123");

        assertThat(result.secondaryUserId()).isEqualTo("usr_secondary");
    }

    @Test
    void initiate_nullIdentifier_throwsBadRequest() {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));

        assertThatThrownBy(() -> service.initiate("usr_primary", null))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.identifierRequired");
    }

    @Test
    void initiate_blankIdentifier_throwsBadRequest() {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));

        assertThatThrownBy(() -> service.initiate("usr_primary", "   "))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.identifierRequired");
    }

    @Test
    void initiate_invalidOAuthIdentifierFormat_throwsBadRequest() {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));

        assertThatThrownBy(() -> service.initiate("usr_primary", "github:"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.identifierInvalid");
    }

    @Test
    void initiate_existingPendingRequest_throwsConflict() {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "secondary@example.com", null);
        LocalCredential secondaryCredential = new LocalCredential("usr_secondary", "secondary", "hash");
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(localCredentialRepository.findByUsernameIgnoreCase("secondary")).willReturn(Optional.of(secondaryCredential));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(mergeRequestRepository.existsBySecondaryUserIdAndStatus("usr_secondary", AccountMergeRequest.STATUS_PENDING))
            .willReturn(true);

        assertThatThrownBy(() -> service.initiate("usr_primary", "secondary"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.pendingExists");
    }

    @Test
    void initiate_bothUsersHaveLocalCredentials_throwsConflict() {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "secondary@example.com", null);
        LocalCredential primaryCredential = new LocalCredential("usr_primary", "primary", "hash1");
        LocalCredential secondaryCredential = new LocalCredential("usr_secondary", "secondary", "hash2");
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(localCredentialRepository.findByUsernameIgnoreCase("secondary")).willReturn(Optional.of(secondaryCredential));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(mergeRequestRepository.existsBySecondaryUserIdAndStatus("usr_secondary", AccountMergeRequest.STATUS_PENDING))
            .willReturn(false);
        given(localCredentialRepository.findByUserId("usr_primary")).willReturn(Optional.of(primaryCredential));
        given(localCredentialRepository.findByUserId("usr_secondary")).willReturn(Optional.of(secondaryCredential));

        assertThatThrownBy(() -> service.initiate("usr_primary", "secondary"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.localCredentialConflict");
    }

    @Test
    void initiate_sameAccount_throwsBadRequest() {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        LocalCredential primaryCredential = new LocalCredential("usr_primary", "primary", "hash");
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(localCredentialRepository.findByUsernameIgnoreCase("primary")).willReturn(Optional.of(primaryCredential));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));

        assertThatThrownBy(() -> service.initiate("usr_primary", "primary"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.sameAccount");
    }

    @Test
    void initiate_secondaryNotActive_throwsBadRequest() {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "secondary@example.com", null);
        secondary.setStatus(UserStatus.DISABLED);
        LocalCredential secondaryCredential = new LocalCredential("usr_secondary", "secondary", "hash");
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(localCredentialRepository.findByUsernameIgnoreCase("secondary")).willReturn(Optional.of(secondaryCredential));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));

        assertThatThrownBy(() -> service.initiate("usr_primary", "secondary"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.secondaryNotActive");
    }

    @Test
    void initiate_neitherHasLocalCredential_createsRequest() {
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
    void verify_requestNotFound_throwsNotFound() throws Exception {
        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("usr_primary", 7L, "token"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.requestNotFound");
    }

    @Test
    void verify_requestNotPending_throwsBadRequest() throws Exception {
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));

        assertThatThrownBy(() -> service.verify("usr_primary", 7L, "token"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.requestNotPending");
    }

    @Test
    void verify_nullTokenExpiresAt_throwsBadRequest() throws Exception {
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setTokenExpiresAt(null);

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));

        assertThatThrownBy(() -> service.verify("usr_primary", 7L, "token"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.tokenExpired");
    }

    @Test
    void verify_expiredToken_throwsBadRequest() throws Exception {
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setTokenExpiresAt(Instant.parse("2026-03-17T23:59:00Z"));

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));

        assertThatThrownBy(() -> service.verify("usr_primary", 7L, "token"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.tokenExpired");
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

    @Test
    void verify_primaryUserNotFound_throwsNotFound() throws Exception {
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(passwordEncoder.matches("token", "encoded")).willReturn(true);
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("usr_primary", 7L, "token"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.primaryNotFound");
    }

    @Test
    void verify_primaryUserNotActive_throwsBadRequest() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        primary.setStatus(UserStatus.DISABLED);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(passwordEncoder.matches("token", "encoded")).willReturn(true);
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));

        assertThatThrownBy(() -> service.verify("usr_primary", 7L, "token"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.primaryNotActive");
    }

    @Test
    void verify_secondaryUserNotFound_throwsNotFound() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(passwordEncoder.matches("token", "encoded")).willReturn(true);
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("usr_primary", 7L, "token"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.secondaryNotFound");
    }

    @Test
    void verify_secondaryNotActive_throwsBadRequest() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "secondary@example.com", null);
        secondary.setStatus(UserStatus.DISABLED);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(passwordEncoder.matches("token", "encoded")).willReturn(true);
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));

        assertThatThrownBy(() -> service.verify("usr_primary", 7L, "token"))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.secondaryNotActive");
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
        assertThat(secondary.getStatus()).isEqualTo(UserStatus.MERGED);
        assertThat(secondary.getMergedToUserId()).isEqualTo("usr_primary");
        assertThat(request.getStatus()).isEqualTo(AccountMergeRequest.STATUS_COMPLETED);
        assertThat(request.getCompletedAt()).isEqualTo(Instant.parse("2026-03-18T00:00:00Z"));
        assertThat(request.getVerificationToken()).isNull();
        verify(userRoleBindingRepository).save(any(UserRoleBinding.class));
        verify(userRoleBindingRepository).deleteAll(List.of(secondaryRole));
    }

    @Test
    void confirm_requestNotFound_throwsNotFound() throws Exception {
        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm("usr_primary", 7L))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.requestNotFound");
    }

    @Test
    void confirm_requestNotVerified_throwsBadRequest() throws Exception {
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));

        assertThatThrownBy(() -> service.confirm("usr_primary", 7L))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.requestNotVerified");
    }

    @Test
    void confirm_secondaryUserNotFound_throwsNotFound() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm("usr_primary", 7L))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.secondaryNotFound");
    }

    @Test
    void confirm_sameAccount_throwsBadRequest() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        AccountMergeRequest request = request("usr_primary", "usr_primary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));

        assertThatThrownBy(() -> service.confirm("usr_primary", 7L))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.sameAccount");
    }

    @Test
    void confirm_secondaryNotActive_throwsBadRequest() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "secondary@example.com", null);
        secondary.setStatus(UserStatus.DISABLED);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));

        assertThatThrownBy(() -> service.confirm("usr_primary", 7L))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.secondaryNotActive");
    }

    @Test
    void confirm_namespaceMembershipPromotesWhenSecondaryHasHigherRole() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "", null);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);
        NamespaceMember primaryMembership = new NamespaceMember(1L, "usr_primary", NamespaceRole.MEMBER);
        NamespaceMember secondaryMembership = new NamespaceMember(1L, "usr_secondary", NamespaceRole.ADMIN);

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(mergeRequestRepository.save(any(AccountMergeRequest.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(identityBindingRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(apiTokenRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(userRoleBindingRepository.findByUserId(any())).willReturn(List.of());
        given(namespaceMemberRepository.findByUserId("usr_secondary")).willReturn(List.of(secondaryMembership));
        given(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "usr_primary")).willReturn(Optional.of(primaryMembership));
        given(localCredentialRepository.findByUserId("usr_primary")).willReturn(Optional.empty());
        given(localCredentialRepository.findByUserId("usr_secondary")).willReturn(Optional.empty());

        service.confirm("usr_primary", 7L);

        assertThat(primaryMembership.getRole()).isEqualTo(NamespaceRole.ADMIN);
        verify(namespaceMemberRepository).save(primaryMembership);
        verify(namespaceMemberRepository).deleteByNamespaceIdAndUserId(1L, "usr_secondary");
    }

    @Test
    void confirm_namespaceMembershipNoPromoteWhenSecondaryHasLowerRole() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "", null);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);
        NamespaceMember primaryMembership = new NamespaceMember(1L, "usr_primary", NamespaceRole.ADMIN);
        NamespaceMember secondaryMembership = new NamespaceMember(1L, "usr_secondary", NamespaceRole.MEMBER);

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(mergeRequestRepository.save(any(AccountMergeRequest.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(identityBindingRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(apiTokenRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(userRoleBindingRepository.findByUserId(any())).willReturn(List.of());
        given(namespaceMemberRepository.findByUserId("usr_secondary")).willReturn(List.of(secondaryMembership));
        given(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "usr_primary")).willReturn(Optional.of(primaryMembership));
        given(localCredentialRepository.findByUserId("usr_primary")).willReturn(Optional.empty());
        given(localCredentialRepository.findByUserId("usr_secondary")).willReturn(Optional.empty());

        service.confirm("usr_primary", 7L);

        assertThat(primaryMembership.getRole()).isEqualTo(NamespaceRole.ADMIN);
        verify(namespaceMemberRepository, never()).save(any());
        verify(namespaceMemberRepository).deleteByNamespaceIdAndUserId(1L, "usr_secondary");
    }

    @Test
    void confirm_namespaceMembershipPromotesToOwner() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "", null);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);
        NamespaceMember primaryMembership = new NamespaceMember(1L, "usr_primary", NamespaceRole.ADMIN);
        NamespaceMember secondaryMembership = new NamespaceMember(1L, "usr_secondary", NamespaceRole.OWNER);

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(mergeRequestRepository.save(any(AccountMergeRequest.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(identityBindingRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(apiTokenRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(userRoleBindingRepository.findByUserId(any())).willReturn(List.of());
        given(namespaceMemberRepository.findByUserId("usr_secondary")).willReturn(List.of(secondaryMembership));
        given(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "usr_primary")).willReturn(Optional.of(primaryMembership));
        given(localCredentialRepository.findByUserId("usr_primary")).willReturn(Optional.empty());
        given(localCredentialRepository.findByUserId("usr_secondary")).willReturn(Optional.empty());

        service.confirm("usr_primary", 7L);

        assertThat(primaryMembership.getRole()).isEqualTo(NamespaceRole.OWNER);
        verify(namespaceMemberRepository).save(primaryMembership);
        verify(namespaceMemberRepository).deleteByNamespaceIdAndUserId(1L, "usr_secondary");
    }

    @Test
    void confirm_migratesEmailWhenPrimaryHasNone() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", null, null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "secondary@example.com", null);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(mergeRequestRepository.save(any(AccountMergeRequest.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(identityBindingRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(apiTokenRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(userRoleBindingRepository.findByUserId(any())).willReturn(List.of());
        given(namespaceMemberRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(localCredentialRepository.findByUserId("usr_primary")).willReturn(Optional.empty());
        given(localCredentialRepository.findByUserId("usr_secondary")).willReturn(Optional.empty());

        service.confirm("usr_primary", 7L);

        assertThat(primary.getEmail()).isEqualTo("secondary@example.com");
        verify(userAccountRepository).save(primary);
    }

    @Test
    void confirm_doesNotMigrateEmailWhenPrimaryHasOne() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "secondary@example.com", null);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(mergeRequestRepository.save(any(AccountMergeRequest.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(identityBindingRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(apiTokenRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(userRoleBindingRepository.findByUserId(any())).willReturn(List.of());
        given(namespaceMemberRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(localCredentialRepository.findByUserId("usr_primary")).willReturn(Optional.empty());
        given(localCredentialRepository.findByUserId("usr_secondary")).willReturn(Optional.empty());

        service.confirm("usr_primary", 7L);

        assertThat(primary.getEmail()).isEqualTo("primary@example.com");
    }

    @Test
    void confirm_migratesLocalCredentialWhenOnlySecondaryHasOne() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "secondary@example.com", null);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);
        LocalCredential secondaryCredential = new LocalCredential("usr_secondary", "secondary", "hash");

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(mergeRequestRepository.save(any(AccountMergeRequest.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(identityBindingRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(apiTokenRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(userRoleBindingRepository.findByUserId(any())).willReturn(List.of());
        given(namespaceMemberRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(localCredentialRepository.findByUserId("usr_primary")).willReturn(Optional.empty());
        given(localCredentialRepository.findByUserId("usr_secondary")).willReturn(Optional.of(secondaryCredential));

        service.confirm("usr_primary", 7L);

        assertThat(secondaryCredential.getUserId()).isEqualTo("usr_primary");
        verify(localCredentialRepository).save(secondaryCredential);
    }

    @Test
    void confirm_noLocalCredentialMigrationWhenNeitherHasOne() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "secondary@example.com", null);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(mergeRequestRepository.save(any(AccountMergeRequest.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(identityBindingRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(apiTokenRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(userRoleBindingRepository.findByUserId(any())).willReturn(List.of());
        given(namespaceMemberRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(localCredentialRepository.findByUserId("usr_primary")).willReturn(Optional.empty());
        given(localCredentialRepository.findByUserId("usr_secondary")).willReturn(Optional.empty());

        service.confirm("usr_primary", 7L);

        verify(localCredentialRepository, never()).save(any());
    }

    @Test
    void confirm_skipsDuplicateRoleWhenPrimaryAlreadyHasIt() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "", null);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);
        Role role = mock(Role.class);
        given(role.getCode()).willReturn("USER");
        UserRoleBinding primaryRole = new UserRoleBinding("usr_primary", role);
        UserRoleBinding secondaryRole = new UserRoleBinding("usr_secondary", role);

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(mergeRequestRepository.save(any(AccountMergeRequest.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(identityBindingRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(apiTokenRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(userRoleBindingRepository.findByUserId("usr_primary")).willReturn(List.of(primaryRole));
        given(userRoleBindingRepository.findByUserId("usr_secondary")).willReturn(List.of(secondaryRole));
        given(namespaceMemberRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(localCredentialRepository.findByUserId("usr_primary")).willReturn(Optional.empty());
        given(localCredentialRepository.findByUserId("usr_secondary")).willReturn(Optional.empty());

        service.confirm("usr_primary", 7L);

        verify(userRoleBindingRepository, never()).save(any());
        verify(userRoleBindingRepository).deleteAll(List.of(secondaryRole));
    }

    @Test
    void confirm_migratesApiTokenWithoutUserSubjectType() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "", null);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);
        ApiToken token = new ApiToken("usr_secondary", "cli", "sk_123", "hash", "[]");
        org.springframework.test.util.ReflectionTestUtils.setField(token, "subjectType", "APP");
        token.setSubjectId("app_1");

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(mergeRequestRepository.save(any(AccountMergeRequest.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(identityBindingRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(apiTokenRepository.findByUserId("usr_secondary")).willReturn(List.of(token));
        given(userRoleBindingRepository.findByUserId(any())).willReturn(List.of());
        given(namespaceMemberRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(localCredentialRepository.findByUserId("usr_primary")).willReturn(Optional.empty());
        given(localCredentialRepository.findByUserId("usr_secondary")).willReturn(Optional.empty());

        service.confirm("usr_primary", 7L);

        assertThat(token.getUserId()).isEqualTo("usr_primary");
        assertThat(token.getSubjectId()).isEqualTo("app_1");
    }

    @Test
    void confirm_bothHaveLocalCredentials_throwsConflict() throws Exception {
        UserAccount primary = new UserAccount("usr_primary", "primary", "primary@example.com", null);
        UserAccount secondary = new UserAccount("usr_secondary", "secondary", "secondary@example.com", null);
        AccountMergeRequest request = request("usr_primary", "usr_secondary", "encoded");
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);
        LocalCredential primaryCredential = new LocalCredential("usr_primary", "primary", "hash1");
        LocalCredential secondaryCredential = new LocalCredential("usr_secondary", "secondary", "hash2");

        given(mergeRequestRepository.findByIdAndPrimaryUserId(7L, "usr_primary")).willReturn(Optional.of(request));
        given(userAccountRepository.findById("usr_primary")).willReturn(Optional.of(primary));
        given(userAccountRepository.findById("usr_secondary")).willReturn(Optional.of(secondary));
        given(identityBindingRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(apiTokenRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(userRoleBindingRepository.findByUserId(any())).willReturn(List.of());
        given(namespaceMemberRepository.findByUserId("usr_secondary")).willReturn(List.of());
        given(localCredentialRepository.findByUserId("usr_primary")).willReturn(Optional.of(primaryCredential));
        given(localCredentialRepository.findByUserId("usr_secondary")).willReturn(Optional.of(secondaryCredential));

        assertThatThrownBy(() -> service.confirm("usr_primary", 7L))
            .isInstanceOf(AuthFlowException.class)
            .hasMessageContaining("error.auth.merge.localCredentialConflict");
    }

    private AccountMergeRequest request(String primaryUserId, String secondaryUserId, String token) throws Exception {
        AccountMergeRequest request = new AccountMergeRequest(
            primaryUserId,
            secondaryUserId,
            token,
            Instant.now(clock).plusSeconds(600)
        );
        Field idField = AccountMergeRequest.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(request, 7L);
        return request;
    }
}
