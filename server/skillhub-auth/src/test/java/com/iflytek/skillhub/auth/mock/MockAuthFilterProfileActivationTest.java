package com.iflytek.skillhub.auth.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MockAuthFilterProfileActivationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MockAuthFilter.class))
            .withBean(UserAccountRepository.class, () -> mock(UserAccountRepository.class))
            .withBean(UserRoleBindingRepository.class, () -> mock(UserRoleBindingRepository.class))
            .withBean(PlatformSessionService.class, () -> mock(PlatformSessionService.class))
            .withPropertyValues("skillhub.auth.mock.enabled=true");

    @Test
    void mockAuthFilter_isActiveForDevProfile() {
        runner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).hasSingleBean(MockAuthFilter.class));
    }

    @Test
    void mockAuthFilter_isNotActiveWithoutSupportedProfile() {
        runner.withPropertyValues("spring.profiles.active=qa")
                .run(context -> assertThat(context).doesNotHaveBean(MockAuthFilter.class));
    }
}
