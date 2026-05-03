package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisSessionRuntimeSelectionTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RedisAutoConfiguration.class,
                    SessionAutoConfiguration.class
            ))
            .withUserConfiguration(NoOpRedisConfigureActionConfig.class);

    @Test
    void redisProvider_wiresRedisBackedSessionRepository() {
        contextRunner
                .withInitializer(context -> applyRuntimeStateDefaults((ConfigurableEnvironment) context.getEnvironment()))
                .withPropertyValues(redisPropertyValues("skillhub:test:session:wiring"))
                .run(context -> {
                    assertThat(context).hasSingleBean(SessionRepository.class);
                    assertThat(context.getBean(SessionRepository.class))
                            .isInstanceOfAny(RedisIndexedSessionRepository.class, RedisSessionRepository.class);
                });
    }

    @Test
    void redisProvider_restoresAuthenticatedSessionAcrossContextRestart() {
        String namespace = "skillhub:test:session:persist:" + System.nanoTime();
        String[] sessionIdHolder = new String[1];

        contextRunner
                .withInitializer(context -> applyRuntimeStateDefaults((ConfigurableEnvironment) context.getEnvironment()))
                .withPropertyValues(redisPropertyValues(namespace))
                .run(context -> {
                    SessionRepository<Session> repository = sessionRepository(context);
                    Session session = repository.createSession();
                    session.setAttribute(
                            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                            authenticatedSecurityContext()
                    );
                    repository.save(session);
                    sessionIdHolder[0] = session.getId();
                });

        contextRunner
                .withInitializer(context -> applyRuntimeStateDefaults((ConfigurableEnvironment) context.getEnvironment()))
                .withPropertyValues(redisPropertyValues(namespace))
                .run(context -> {
                    SessionRepository<Session> repository = sessionRepository(context);
                    Session restoredSession = repository.findById(sessionIdHolder[0]);

                    assertThat(restoredSession).isNotNull();
                    SecurityContext securityContext = restoredSession.getAttribute(
                            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
                    );
                    assertThat(securityContext).isNotNull();
                    assertThat(securityContext.getAuthentication()).isNotNull();
                    assertThat(securityContext.getAuthentication().getPrincipal()).isInstanceOf(PlatformPrincipal.class);
                    PlatformPrincipal principal = (PlatformPrincipal) securityContext.getAuthentication().getPrincipal();
                    assertThat(principal.userId()).isEqualTo("redis-session-user");
                    assertThat(principal.platformRoles()).containsExactly("USER");
                });
    }

    private String[] redisPropertyValues(String namespace) {
        return new String[] {
                "skillhub.runtime.state.provider=redis",
                "spring.session.store-type=redis",
                "spring.data.redis.host=" + REDIS.getHost(),
                "spring.data.redis.port=" + REDIS.getMappedPort(6379),
                "spring.session.redis.namespace=" + namespace
        };
    }

    private SecurityContext authenticatedSecurityContext() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "redis-session-user",
                "Redis Session User",
                "redis-session-user@example.com",
                "",
                "session",
                Set.of("USER")
        );
        SecurityContextImpl securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
        return securityContext;
    }

    @SuppressWarnings("unchecked")
    private SessionRepository<Session> sessionRepository(AssertableWebApplicationContext context) {
        return (SessionRepository<Session>) context.getBean(SessionRepository.class);
    }

    private void applyRuntimeStateDefaults(ConfigurableEnvironment environment) {
        environment.getPropertySources().addFirst(new MapPropertySource(
                RuntimeStatePropertyDefaults.PROPERTY_SOURCE_NAME,
                RuntimeStatePropertyDefaults.resolveOverrides(environment)
        ));
    }

    @Configuration
    static class NoOpRedisConfigureActionConfig {

        @Bean
        ConfigureRedisAction configureRedisAction() {
            return ConfigureRedisAction.NO_OP;
        }
    }
}
