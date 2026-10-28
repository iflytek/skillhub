package com.iflytek.skillhub.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SkillWebUrlsTest {
    @Test
    void buildsEncodedSegmentsWithConfiguredDeploymentPrefix() {
        var urls = new SkillWebUrls(" https://skills.example/team/ ");
        assertThat(urls.detail("团队 /", "a+b#?%"))
                .isEqualTo("https://skills.example/team/space/%E5%9B%A2%E9%98%9F%20%2F/a%2Bb%23%3F%25");
    }

    @Test
    void absentOrUnsafeConfigurationNeverInventsAPublicAddress() {
        for (String base : new String[] {null, "", "  ", "//evil.example", "javascript:alert(1)",
                "https://user:password@example.com", "https://example.com?q=1", "https://example.com#x",
                "https://example.com\\evil", "https://example.com:99999", "https://example.com/\npath"}) {
            assertThat(new SkillWebUrls(base).detail("team", "demo")).isNull();
        }
    }
}
