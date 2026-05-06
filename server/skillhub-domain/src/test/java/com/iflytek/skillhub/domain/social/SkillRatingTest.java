package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillRatingTest {

    @Test
    void protectedConstructor_existsForJpa() {
        // Cover the protected no-arg constructor via reflection instantiation
        SkillRating rating = new SkillRating();
        assertThat(rating).isNotNull();
    }

    @Test
    void constructor_validScore_createsRating() {
        SkillRating rating = new SkillRating(1L, "user-1", (short) 4);

        assertThat(rating.getSkillId()).isEqualTo(1L);
        assertThat(rating.getUserId()).isEqualTo("user-1");
        assertThat(rating.getScore()).isEqualTo((short) 4);
    }

    @Test
    void constructor_scoreTooLow_throws() {
        assertThatThrownBy(() -> new SkillRating(1L, "user-1", (short) 0))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.rating.score.invalid");
    }

    @Test
    void constructor_scoreTooHigh_throws() {
        assertThatThrownBy(() -> new SkillRating(1L, "user-1", (short) 6))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.rating.score.invalid");
    }

    @Test
    void updateScore_validScore_updatesScoreAndUpdatedAt() {
        SkillRating rating = new SkillRating(1L, "user-1", (short) 3);

        rating.updateScore((short) 5);

        assertThat(rating.getScore()).isEqualTo((short) 5);
        assertThat(rating.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateScore_tooLow_throws() {
        SkillRating rating = new SkillRating(1L, "user-1", (short) 3);

        assertThatThrownBy(() -> rating.updateScore((short) 0))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.rating.score.invalid");
    }

    @Test
    void updateScore_tooHigh_throws() {
        SkillRating rating = new SkillRating(1L, "user-1", (short) 3);

        assertThatThrownBy(() -> rating.updateScore((short) 6))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.rating.score.invalid");
    }

    @Test
    void prePersist_setsCreatedAtAndUpdatedAt() {
        SkillRating rating = new SkillRating();
        assertThat(rating.getCreatedAt()).isNull();
        assertThat(rating.getUpdatedAt()).isNull();

        rating.prePersist();

        assertThat(rating.getCreatedAt()).isNotNull();
        assertThat(rating.getUpdatedAt()).isEqualTo(rating.getCreatedAt());
    }

    @Test
    void preUpdate_setsUpdatedAt() {
        SkillRating rating = new SkillRating(1L, "user-1", (short) 3);
        rating.prePersist();

        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        rating.preUpdate();

        assertThat(rating.getUpdatedAt()).isAfterOrEqualTo(rating.getCreatedAt());
    }

    @Test
    void getters_returnExpectedValues() {
        SkillRating rating = new SkillRating(42L, "user-99", (short) 2);

        assertThat(rating.getId()).isNull();
        assertThat(rating.getSkillId()).isEqualTo(42L);
        assertThat(rating.getUserId()).isEqualTo("user-99");
        assertThat(rating.getScore()).isEqualTo((short) 2);
        assertThat(rating.getCreatedAt()).isNull();
        assertThat(rating.getUpdatedAt()).isNull();
    }
}
