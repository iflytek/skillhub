package com.iflytek.skillhub.search.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AbstractJpaSearchRebuildServiceCoverageTest {

    private final PostgresSearchRebuildService service = new PostgresSearchRebuildService(
            Mockito.mock(SkillRepository.class),
            Mockito.mock(NamespaceRepository.class),
            Mockito.mock(SkillVersionRepository.class),
            Mockito.mock(LabelDefinitionRepository.class),
            Mockito.mock(LabelTranslationRepository.class),
            Mockito.mock(SkillLabelRepository.class),
            Mockito.mock(SearchIndexService.class),
            new SearchTextTokenizer()
    );

    @Test
    void privateHelpersShouldHandleFallbackBranches() throws Exception {
        assertThat(invoke("extractParsedMetadata", new Class<?>[]{com.iflytek.skillhub.domain.skill.SkillVersion.class},
                versionWithMetadata("{invalid json"))).isEqualTo(Map.of());

        assertThat(invoke("flattenToStrings", new Class<?>[]{Object.class}, new Object[]{null})).isEqualTo(List.of());
        assertThat(invoke("flattenToStrings", new Class<?>[]{Object.class}, 123)).isEqualTo(List.of("123"));
        assertThat(invoke("flattenToStrings", new Class<?>[]{Object.class}, new Object() {
            @Override
            public String toString() {
                return "custom-object";
            }
        })).isEqualTo(List.of("custom-object"));

        List<String> parts = new ArrayList<>();
        invokeVoid("addPart", new Class<?>[]{List.class, String.class}, parts, null);
        assertThat(parts).isEmpty();

        invokeVoid("appendLabelKeywords", new Class<?>[]{Long.class, Set.class}, 1L, new java.util.TreeSet<String>());

        Object result = invoke("resolveLabelSlugs", new Class<?>[]{Long.class}, 1L);
        assertThat(result).isEqualTo(List.of());
    }

    private com.iflytek.skillhub.domain.skill.SkillVersion versionWithMetadata(String json) {
        com.iflytek.skillhub.domain.skill.SkillVersion version =
                new com.iflytek.skillhub.domain.skill.SkillVersion(1L, "1.0.0", "owner");
        version.setParsedMetadataJson(json);
        return version;
    }

    private Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = findMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(service, args);
    }

    private void invokeVoid(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = findMethod(name, parameterTypes);
        method.setAccessible(true);
        method.invoke(service, args);
    }

    private Method findMethod(String name, Class<?>[] parameterTypes) throws Exception {
        Class<?> type = service.getClass();
        while (type != null) {
            try {
                return type.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }
}
