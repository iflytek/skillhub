package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SitePresentationLinksTest {
    @ParameterizedTest
    @ValueSource(strings = {"/../docs", "/./docs", "/guide/../docs", "/guide/./docs", "/guide/..", "/guide/.",
            "/%2e%2e/docs", "/%2E/docs", "/%252e%252e/docs", "/guide/%252E/docs", "/%2e./docs",
            "/.%2e/docs", "/guide%2f..%2fdocs", "/guide/%252f../docs"})
    void rejectsRootRelativeDotSegmentsBeforeBrowserBasePathResolution(String url) {
        assertThat(SitePresentationLinks.safeUrl(url)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/docs/.hidden", "/docs/file..name", "/docs?return=/../dashboard", "/docs#../anchor",
            "https://docs.example.test/../guide", "http://docs.example.test/a/./guide", "/docs/file%20name", "/docs/100%25"})
    void keepsNonDotNamesQueryDataAndAbsoluteHttpNavigationValid(String url) {
        assertThat(SitePresentationLinks.safeUrl(url)).isEqualTo(url);
    }
}
