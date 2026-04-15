package com.iflytek.skillhub.dto.collection;

import com.iflytek.skillhub.domain.collection.SkillCollection;
import java.time.Instant;
import java.util.List;

public record SkillCollectionResponse(
        Long id,
        String ownerId,
        String slug,
        String title,
        String description,
        String visibility,
        List<SkillCollectionMemberResponse> members,
        int additionalMembersHiddenFromActorCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static SkillCollectionResponse from(SkillCollection collection) {
        return from(collection, List.of());
    }

    public static SkillCollectionResponse from(SkillCollection collection, List<SkillCollectionMemberResponse> members) {
        return from(collection, members, 0);
    }

    public static SkillCollectionResponse from(
            SkillCollection collection,
            List<SkillCollectionMemberResponse> members,
            int additionalMembersHiddenFromActorCount
    ) {
        return new SkillCollectionResponse(
                collection.getId(),
                collection.getOwnerId(),
                collection.getSlug(),
                collection.getTitle(),
                collection.getDescription(),
                collection.getVisibility().name(),
                members,
                additionalMembersHiddenFromActorCount,
                collection.getCreatedAt(),
                collection.getUpdatedAt()
        );
    }
}
