package com.iflytek.skillhub.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HashingSearchEmbeddingServiceTest {

    private final HashingSearchEmbeddingService service = new HashingSearchEmbeddingService();

    @Test
    void embedShouldBeDeterministic() {
        String first = service.embed("self improving skill");
        String second = service.embed("self improving skill");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void similarityShouldFavorCloserText() {
        String relevantVector = service.embed("self improvement productivity habit tracker");
        String noisyVector = service.embed("web search keywords company research");

        double relevant = service.similarity("self improvement", relevantVector);
        double noisy = service.similarity("self improvement", noisyVector);

        assertThat(relevant).isGreaterThan(noisy);
    }

    @Test
    void similarityShouldHandleSingularAndPluralForms() {
        String pluralVector = service.embed("build strong habits with daily practice");
        String unrelatedVector = service.embed("research company profiles on the web");

        double pluralMatch = service.similarity("habit", pluralVector);
        double unrelatedMatch = service.similarity("habit", unrelatedVector);

        assertThat(pluralMatch).isGreaterThan(unrelatedMatch);
    }

    @Test
    void similarityWithNullOrBlankVectorReturnsZero() {
        assertThat(service.similarity("test", null)).isEqualTo(0D);
        assertThat(service.similarity("test", "   ")).isEqualTo(0D);
        assertThat(service.similarity("test", "")).isEqualTo(0D);
    }

    @Test
    void similarityWithMismatchedVectorLengthsReturnsZero() {
        assertThat(service.similarity("test", "0.1,0.2")).isEqualTo(0D);
    }

    @Test
    void embedWithNullOrBlankTextReturnsZeroVector() {
        String nullEmbed = service.embed(null);
        String blankEmbed = service.embed("   ");
        assertThat(nullEmbed).isEqualTo(blankEmbed);
        assertThat(service.similarity("anything", nullEmbed)).isEqualTo(0D);
    }
}
