package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.report.SkillReport;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.GovernanceInboxItemResponse;
import com.iflytek.skillhub.dto.PromotionResponseDto;
import com.iflytek.skillhub.dto.ReviewTaskResponse;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class JpaGovernanceQueryRepository implements GovernanceQueryRepository {

    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final NamespaceRepository namespaceRepository;
    private final UserAccountRepository userAccountRepository;

    public JpaGovernanceQueryRepository(SkillRepository skillRepository,
                                        SkillVersionRepository skillVersionRepository,
                                        NamespaceRepository namespaceRepository,
                                        UserAccountRepository userAccountRepository) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.namespaceRepository = namespaceRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public ReviewTaskResponse getReviewTaskResponse(ReviewTask task) {
        return getReviewTaskResponses(List.of(task)).get(0);
    }

    @Override
    public List<ReviewTaskResponse> getReviewTaskResponses(List<ReviewTask> tasks) {
        ReviewReadBundle bundle = loadReviewBundle(tasks);
        return tasks.stream().map(task -> toReviewTaskResponse(task, bundle)).toList();
    }

    @Override
    public PromotionResponseDto getPromotionResponse(PromotionRequest request) {
        return getPromotionResponses(List.of(request)).get(0);
    }

    @Override
    public List<PromotionResponseDto> getPromotionResponses(List<PromotionRequest> requests) {
        PromotionReadBundle bundle = loadPromotionBundle(requests);
        return requests.stream().map(request -> toPromotionResponse(request, bundle)).toList();
    }

    @Override
    public GovernanceInboxItemResponse getReviewInboxItem(ReviewTask task) {
        return getReviewInboxItems(List.of(task)).get(0);
    }

    @Override
    public List<GovernanceInboxItemResponse> getReviewInboxItems(List<ReviewTask> tasks) {
        ReviewReadBundle bundle = loadReviewBundle(tasks);
        return tasks.stream().map(task -> toReviewInboxItem(task, bundle)).toList();
    }

    @Override
    public GovernanceInboxItemResponse getPromotionInboxItem(PromotionRequest request) {
        return getPromotionInboxItems(List.of(request)).get(0);
    }

    @Override
    public List<GovernanceInboxItemResponse> getPromotionInboxItems(List<PromotionRequest> requests) {
        PromotionReadBundle bundle = loadPromotionBundle(requests);
        return requests.stream().map(request -> toPromotionInboxItem(request, bundle)).toList();
    }

    @Override
    public GovernanceInboxItemResponse getReportInboxItem(SkillReport report) {
        return getReportInboxItems(List.of(report)).get(0);
    }

    @Override
    public List<GovernanceInboxItemResponse> getReportInboxItems(List<SkillReport> reports) {
        ReportReadBundle bundle = loadReportBundle(reports);
        return reports.stream().map(report -> toReportInboxItem(report, bundle)).toList();
    }

    private ReviewReadBundle loadReviewBundle(List<ReviewTask> tasks) {
        List<Long> versionIds = distinct(tasks.stream().map(ReviewTask::getSkillVersionId).toList());
        Map<Long, SkillVersion> versionsById = versionIds.isEmpty()
                ? Map.of()
                : skillVersionRepository.findByIdIn(versionIds).stream()
                .collect(Collectors.toMap(SkillVersion::getId, Function.identity()));
        List<Long> skillIds = distinct(versionsById.values().stream().map(SkillVersion::getSkillId).toList());
        Map<Long, Skill> skillsById = skillIds.isEmpty()
                ? Map.of()
                : skillRepository.findByIdIn(skillIds).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));
        List<Long> namespaceIds = distinct(skillsById.values().stream().map(Skill::getNamespaceId).toList());
        Map<Long, Namespace> namespacesById = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        List<String> userIds = distinctStrings(tasks.stream()
                .flatMap(task -> java.util.stream.Stream.of(task.getSubmittedBy(), task.getReviewedBy()))
                .filter(Objects::nonNull)
                .toList());
        Map<String, UserAccount> usersById = userIds.isEmpty()
                ? Map.of()
                : userAccountRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        return new ReviewReadBundle(versionsById, skillsById, namespacesById, usersById);
    }

    private PromotionReadBundle loadPromotionBundle(List<PromotionRequest> requests) {
        List<Long> sourceSkillIds = distinct(requests.stream().map(PromotionRequest::getSourceSkillId).toList());
        Map<Long, Skill> skillsById = sourceSkillIds.isEmpty()
                ? Map.of()
                : skillRepository.findByIdIn(sourceSkillIds).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));
        List<Long> sourceVersionIds = distinct(requests.stream().map(PromotionRequest::getSourceVersionId).toList());
        Map<Long, SkillVersion> versionsById = sourceVersionIds.isEmpty()
                ? Map.of()
                : skillVersionRepository.findByIdIn(sourceVersionIds).stream()
                .collect(Collectors.toMap(SkillVersion::getId, Function.identity()));
        Set<Long> namespaceIds = new LinkedHashSet<>(distinct(requests.stream().map(PromotionRequest::getTargetNamespaceId).toList()));
        namespaceIds.addAll(skillsById.values().stream().map(Skill::getNamespaceId).toList());
        Map<Long, Namespace> namespacesById = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(List.copyOf(namespaceIds)).stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        List<String> userIds = distinctStrings(requests.stream()
                .flatMap(request -> java.util.stream.Stream.of(request.getSubmittedBy(), request.getReviewedBy()))
                .filter(Objects::nonNull)
                .toList());
        Map<String, UserAccount> usersById = userIds.isEmpty()
                ? Map.of()
                : userAccountRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        return new PromotionReadBundle(skillsById, versionsById, namespacesById, usersById);
    }

    private ReportReadBundle loadReportBundle(List<SkillReport> reports) {
        List<Long> skillIds = distinct(reports.stream().map(SkillReport::getSkillId).toList());
        Map<Long, Skill> skillsById = skillIds.isEmpty()
                ? Map.of()
                : skillRepository.findByIdIn(skillIds).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));
        List<Long> namespaceIds = distinct(reports.stream().map(SkillReport::getNamespaceId).toList());
        Map<Long, Namespace> namespacesById = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        return new ReportReadBundle(skillsById, namespacesById);
    }

    private ReviewTaskResponse toReviewTaskResponse(ReviewTask task, ReviewReadBundle bundle) {
        SkillVersion version = require(bundle.versionsById(), task.getSkillVersionId(), "skill_version.not_found");
        Skill skill = require(bundle.skillsById(), version.getSkillId(), "skill.not_found");
        Namespace namespace = require(bundle.namespacesById(), skill.getNamespaceId(), "namespace.not_found");
        UserAccount submittedBy = bundle.usersById().get(task.getSubmittedBy());
        UserAccount reviewedBy = task.getReviewedBy() != null ? bundle.usersById().get(task.getReviewedBy()) : null;
        return new ReviewTaskResponse(
                task.getId(),
                task.getSkillVersionId(),
                namespace.getSlug(),
                skill.getSlug(),
                version.getVersion(),
                task.getStatus().name(),
                task.getSubmittedBy(),
                submittedBy != null ? submittedBy.getDisplayName() : null,
                task.getReviewedBy(),
                reviewedBy != null ? reviewedBy.getDisplayName() : null,
                task.getReviewComment(),
                task.getSubmittedAt(),
                task.getReviewedAt()
        );
    }

    private PromotionResponseDto toPromotionResponse(PromotionRequest request, PromotionReadBundle bundle) {
        Skill skill = require(bundle.skillsById(), request.getSourceSkillId(), "skill.not_found");
        SkillVersion version = require(bundle.versionsById(), request.getSourceVersionId(), "skill_version.not_found");
        Namespace sourceNamespace = require(bundle.namespacesById(), skill.getNamespaceId(), "namespace.not_found");
        Namespace targetNamespace = require(bundle.namespacesById(), request.getTargetNamespaceId(), "namespace.not_found");
        UserAccount submittedBy = bundle.usersById().get(request.getSubmittedBy());
        UserAccount reviewedBy = request.getReviewedBy() != null ? bundle.usersById().get(request.getReviewedBy()) : null;
        return new PromotionResponseDto(
                request.getId(),
                request.getSourceSkillId(),
                sourceNamespace.getSlug(),
                skill.getSlug(),
                version.getVersion(),
                targetNamespace.getSlug(),
                request.getTargetSkillId(),
                request.getStatus().name(),
                request.getSubmittedBy(),
                submittedBy != null ? submittedBy.getDisplayName() : null,
                request.getReviewedBy(),
                reviewedBy != null ? reviewedBy.getDisplayName() : null,
                request.getReviewComment(),
                request.getSubmittedAt(),
                request.getReviewedAt()
        );
    }

    private GovernanceInboxItemResponse toReviewInboxItem(ReviewTask task, ReviewReadBundle bundle) {
        SkillVersion version = bundle.versionsById().get(task.getSkillVersionId());
        Skill skill = version != null ? bundle.skillsById().get(version.getSkillId()) : null;
        Namespace namespace = skill != null ? bundle.namespacesById().get(skill.getNamespaceId()) : null;
        String namespaceSlug = namespace != null ? namespace.getSlug() : null;
        String skillSlug = skill != null ? skill.getSlug() : null;
        String versionName = version != null ? version.getVersion() : null;
        return new GovernanceInboxItemResponse(
                "REVIEW",
                task.getId(),
                join(namespaceSlug, skillSlug, versionName),
                "Pending review",
                task.getSubmittedAt() != null ? task.getSubmittedAt().toString() : null,
                namespaceSlug,
                skillSlug
        );
    }

    private GovernanceInboxItemResponse toPromotionInboxItem(PromotionRequest request, PromotionReadBundle bundle) {
        Skill skill = bundle.skillsById().get(request.getSourceSkillId());
        SkillVersion version = bundle.versionsById().get(request.getSourceVersionId());
        Namespace sourceNamespace = skill != null ? bundle.namespacesById().get(skill.getNamespaceId()) : null;
        Namespace targetNamespace = bundle.namespacesById().get(request.getTargetNamespaceId());
        String sourceSlug = sourceNamespace != null ? sourceNamespace.getSlug() : null;
        String skillSlug = skill != null ? skill.getSlug() : null;
        String targetSlug = targetNamespace != null ? targetNamespace.getSlug() : null;
        String versionName = version != null ? version.getVersion() : null;
        return new GovernanceInboxItemResponse(
                "PROMOTION",
                request.getId(),
                join(sourceSlug, skillSlug, versionName),
                targetSlug != null ? "Promote to @" + targetSlug : "Pending promotion",
                request.getSubmittedAt() != null ? request.getSubmittedAt().toString() : null,
                sourceSlug,
                skillSlug
        );
    }

    private GovernanceInboxItemResponse toReportInboxItem(SkillReport report, ReportReadBundle bundle) {
        Skill skill = bundle.skillsById().get(report.getSkillId());
        Namespace namespace = bundle.namespacesById().get(report.getNamespaceId());
        String namespaceSlug = namespace != null ? namespace.getSlug() : null;
        String skillSlug = skill != null ? skill.getSlug() : null;
        return new GovernanceInboxItemResponse(
                "REPORT",
                report.getId(),
                join(namespaceSlug, skillSlug, null),
                report.getReason(),
                report.getCreatedAt() != null ? report.getCreatedAt().toString() : null,
                namespaceSlug,
                skillSlug
        );
    }

    private <K, V> V require(Map<K, V> values, K key, String code) {
        V value = values.get(key);
        if (value == null) {
            throw new DomainNotFoundException(code, key);
        }
        return value;
    }

    private List<Long> distinct(Collection<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private List<String> distinctStrings(Collection<String> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String join(String namespaceSlug, String skillSlug, String version) {
        String path = namespaceSlug != null && skillSlug != null ? namespaceSlug + "/" + skillSlug : "Unknown target";
        return version != null ? path + "@" + version : path;
    }

    private record ReviewReadBundle(Map<Long, SkillVersion> versionsById,
                                    Map<Long, Skill> skillsById,
                                    Map<Long, Namespace> namespacesById,
                                    Map<String, UserAccount> usersById) {
    }

    private record PromotionReadBundle(Map<Long, Skill> skillsById,
                                       Map<Long, SkillVersion> versionsById,
                                       Map<Long, Namespace> namespacesById,
                                       Map<String, UserAccount> usersById) {
    }

    private record ReportReadBundle(Map<Long, Skill> skillsById,
                                    Map<Long, Namespace> namespacesById) {
    }
}
