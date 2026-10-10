package com.iflytek.skillhub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.List;

class BuiltinSkillManifestLoaderTest {

    private static final String SHA256_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SHA256_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void loadsManifestItemsInOrder() {
        BuiltinSkillManifestLoader loader = loaderWith("""
                {
                  "skills": [
                    {
                      "slug": "skillhub-hello",
                      "version": "1.0.0",
                      "url": "https://bjcdn.openstorage.cn/skillhub-hello.zip",
                      "sha256": "%s"
                    },
                    {
                      "slug": "skillhub-hello",
                      "version": "1.1.0",
                      "url": "https://cdn.bjcdn.openstorage.cn/skillhub-hello.zip",
                      "sha256": "%s"
                    }
                  ]
                }
                """.formatted(SHA256_A, SHA256_B));

        List<BuiltinSkillManifestLoader.ManifestItem> items = loader.load();

        assertThat(items)
                .extracting(BuiltinSkillManifestLoader.ManifestItem::version)
                .containsExactly("1.0.0", "1.1.0");
        assertThat(items)
                .extracting(BuiltinSkillManifestLoader.ManifestItem::sha256)
                .containsExactly(SHA256_A, SHA256_B);
    }

    @Test
    void loadsBundledManifestFromClasspath() {
        BuiltinSkillManifestLoader loader = new BuiltinSkillManifestLoader(
                new ObjectMapper(),
                new DefaultResourceLoader()
        );

        List<BuiltinSkillManifestLoader.ManifestItem> items = loader.load();

        assertThat(items).isNotEmpty().allSatisfy(item ->
                assertThat(item.sha256()).matches("[0-9a-f]{64}")
        );
        assertThat(items).anySatisfy(item -> {
            assertThat(item.slug()).isEqualTo("plugin-scanner");
            assertThat(item.version()).isEqualTo("1.0.0");
        });
    }

    @Test
    void returnsEmptyListWhenManifestIsMissing() {
        BuiltinSkillManifestLoader loader = new BuiltinSkillManifestLoader(
                new ObjectMapper(),
                new ResourceLoader() {
                    @Override
                    public org.springframework.core.io.Resource getResource(String location) {
                        return new MissingResource();
                    }

                    @Override
                    public ClassLoader getClassLoader() {
                        return getClass().getClassLoader();
                    }
                }
        );

        assertThat(loader.load()).isEmpty();
    }

    @Test
    void returnsEmptyListWhenManifestIsMalformed() {
        BuiltinSkillManifestLoader loader = loaderWith("{not-json");

        assertThat(loader.load()).isEmpty();
    }

    @Test
    void returnsEmptyListWhenManifestIsEmpty() {
        BuiltinSkillManifestLoader loader = loaderWith("");

        assertThat(loader.load()).isEmpty();
    }

    @Test
    void skipsItemsWithMissingRequiredFieldsAndDuplicateSlugVersion() {
        BuiltinSkillManifestLoader loader = loaderWith("""
                {
                  "skills": [
                    {
                      "slug": "skillhub-hello",
                      "version": "1.0.0",
                      "url": "https://bjcdn.openstorage.cn/first.zip",
                      "sha256": "%s"
                    },
                    {
                      "slug": "skillhub-hello",
                      "version": "1.0.0",
                      "url": "https://bjcdn.openstorage.cn/second.zip",
                      "sha256": "%s"
                    },
                    {
                      "slug": "InvalidUppercase",
                      "version": "1.0.0",
                      "url": "https://bjcdn.openstorage.cn/invalid.zip",
                      "sha256": "%s"
                    },
                    {
                      "slug": "",
                      "version": "1.0.0",
                      "url": "https://bjcdn.openstorage.cn/blank.zip",
                      "sha256": "%s"
                    },
                    {
                      "slug": "missing-version",
                      "url": "https://bjcdn.openstorage.cn/missing-version.zip",
                      "sha256": "%s"
                    },
                    {
                      "slug": "missing-url",
                      "version": "1.0.0",
                      "sha256": "%s"
                    },
                    {
                      "slug": "valid-after-invalid",
                      "version": "1.0.0",
                      "url": "https://bjcdn.openstorage.cn/valid.zip",
                      "sha256": "%s"
                    }
                  ]
                }
                """.formatted(
                        SHA256_A,
                        SHA256_B,
                        SHA256_A,
                        SHA256_A,
                        SHA256_A,
                        SHA256_A,
                        SHA256_B
                ));

        List<BuiltinSkillManifestLoader.ManifestItem> items = loader.load();

        assertThat(items)
                .extracting(BuiltinSkillManifestLoader.ManifestItem::url)
                .containsExactly(
                        "https://bjcdn.openstorage.cn/first.zip",
                        "https://bjcdn.openstorage.cn/valid.zip"
                );
    }

    @Test
    void skipsItemsWithMissingOrInvalidSha256() {
        BuiltinSkillManifestLoader loader = loaderWith("""
                {
                  "skills": [
                    {
                      "slug": "missing-sha",
                      "version": "1.0.0",
                      "url": "https://bjcdn.openstorage.cn/missing.zip"
                    },
                    {
                      "slug": "short-sha",
                      "version": "1.0.0",
                      "url": "https://bjcdn.openstorage.cn/short.zip",
                      "sha256": "abc123"
                    },
                    {
                      "slug": "uppercase-sha",
                      "version": "1.0.0",
                      "url": "https://bjcdn.openstorage.cn/uppercase.zip",
                      "sha256": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                    },
                    {
                      "slug": "valid-sha",
                      "version": "1.0.0",
                      "url": "https://bjcdn.openstorage.cn/valid.zip",
                      "sha256": "%s"
                    }
                  ]
                }
                """.formatted(SHA256_A));

        List<BuiltinSkillManifestLoader.ManifestItem> items = loader.load();

        assertThat(items)
                .extracting(BuiltinSkillManifestLoader.ManifestItem::slug)
                .containsExactly("valid-sha");
    }

    @Test
    void capsManifestEntriesAtOneHundredRawEntries() {
        StringBuilder json = new StringBuilder("{\"skills\":[");
        for (int i = 0; i < 101; i++) {
            if (i > 0) {
                json.append(',');
            }
            if (i == 0) {
                json.append("{\"slug\":\"\",\"version\":\"1.0.0\",\"url\":\"https://bjcdn.openstorage.cn/blank.zip\",")
                        .append("\"sha256\":\"").append(SHA256_A).append("\"}");
            } else {
                json.append("{\"slug\":\"skill-").append(i)
                        .append("\",\"version\":\"1.0.0\",\"url\":\"https://bjcdn.openstorage.cn/skill-")
                        .append(i)
                        .append(".zip\",\"sha256\":\"").append(SHA256_A).append("\"}");
            }
        }
        json.append("]}");

        BuiltinSkillManifestLoader loader = loaderWith(json.toString());

        assertThat(loader.load()).hasSize(99);
    }

    private BuiltinSkillManifestLoader loaderWith(String content) {
        ResourceLoader resourceLoader = new ResourceLoader() {
            @Override
            public org.springframework.core.io.Resource getResource(String location) {
                return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public boolean exists() {
                        return true;
                    }

                    @Override
                    public String getDescription() {
                        return "test manifest";
                    }
                };
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };
        return new BuiltinSkillManifestLoader(new ObjectMapper(), resourceLoader);
    }

    static class MissingResource extends ByteArrayResource {

        MissingResource() {
            super(new byte[0]);
        }

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public String getDescription() {
            return "missing manifest";
        }
    }
}
