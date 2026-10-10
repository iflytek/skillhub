package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.SlugValidator;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Creates Suite drafts after member coordinates have been resolved to exact Skill versions. */
@Service
public class SkillSuiteDraftService {

    private static final Logger log = LoggerFactory.getLogger(SkillSuiteDraftService.class);
    private static final Pattern PORTABLE_VERSION_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}");

    private final SkillSuiteRepository suiteRepository;
    private final SkillSuiteVersionRepository versionRepository;
    private final SkillSuiteVersionMemberRepository memberRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillSuitePublicationValidator publicationValidator;
    private final AuditLogService auditLogService;

    public SkillSuiteDraftService(
            SkillSuiteRepository suiteRepository,
            SkillSuiteVersionRepository versionRepository,
            SkillSuiteVersionMemberRepository memberRepository,
            NamespaceRepository namespaceRepository,
            SkillSuitePublicationValidator publicationValidator,
            AuditLogService auditLogService
    ) {
        this.suiteRepository = suiteRepository;
        this.versionRepository = versionRepository;
        this.memberRepository = memberRepository;
        this.namespaceRepository = namespaceRepository;
        this.publicationValidator = publicationValidator;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public CreatedDraft create(
            CreateSkillSuiteDraftCommand command,
            SkillSuiteActionContext context
    ) {
        requireWritableNamespace(command.namespaceId());
        assertCanCreate(command.namespaceId(), context);
        SlugValidator.validate(command.slug());
        validateDefinition(command);
        if (suiteRepository.findByNamespaceIdAndSlug(command.namespaceId(), command.slug()).isPresent()) {
            throw new DomainBadRequestException("error.suite.slug.exists", command.slug());
        }

        SkillSuite suite = new SkillSuite(
                command.namespaceId(), command.slug(), command.displayName(), context.actorUserId());
        suite.setSummary(command.summary());
        suite.setUpdatedBy(context.actorUserId());
        suite = suiteRepository.save(suite);

        SkillSuiteVersion version = new SkillSuiteVersion(
                suite.getId(), command.version(), command.displayName(), command.summary(),
                command.visibility(), context.actorUserId());
        version.setOverview(command.overview());
        version.setChangelog(command.changelog());
        version = versionRepository.save(version);

        List<SkillSuiteVersionMember> members = saveMembers(
                version, command.members(), command.entrySkillVersionId());

        // Validate after persistence so the same resolver is used for draft creation and publication.
        // The transaction rolls the draft back if any exact member changed during creation.
        publicationValidator.validate(suite, version);
        auditLogService.record(
                context.actorUserId(), "CREATE_SKILL_SUITE_DRAFT", "SKILL_SUITE_VERSION",
                version.getId(), context.requestId(), context.clientIp(), context.userAgent(),
                AuditDetail.builder()
                        .put("suiteId", suite.getId())
                        .put("memberCount", members.size())
                        .build());
        log.info("Suite draft created [suiteId={}, versionId={}, namespaceId={}, actorId={}, memberCount={}, requestId={}]",
                suite.getId(), version.getId(), suite.getNamespaceId(), context.actorUserId(),
                members.size(), context.requestId());
        return new CreatedDraft(suite, version, members);
    }

    @Transactional
    public CreatedDraft createVersion(
            Long suiteId,
            CreateSkillSuiteDraftCommand command,
            SkillSuiteActionContext context
    ) {
        SkillSuite suite = suiteRepository.findById(suiteId)
                .orElseThrow(() -> new DomainNotFoundException("error.suite.notFound", suiteId));
        Namespace namespace = requireWritableNamespace(suite.getNamespaceId());
        assertMatchesSuite(command, suite);
        assertCanManageSuite(suite, context);
        if (suite.getStatus() != SkillSuiteStatus.ACTIVE) {
            throw new DomainBadRequestException("error.suite.notActive", suite.getSlug());
        }
        if (versionRepository.findBySuiteIdAndVersion(suiteId, command.version()).isPresent()) {
            throw new DomainBadRequestException("error.suite.version.exists", command.version());
        }
        validateDefinition(command);

        CreatedDraft created = persistVersion(suite, command, context);
        log.info("Suite version draft created [suiteId={}, versionId={}, namespaceId={}, actorId={}, memberCount={}, requestId={}]",
                suiteId, created.version().getId(), namespace.getId(), context.actorUserId(),
                created.members().size(), context.requestId());
        return created;
    }

    @Transactional
    public CreatedDraft updateDraft(
            Long suiteId,
            Long versionId,
            CreateSkillSuiteDraftCommand command,
            SkillSuiteActionContext context
    ) {
        SkillSuite suite = suiteRepository.findById(suiteId)
                .orElseThrow(() -> new DomainNotFoundException("error.suite.notFound", suiteId));
        requireWritableNamespace(suite.getNamespaceId());
        SkillSuiteVersion version = versionRepository.findByIdForDefinitionUpdate(versionId)
                .orElseThrow(() -> new DomainNotFoundException("error.suite.version.notFound", versionId));
        if (!suiteId.equals(version.getSuiteId())) {
            throw new DomainBadRequestException("error.suite.version.mismatch");
        }
        assertMatchesSuite(command, suite);
        assertCanManageVersion(suite, version, context);
        version.assertEditable();
        if (!version.getVersion().equals(command.version())) {
            throw new DomainBadRequestException("error.suite.version.renameNotAllowed");
        }
        validateDefinition(command);

        version.setDisplayName(command.displayName());
        version.setSummary(command.summary());
        version.setOverview(command.overview());
        version.setVisibility(command.visibility());
        version.setChangelog(command.changelog());
        versionRepository.save(version);
        memberRepository.deleteBySuiteVersionId(versionId);
        List<SkillSuiteVersionMember> members = saveMembers(
                version, command.members(), command.entrySkillVersionId());
        publicationValidator.validate(suite, version);
        auditLogService.record(
                context.actorUserId(), "UPDATE_SKILL_SUITE_DRAFT", "SKILL_SUITE_VERSION",
                versionId, context.requestId(), context.clientIp(), context.userAgent(),
                AuditDetail.builder()
                        .put("suiteId", suiteId)
                        .put("memberCount", members.size())
                        .build());
        log.info("Suite draft updated [suiteId={}, versionId={}, namespaceId={}, actorId={}, memberCount={}, requestId={}]",
                suiteId, versionId, suite.getNamespaceId(), context.actorUserId(),
                members.size(), context.requestId());
        return new CreatedDraft(suite, version, members);
    }

    private Namespace requireWritableNamespace(Long namespaceId) {
        Namespace namespace = namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", namespaceId));
        if (namespace.getStatus() != NamespaceStatus.ACTIVE) {
            throw new DomainBadRequestException("error.suite.namespace.notWritable", namespace.getStatus());
        }
        return namespace;
    }

    private void assertMatchesSuite(CreateSkillSuiteDraftCommand command, SkillSuite suite) {
        if (!suite.getNamespaceId().equals(command.namespaceId()) || !suite.getSlug().equals(command.slug())) {
            throw new DomainBadRequestException("error.suite.version.mismatch");
        }
    }

    private void assertCanManageSuite(SkillSuite suite, SkillSuiteActionContext context) {
        if (!SkillSuiteAuthorizationPolicy.canCreateVersion(suite, context)) {
            throw new DomainForbiddenException("error.suite.lifecycle.noPermission");
        }
    }

    private void assertCanManageVersion(
            SkillSuite suite,
            SkillSuiteVersion version,
            SkillSuiteActionContext context
    ) {
        if (!SkillSuiteAuthorizationPolicy.canManageVersion(suite, version, context)) {
            throw new DomainForbiddenException("error.suite.lifecycle.noPermission");
        }
    }

    private void validateDefinition(CreateSkillSuiteDraftCommand command) {
        if (command.displayName() == null || command.displayName().isBlank()) {
            throw new DomainBadRequestException("error.suite.displayName.required");
        }
        if (command.version() == null || command.version().isBlank()) {
            throw new DomainBadRequestException("error.suite.version.required");
        }
        if (!PORTABLE_VERSION_PATTERN.matcher(command.version()).matches()) {
            throw new DomainBadRequestException("error.suite.version.invalid");
        }
        SkillSuiteCompositionPolicy.validate(command.members(), command.entrySkillVersionId());
    }

    private CreatedDraft persistVersion(
            SkillSuite suite,
            CreateSkillSuiteDraftCommand command,
            SkillSuiteActionContext context
    ) {
        SkillSuiteVersion version = new SkillSuiteVersion(
                suite.getId(), command.version(), command.displayName(), command.summary(),
                command.visibility(), context.actorUserId());
        version.setOverview(command.overview());
        version.setChangelog(command.changelog());
        version = versionRepository.save(version);
        List<SkillSuiteVersionMember> members = saveMembers(
                version, command.members(), command.entrySkillVersionId());
        publicationValidator.validate(suite, version);
        auditLogService.record(
                context.actorUserId(), "CREATE_SKILL_SUITE_VERSION_DRAFT", "SKILL_SUITE_VERSION",
                version.getId(), context.requestId(), context.clientIp(), context.userAgent(),
                AuditDetail.builder()
                        .put("suiteId", suite.getId())
                        .put("memberCount", members.size())
                        .build());
        return new CreatedDraft(suite, version, members);
    }

    private List<SkillSuiteVersionMember> saveMembers(
            SkillSuiteVersion version,
            List<SkillSuiteMemberSelection> selections,
            Long entrySkillVersionId
    ) {
        List<SkillSuiteVersionMember> members = new ArrayList<>(selections.size());
        for (int position = 0; position < selections.size(); position++) {
            SkillSuiteMemberSelection selection = selections.get(position);
            members.add(new SkillSuiteVersionMember(
                    version.getId(), selection, position,
                    selection.skillVersionId().equals(entrySkillVersionId)));
        }
        return memberRepository.saveAll(members);
    }

    private void assertCanCreate(Long namespaceId, SkillSuiteActionContext context) {
        NamespaceRole role = context.namespaceRoles().get(namespaceId);
        if (!context.platformRoles().contains("SUPER_ADMIN") && role == null) {
            throw new DomainForbiddenException("error.suite.lifecycle.noPermission");
        }
    }

    public record CreatedDraft(
            SkillSuite suite,
            SkillSuiteVersion version,
            List<SkillSuiteVersionMember> members
    ) {
        public CreatedDraft {
            members = List.copyOf(members);
        }
    }
}
