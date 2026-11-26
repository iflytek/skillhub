package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.SkillSuiteMemberResponse;
import com.iflytek.skillhub.dto.SkillSuiteResponse;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SuiteExplanationServiceTest {
    final ObjectMapper json = new ObjectMapper();

    private SkillSuiteResponse snapshot(List<SkillSuiteMemberResponse> members) {
        return new SkillSuiteResponse(1L, 5L, "global", "suite", "Suite", "", "", "", "owner", null,
                null, null, null, "1.0", "PUBLISHED", com.iflytek.skillhub.domain.skill.SkillVisibility.PUBLIC,
                "ACTIVE", false, Set.of(), true, true, members);
    }

    @Test
    void sendsOnlyPinnedPublicSourcesAndMapsCitationsBackToTheRegistry() throws Exception {
        var prompt = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            prompt.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String answer = """
                    {"statements":[{"text":"One readable skill.","sources":[0]},
                    {"text":"Start with entry.","sources":[0]},{"text":"The entry asks for review.","sources":[1]}]}
                    """;
            byte[] body = json.writeValueAsBytes(Map.of("choices", List.of(Map.of("message", Map.of("content", answer)))));
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var suites = mock(SkillSuiteAppService.class);
            var files = mock(SkillQueryService.class);
            var entry = new SkillSuiteMemberResponse(1L, 2L, "global", "entry", null, null, "1.1", null, 0, true, true, null);
            var restricted = new SkillSuiteMemberResponse(3L, 4L, "team", "secret", null, null, "9", null, 1, false, false, null);
            var snapshot = snapshot(List.of(entry, restricted));
            when(suites.getDetail("global", "suite", "1.0", "viewer", Map.of(), Set.of())).thenReturn(snapshot);
            when(suites.getDetail("global", "suite", "1.0", null, Map.of(), Set.of())).thenReturn(snapshot);
            when(files.getFileContent("global", "entry", "1.1", "SKILL.md", null, Map.of()))
                    .thenReturn(new ByteArrayInputStream("Ask for review.".getBytes(StandardCharsets.UTF_8)));
            var service = new SuiteExplanationService(suites, files, json,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/chat", "fixture-model", "", false);
            var answer = service.explain("global", "suite", "1.0", "viewer", Map.of(), Set.of(), Locale.ENGLISH);
            assertThat(answer.statements()).hasSize(3);
            assertThat(answer.statements().get(2).sources()).containsExactly(
                    new SuiteExplanationService.Source("global", "entry", "1.1", false));
            assertThat(prompt.get()).contains("Ask for review.", "@global/entry@1.1").doesNotContain("secret");
            verify(files).getFileContent("global", "entry", "1.1", "SKILL.md", null, Map.of());
            verifyNoMoreInteractions(files);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void publicOnlyPolicyRejectsAViewerReadablePrivateSuiteBeforeReadingFiles() {
        var suites = mock(SkillSuiteAppService.class);
        var files = mock(SkillQueryService.class);
        var viewerSnapshot = snapshot(List.of());
        when(suites.getDetail("team", "internal", "1", "reader", Map.of(), Set.of())).thenReturn(viewerSnapshot);
        var denied = new com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException("error.suite.version.notFound", "1");
        when(suites.getDetail("team", "internal", "1", null, Map.of(), Set.of())).thenThrow(denied);
        var service = new SuiteExplanationService(suites, files, json, "http://127.0.0.1", "fixture", "", false);
        assertThatThrownBy(() -> service.explain("team", "internal", "1", "reader", Map.of(), Set.of(), Locale.ENGLISH))
                .isSameAs(denied);
        verifyNoInteractions(files);
    }

    @Test
    void rejectsProviderInventedReferences() throws Exception {
        var invalid = json.readTree("""
                {"statements":[{"text":"Claim","sources":[99]},{},{}]}
                """);
        assertThatIOException().isThrownBy(() -> SuiteExplanationService.parse(invalid, List.of()));
    }
}
