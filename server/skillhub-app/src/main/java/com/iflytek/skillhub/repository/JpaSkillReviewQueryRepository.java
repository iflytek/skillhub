package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.social.SkillRating;
import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.SkillReviewResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class JpaSkillReviewQueryRepository implements SkillReviewQueryRepository {

    private final SkillRatingRepository ratingRepository;
    private final UserAccountRepository userAccountRepository;

    public JpaSkillReviewQueryRepository(SkillRatingRepository ratingRepository,
                                         UserAccountRepository userAccountRepository) {
        this.ratingRepository = ratingRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public Page<SkillReviewResponse> list(Long skillId,
                                          String viewerId,
                                          boolean includeHidden,
                                          Pageable pageable) {
        Page<SkillRating> reviews = includeHidden
                ? ratingRepository.findReviewsBySkillId(skillId, pageable)
                : ratingRepository.findVisibleReviewsBySkillId(skillId, pageable);
        List<String> authorIds = reviews.getContent().stream()
                .map(SkillRating::getUserId)
                .distinct()
                .toList();
        Map<String, UserAccount> authors = userAccountRepository.findByIdIn(authorIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        return reviews.map(review -> toResponse(
                review,
                authors.get(review.getUserId()),
                viewerId,
                includeHidden
        ));
    }

    private SkillReviewResponse toResponse(SkillRating review,
                                           UserAccount author,
                                           String viewerId,
                                           boolean includeModerationDetails) {
        return new SkillReviewResponse(
                review.getId(),
                includeModerationDetails ? review.getUserId() : null,
                author != null ? author.getDisplayName() : review.getUserId(),
                author != null ? author.getAvatarUrl() : null,
                review.getScore(),
                review.getReviewText(),
                review.getReviewStatus().name(),
                review.getUserId().equals(viewerId),
                includeModerationDetails ? review.getModerationReason() : null,
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
