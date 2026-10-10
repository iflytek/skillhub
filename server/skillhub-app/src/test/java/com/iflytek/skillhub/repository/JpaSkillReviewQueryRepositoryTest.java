package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.social.SkillRating;
import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class JpaSkillReviewQueryRepositoryTest {

    @Mock private SkillRatingRepository ratingRepository;
    @Mock private UserAccountRepository userAccountRepository;

    @Test
    void assemblesAuthorProfileWithoutPerRowUserQueries() {
        SkillRating rating = new SkillRating(10L, "author-1", (short) 5);
        rating.updateReview((short) 5, "excellent");
        UserAccount author = new UserAccount("author-1", "Alice", null, "avatar.png");
        PageRequest page = PageRequest.of(0, 20);
        when(ratingRepository.findVisibleReviewsBySkillId(10L, page))
                .thenReturn(new PageImpl<>(List.of(rating), page, 1));
        when(userAccountRepository.findByIdIn(List.of("author-1"))).thenReturn(List.of(author));

        var result = new JpaSkillReviewQueryRepository(ratingRepository, userAccountRepository)
                .list(10L, "author-1", false, page);

        assertThat(result.getContent()).singleElement().satisfies(review -> {
            assertThat(review.displayName()).isEqualTo("Alice");
            assertThat(review.avatarUrl()).isEqualTo("avatar.png");
            assertThat(review.reviewText()).isEqualTo("excellent");
            assertThat(review.authoredByViewer()).isTrue();
            assertThat(review.status()).isEqualTo("VISIBLE");
            assertThat(review.userId()).isNull();
            assertThat(review.moderationReason()).isNull();
        });
        verify(userAccountRepository).findByIdIn(List.of("author-1"));
    }

    @Test
    void administratorListingIncludesModerationDetails() {
        SkillRating rating = new SkillRating(10L, "author-1", (short) 2);
        rating.updateReview((short) 2, "off topic");
        rating.hideReview("admin", "Policy violation");
        PageRequest page = PageRequest.of(0, 20);
        when(ratingRepository.findReviewsBySkillId(10L, page))
                .thenReturn(new PageImpl<>(List.of(rating), page, 1));
        when(userAccountRepository.findByIdIn(List.of("author-1"))).thenReturn(List.of());

        var result = new JpaSkillReviewQueryRepository(ratingRepository, userAccountRepository)
                .list(10L, "admin", true, page);

        assertThat(result.getContent()).singleElement().satisfies(review -> {
            assertThat(review.userId()).isEqualTo("author-1");
            assertThat(review.moderationReason()).isEqualTo("Policy violation");
            assertThat(review.status()).isEqualTo("HIDDEN");
        });
    }
}
