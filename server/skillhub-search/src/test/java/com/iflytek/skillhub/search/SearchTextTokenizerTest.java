package com.iflytek.skillhub.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTextTokenizerTest {

    private final SearchTextTokenizer tokenizer = new SearchTextTokenizer();

    @Test
    void enrichForIndexShouldKeepRawPhraseAndAddSegmentedTokens() {
        String enriched = tokenizer.enrichForIndex("中文技能搜索");

        assertThat(enriched).contains("中文技能搜索");
        assertThat(tokenizer.tokenizeForIndex("中文技能搜索")).contains("中文", "技能", "搜索");
    }

    @Test
    void tokenizeForQueryShouldNormalizeAsciiTerms() {
        assertThat(tokenizer.tokenizeForQuery("OpenAI Agent"))
                .containsExactly("openai", "agent");
    }
}
