package com.iflytek.skillhub.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.user.ProfileChangeRequest;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.ProfileReviewSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class JpaProfileReviewQueryRepository implements ProfileReviewQueryRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final UserAccountRepository userAccountRepository;

    public JpaProfileReviewQueryRepository(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public List<ProfileReviewSummaryResponse> getProfileReviewSummaries(List<ProfileChangeRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        var allUserIds = requests.stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getUserId(), r.getReviewerId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<String, UserAccount> usersById = allUserIds.isEmpty()
                ? Map.of()
                : userAccountRepository.findByIdIn(allUserIds).stream()
                        .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        return requests.stream()
                .map(request -> toResponse(request, usersById))
                .toList();
    }

    private ProfileReviewSummaryResponse toResponse(ProfileChangeRequest request,
                                                    Map<String, UserAccount> usersById) {
        UserAccount submitter = usersById.get(request.getUserId());
        UserAccount reviewer = request.getReviewerId() != null ? usersById.get(request.getReviewerId()) : null;
        Map<String, String> changes = parseChangesJson(request.getChanges());
        Map<String, String> oldValues = parseChangesJson(request.getOldValues());
        String currentDisplayName = oldValues.getOrDefault(
                "displayName",
                submitter != null ? submitter.getDisplayName() : null
        );

        return new ProfileReviewSummaryResponse(
                request.getId(),
                request.getUserId(),
                submitter != null ? submitter.getDisplayName() : request.getUserId(),
                currentDisplayName,
                changes.getOrDefault("displayName", null),
                request.getStatus().name(),
                request.getMachineResult(),
                request.getReviewerId(),
                reviewer != null ? reviewer.getDisplayName() : null,
                request.getReviewComment(),
                request.getCreatedAt(),
                request.getReviewedAt()
        );
    }

    private Map<String, String> parseChangesJson(String json) {
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
