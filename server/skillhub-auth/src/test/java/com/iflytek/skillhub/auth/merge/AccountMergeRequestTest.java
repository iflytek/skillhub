package com.iflytek.skillhub.auth.merge;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccountMergeRequestTest {

    @Test
    void protectedConstructor_createsInstance() throws Exception {
        java.lang.reflect.Constructor<AccountMergeRequest> ctor = AccountMergeRequest.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        AccountMergeRequest r = ctor.newInstance();
        assertThat(r).isNotNull();
        assertThat(r.getStatus()).isEqualTo(AccountMergeRequest.STATUS_PENDING);
    }

    @Test
    void prePersist_setsCreatedAt() {
        AccountMergeRequest request = new AccountMergeRequest(
                "primary-1", "secondary-1", "token-1", Instant.now().plusSeconds(3600)
        );
        ReflectionTestUtils.invokeMethod(request, "prePersist");
        assertThat(request.getCreatedAt()).isNotNull();
    }

    @Test
    void gettersWork() {
        AccountMergeRequest request = new AccountMergeRequest(
                "primary-1", "secondary-1", "token-1", Instant.now().plusSeconds(3600)
        );
        assertThat(request.getPrimaryUserId()).isEqualTo("primary-1");
        assertThat(request.getSecondaryUserId()).isEqualTo("secondary-1");
        assertThat(request.getVerificationToken()).isEqualTo("token-1");
        assertThat(request.getStatus()).isEqualTo(AccountMergeRequest.STATUS_PENDING);
    }

    @Test
    void settersWork() {
        AccountMergeRequest request = new AccountMergeRequest(
                "primary-1", "secondary-1", "token-1", Instant.now().plusSeconds(3600)
        );
        request.setStatus(AccountMergeRequest.STATUS_VERIFIED);
        request.setVerificationToken("token-2");
        request.setTokenExpiresAt(Instant.now());
        request.setCompletedAt(Instant.now());

        assertThat(request.getStatus()).isEqualTo(AccountMergeRequest.STATUS_VERIFIED);
        assertThat(request.getVerificationToken()).isEqualTo("token-2");
        assertThat(request.getTokenExpiresAt()).isNotNull();
        assertThat(request.getCompletedAt()).isNotNull();
    }
}
