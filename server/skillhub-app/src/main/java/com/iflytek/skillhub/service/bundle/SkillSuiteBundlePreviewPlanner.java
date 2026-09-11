package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteActionContext;
import com.iflytek.skillhub.domain.suite.SkillSuiteAuthorizationPolicy;
import com.iflytek.skillhub.domain.suite.SkillSuiteRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteStatus;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionMember;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionMemberRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifest;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMember;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds a side-effect-free, batch-loaded publication plan from validated Bundle packages. */
@Service
public class SkillSuiteBundlePreviewPlanner {

    private static final DateTimeFormatter AUTO_VERSION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss").withZone(ZoneId.systemDefault());

    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final SkillSuiteRepository suiteRepository;
    private final SkillSuiteVersionRepository suiteVersionRepository;
    private final SkillSuiteVersionMemberRepository suiteMemberRepository;
    private final VisibilityChecker visibilityChecker;
    private final SecurityScanService securityScanService;
    private final Clock clock;

    public SkillSuiteBundlePreviewPlanner(
            NamespaceRepository namespaceRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillFileRepository skillFileRepository,
            SkillSuiteRepository suiteRepository,
            SkillSuiteVersionRepository suiteVersionRepository,
            SkillSuiteVersionMemberRepository suiteMemberRepository,
            VisibilityChecker visibilityChecker,
            SecurityScanService securityScanService,
            Clock clock
    ) {
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.suiteRepository = suiteRepository;
        this.suiteVersionRepository = suiteVersionRepository;
        this.suiteMemberRepository = suiteMemberRepository;
        this.visibilityChecker = visibilityChecker;
        this.securityScanService = securityScanService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PreviewPlan plan(
            SkillSuiteBundlePackageAnalyzer.BundleAnalysis packageAnalysis,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        SkillSuiteBundleManifest manifest = packageAnalysis.manifest();
        Map<Long, NamespaceRole> safeNamespaceRoles = namespaceRoles == null ? Map.of() : namespaceRoles;
        Set<String> safePlatformRoles = platformRoles == null ? Set.of() : platformRoles;
        List<String> errors = new ArrayList<>(packageAnalysis.errors());
        List<String> warnings = new ArrayList<>();

        Set<String> namespaceSlugs = manifest.spec().members().stream()
                .map(member -> member.coordinate().namespace())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        namespaceSlugs.add(manifest.metadata().coordinate().namespace());
        Map<String, Namespace> namespaces = namespaceRepository.findBySlugIn(List.copyOf(namespaceSlugs)).stream()
                .collect(Collectors.toMap(Namespace::getSlug, Function.identity()));
        for (String namespaceSlug : namespaceSlugs) {
            if (!namespaces.containsKey(namespaceSlug)) {
                errors.add("Namespace not found: " + namespaceSlug);
            }
        }

        Target target = resolveTarget(
                manifest, namespaces.get(manifest.metadata().coordinate().namespace()), actorId,
                safeNamespaceRoles, safePlatformRoles, errors);

        List<Long> namespaceIds = namespaces.values().stream().map(Namespace::getId).distinct().toList();
        List<String> skillSlugs = manifest.spec().members().stream()
                .map(member -> member.coordinate().slug()).distinct().toList();
        List<Skill> skills = namespaceIds.isEmpty() || skillSlugs.isEmpty()
                ? List.of()
                : skillRepository.findByNamespaceIdInAndSlugIn(namespaceIds, skillSlugs);
        List<Long> skillIds = skills.stream().map(Skill::getId).distinct().toList();

        List<SkillVersion> pendingVersions = skillIds.isEmpty()
                ? List.of()
                : skillVersionRepository.findBySkillIdInAndStatus(skillIds, SkillVersionStatus.PENDING_REVIEW);
        List<String> requestedVersions = requestedVersions(packageAnalysis);
        List<SkillVersion> namedVersions = skillIds.isEmpty() || requestedVersions.isEmpty()
                ? List.of()
                : skillVersionRepository.findBySkillIdInAndVersionIn(skillIds, requestedVersions);

        List<Long> latestVersionIds = skills.stream()
                .map(Skill::getLatestVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<SkillVersion> latestVersions = latestVersionIds.isEmpty()
                ? List.of()
                : skillVersionRepository.findByIdIn(latestVersionIds);
        Map<Long, SkillVersion> publishCandidatesById = new LinkedHashMap<>();
        latestVersions.stream()
                .filter(version -> version.getStatus() == SkillVersionStatus.PUBLISHED)
                .forEach(version -> publishCandidatesById.put(version.getId(), version));
        namedVersions.stream()
                .filter(version -> version.getStatus() == SkillVersionStatus.PUBLISHED)
                .forEach(version -> publishCandidatesById.put(version.getId(), version));
        List<SkillVersion> publishedVersions = List.copyOf(publishCandidatesById.values());

        Map<Long, List<SkillVersion>> publishedBySkill = groupVersions(publishedVersions);
        Map<Long, List<SkillVersion>> pendingBySkill = groupVersions(pendingVersions);
        Map<Long, List<SkillVersion>> namedBySkill = groupVersions(namedVersions);
        List<Long> publishedVersionIds = publishedVersions.stream().map(SkillVersion::getId).distinct().toList();
        Map<Long, List<SkillFile>> filesByVersion = publishedVersionIds.isEmpty()
                ? Map.of()
                : skillFileRepository.findByVersionIdIn(publishedVersionIds).stream()
                .collect(Collectors.groupingBy(SkillFile::getVersionId));
        Map<Long, String> fingerprints = publishedVersions.stream().collect(Collectors.toMap(
                SkillVersion::getId,
                version -> fingerprint(filesByVersion.getOrDefault(version.getId(), List.of())),
                (left, right) -> left));

        Map<SkillSuiteBundleCoordinate, SkillSuiteBundlePackageAnalyzer.MemberPackageAnalysis> packageMembers =
                packageAnalysis.packageMembers().stream().collect(Collectors.toMap(
                        SkillSuiteBundlePackageAnalyzer.MemberPackageAnalysis::coordinate,
                        Function.identity()));
        Map<SkillSuiteBundleCoordinate, SkillSuiteVersionMember> baseline = baselineMembers(target);
        List<MemberPlan> members = new ArrayList<>();
        Set<SkillSuiteBundleCoordinate> desiredCoordinates = new LinkedHashSet<>();

        for (SkillSuiteBundleMember member : manifest.spec().members()) {
            desiredCoordinates.add(member.coordinate());
            Namespace namespace = namespaces.get(member.coordinate().namespace());
            List<Skill> coordinateSkills = namespace == null ? List.of() : skills.stream()
                    .filter(skill -> Objects.equals(skill.getNamespaceId(), namespace.getId()))
                    .filter(skill -> skill.getSlug().equals(member.coordinate().slug()))
                    .toList();
            MemberPlan planned = member.packageSource() != null
                    ? planPackageMember(
                            manifest, member, packageMembers.get(member.coordinate()), namespace,
                            coordinateSkills, publishedBySkill, pendingBySkill, namedBySkill,
                            fingerprints, actorId, safeNamespaceRoles, safePlatformRoles)
                    : planReferenceMember(
                            manifest, member, namespace, coordinateSkills, namedBySkill,
                            fingerprints, actorId, safeNamespaceRoles, safePlatformRoles);
            SkillSuiteVersionMember previous = baseline.get(member.coordinate());
            SkillSuiteBundleRelationshipChange relationship = previous == null
                    ? SkillSuiteBundleRelationshipChange.ADDED
                    : Objects.equals(previous.getSkillVersionId(), planned.skillVersionId())
                    ? SkillSuiteBundleRelationshipChange.UNCHANGED
                    : SkillSuiteBundleRelationshipChange.UPDATED;
            planned = planned.withRelationship(relationship);
            members.add(planned);
            planned.errors().forEach(error ->
                    errors.add(member.coordinate().canonical() + ": " + error));
            planned.warnings().forEach(warning ->
                    warnings.add(member.coordinate().canonical() + ": " + warning));
        }

        List<RemovedMemberPlan> removed = baseline.entrySet().stream()
                .filter(entry -> !desiredCoordinates.contains(entry.getKey()))
                .sorted(Comparator.comparingInt(entry -> entry.getValue().getPosition()))
                .map(entry -> new RemovedMemberPlan(
                        entry.getKey(), entry.getValue().getSkillId(),
                        entry.getValue().getSkillVersionId(), entry.getValue().getSkillVersionSnapshot(),
                        entry.getValue().isEntry(), SkillSuiteBundleRelationshipChange.REMOVED,
                        SkillSuiteBundlePublishAction.NONE))
                .toList();

        ResolvedPresentation presentation = resolvePresentation(manifest, target, errors);
        return new PreviewPlan(
                manifest.spec().mode(), manifest.metadata().coordinate(), target.namespaceId(), target.suiteId(),
                target.baseVersionId(), manifest.spec().version(), presentation.displayName(),
                presentation.summary(), presentation.overview(), manifest.spec().visibility(),
                List.copyOf(members), removed, List.copyOf(errors), List.copyOf(warnings),
                warningDigest(warnings));
    }

    private Target resolveTarget(
            SkillSuiteBundleManifest manifest,
            Namespace namespace,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            List<String> errors
    ) {
        if (namespace == null) {
            return Target.empty();
        }
        if (namespace.getStatus() != NamespaceStatus.ACTIVE) {
            errors.add("Target Namespace is not writable");
        }
        SkillSuiteBundleCoordinate coordinate = manifest.metadata().coordinate();
        SkillSuite suite = suiteRepository.findByNamespaceIdAndSlug(namespace.getId(), coordinate.slug()).orElse(null);
        boolean superAdmin = platformRoles.contains("SUPER_ADMIN");
        if (manifest.spec().mode() == SkillSuiteBundleMode.CREATE) {
            if (!superAdmin && !namespaceRoles.containsKey(namespace.getId())) {
                errors.add("No permission to create Suite in target Namespace");
            }
            if (suite != null) {
                errors.add("Target Suite coordinate already exists");
                return new Target(namespace.getId(), suite.getId(), null, null, suite);
            }
            return Target.empty(namespace.getId());
        }
        if (suite == null) {
            errors.add("Target Suite does not exist for UPDATE");
            return Target.empty();
        }
        SkillSuiteActionContext context = new SkillSuiteActionContext(
                actorId, namespaceRoles, platformRoles, null, null, null);
        if (!SkillSuiteAuthorizationPolicy.canCreateVersion(suite, context)) {
            errors.add("No permission to create a version for target Suite");
        }
        if (suite.getStatus() != SkillSuiteStatus.ACTIVE) {
            errors.add("Target Suite is not active");
        }
        SkillSuiteVersion base = suiteVersionRepository
                .findBySuiteIdAndVersion(suite.getId(), manifest.spec().baseVersion()).orElse(null);
        if (base == null || base.getStatus() != SkillSuiteVersionStatus.PUBLISHED) {
            errors.add("Base Suite version is not published or does not exist");
        }
        if (suiteVersionRepository.findBySuiteIdAndVersion(
                suite.getId(), manifest.spec().version()).isPresent()) {
            errors.add("Target Suite version already exists");
        }
        return new Target(namespace.getId(), suite.getId(), base == null ? null : base.getId(), base, suite);
    }

    private MemberPlan planPackageMember(
            SkillSuiteBundleManifest manifest,
            SkillSuiteBundleMember member,
            SkillSuiteBundlePackageAnalyzer.MemberPackageAnalysis packageAnalysis,
            Namespace namespace,
            List<Skill> coordinateSkills,
            Map<Long, List<SkillVersion>> publishedBySkill,
            Map<Long, List<SkillVersion>> pendingBySkill,
            Map<Long, List<SkillVersion>> namedBySkill,
            Map<Long, String> fingerprints,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = packageAnalysis == null
                ? List.of()
                : packageAnalysis.validation().warnings();
        if (packageAnalysis == null) {
            errors.add("Package analysis is missing");
            return MemberPlan.invalidPackage(member.coordinate(), errors);
        }
        errors.addAll(packageAnalysis.validation().errors());
        if (namespace == null) {
            errors.add("Member Namespace does not exist");
            return MemberPlan.packageResult(member.coordinate(), null, null, null,
                    packageAnalysis.fingerprint(), null, null, packageAnalysis.files(), errors, warnings);
        }
        if (namespace.getStatus() != NamespaceStatus.ACTIVE) {
            errors.add("Member Namespace is not writable");
        }

        Skill manageable = selectManageableSkill(
                coordinateSkills, actorId, namespaceRoles.get(namespace.getId()), platformRoles, errors);
        boolean hasExplicitVersion = packageAnalysis.metadata() != null
                && packageAnalysis.metadata().version() != null
                && !packageAnalysis.metadata().version().isBlank();
        String initialResolvedVersion = !hasExplicitVersion
                ? AUTO_VERSION_FORMATTER.format(clock.instant())
                : packageAnalysis.metadata().version();

        if (coordinateSkills.isEmpty()) {
            if (!platformRoles.contains("SUPER_ADMIN") && !namespaceRoles.containsKey(namespace.getId())) {
                errors.add("No permission to create Skill in member Namespace");
            }
            SkillVisibility requestedVisibility = member.packageSource().visibility();
            if (requestedVisibility == null) {
                errors.add("New Skill requires package.visibility");
            } else if (!audienceCompatible(
                    manifest.spec().visibility(), manifest.metadata().coordinate().namespace(),
                    requestedVisibility, member.coordinate().namespace())) {
                errors.add("New Skill visibility is incompatible with Suite audience");
            }
            if (requestedVisibility != null
                    && requestedVisibility != SkillVisibility.PRIVATE
                    && !securityScanService.isEnabled()) {
                errors.add("Security scanner is required for non-private Skill publication");
            }
            return MemberPlan.packageResult(
                    member.coordinate(), null, null, SkillSuiteBundlePublishAction.CREATE_SKILL,
                    packageAnalysis.fingerprint(), requestedVisibility, initialResolvedVersion,
                    packageAnalysis.files(), errors, warnings);
        }
        if (manageable == null) {
            return MemberPlan.packageResult(
                    member.coordinate(), null, null, null, packageAnalysis.fingerprint(), null,
                    initialResolvedVersion, packageAnalysis.files(), errors, warnings);
        }
        if (manageable.getStatus() != SkillStatus.ACTIVE || manageable.isHidden()) {
            errors.add("Existing Skill is not active and visible");
        }
        SkillVisibility requestedVisibility = member.packageSource().visibility();
        if (requestedVisibility != null && requestedVisibility != manageable.getVisibility()) {
            errors.add("Existing Skill visibility cannot be changed by Bundle");
        }
        SkillVisibility finalVisibility = manageable.getVisibility();
        if (!audienceCompatible(
                manifest.spec().visibility(), manifest.metadata().coordinate().namespace(),
                finalVisibility, member.coordinate().namespace())) {
            errors.add("Existing Skill visibility is incompatible with Suite audience");
        }
        if (!pendingBySkill.getOrDefault(manageable.getId(), List.of()).isEmpty()) {
            errors.add("Existing Skill has a pending review version");
        }

        List<SkillVersion> named = namedBySkill.getOrDefault(manageable.getId(), List.of()).stream()
                .filter(version -> initialResolvedVersion.equals(version.getVersion())).toList();
        SkillVersion reusable = named.stream()
                .filter(version -> version.getStatus() == SkillVersionStatus.PUBLISHED)
                .filter(SkillVersion::isDownloadReady)
                .filter(version -> version.getYankedAt() == null)
                .filter(version -> packageAnalysis.fingerprint().equals(fingerprints.get(version.getId())))
                .findFirst().orElse(null);
        String resolvedVersion = initialResolvedVersion;
        if (!hasExplicitVersion && reusable == null) {
            reusable = publishedBySkill.getOrDefault(manageable.getId(), List.of()).stream()
                    .filter(SkillVersion::isDownloadReady)
                    .filter(version -> version.getYankedAt() == null)
                    .filter(version -> packageAnalysis.fingerprint().equals(fingerprints.get(version.getId())))
                    .findFirst().orElse(null);
            if (reusable != null) {
                resolvedVersion = reusable.getVersion();
            }
        }
        SkillSuiteBundlePublishAction action;
        if (!named.isEmpty() && reusable == null) {
            errors.add("Target Skill version already exists with different content or non-published status");
            action = null;
        } else if (reusable != null) {
            action = SkillSuiteBundlePublishAction.REUSE_VERSION;
        } else {
            action = SkillSuiteBundlePublishAction.CREATE_VERSION;
        }
        if ((action == SkillSuiteBundlePublishAction.CREATE_VERSION)
                && finalVisibility != SkillVisibility.PRIVATE
                && !securityScanService.isEnabled()) {
            errors.add("Security scanner is required for non-private Skill publication");
        }
        return MemberPlan.packageResult(
                member.coordinate(), manageable.getId(), reusable == null ? null : reusable.getId(),
                action, packageAnalysis.fingerprint(), finalVisibility, resolvedVersion,
                packageAnalysis.files(), errors, warnings);
    }

    private MemberPlan planReferenceMember(
            SkillSuiteBundleManifest manifest,
            SkillSuiteBundleMember member,
            Namespace namespace,
            List<Skill> coordinateSkills,
            Map<Long, List<SkillVersion>> namedBySkill,
            Map<Long, String> fingerprints,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        String requestedVersion = member.referenceSource().version();
        List<ReferenceCandidate> candidates = new ArrayList<>();
        if (namespace != null) {
            for (Skill skill : coordinateSkills) {
                for (SkillVersion version : namedBySkill.getOrDefault(skill.getId(), List.of())) {
                    if (!requestedVersion.equals(version.getVersion())
                            || version.getStatus() != SkillVersionStatus.PUBLISHED) {
                        continue;
                    }
                    if (isReferenceEligible(
                            manifest, member.coordinate(), namespace, skill, version,
                            actorId, namespaceRoles, platformRoles)) {
                        candidates.add(new ReferenceCandidate(skill, version));
                    }
                }
            }
        }
        if (candidates.size() != 1) {
            return new MemberPlan(
                    member.coordinate(), SkillSuiteBundleMemberSourceType.REFERENCE,
                    SkillSuiteBundleRelationshipChange.ADDED, null, null, null, null,
                    requestedVersion, null, List.of(),
                    List.of("Exact reference is unavailable or ambiguous"), List.of());
        }
        ReferenceCandidate candidate = candidates.getFirst();
        return new MemberPlan(
                member.coordinate(), SkillSuiteBundleMemberSourceType.REFERENCE,
                SkillSuiteBundleRelationshipChange.ADDED,
                SkillSuiteBundlePublishAction.REFERENCE_VERSION,
                candidate.skill().getId(), candidate.version().getId(),
                candidate.skill().getVisibility(), requestedVersion,
                fingerprints.get(candidate.version().getId()), List.of(), List.of(), List.of());
    }

    private boolean isReferenceEligible(
            SkillSuiteBundleManifest manifest,
            SkillSuiteBundleCoordinate coordinate,
            Namespace namespace,
            Skill skill,
            SkillVersion version,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        return namespace.getStatus() == NamespaceStatus.ACTIVE
                && skill.getStatus() == SkillStatus.ACTIVE
                && !skill.isHidden()
                && version.isDownloadReady()
                && version.getYankedAt() == null
                && visibilityChecker.canAccess(skill, actorId, namespaceRoles, platformRoles)
                && audienceCompatible(
                        manifest.spec().visibility(), manifest.metadata().coordinate().namespace(),
                        skill.getVisibility(), coordinate.namespace());
    }

    private Skill selectManageableSkill(
            List<Skill> skills,
            String actorId,
            NamespaceRole namespaceRole,
            Set<String> platformRoles,
            List<String> errors
    ) {
        boolean namespaceAdmin = namespaceRole == NamespaceRole.OWNER || namespaceRole == NamespaceRole.ADMIN;
        boolean superAdmin = platformRoles.contains("SUPER_ADMIN");
        List<Skill> manageable = skills.stream()
                .filter(skill -> Objects.equals(skill.getOwnerId(), actorId) || namespaceAdmin || superAdmin)
                .toList();
        if (manageable.size() == 1) {
            return manageable.getFirst();
        }
        if (!skills.isEmpty()) {
            errors.add(manageable.isEmpty()
                    ? "No permission to publish existing Skill"
                    : "Skill coordinate is ambiguous across owners");
        }
        return null;
    }

    private Map<SkillSuiteBundleCoordinate, SkillSuiteVersionMember> baselineMembers(Target target) {
        if (target.baseVersionId() == null) {
            return Map.of();
        }
        return suiteMemberRepository.findBySuiteVersionIdOrderByPosition(target.baseVersionId()).stream()
                .collect(Collectors.toMap(
                        member -> new SkillSuiteBundleCoordinate(
                                member.getNamespaceSlugSnapshot(), member.getSkillSlugSnapshot()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private ResolvedPresentation resolvePresentation(
            SkillSuiteBundleManifest manifest, Target target, List<String> errors
    ) {
        SkillSuiteBundleManifest.Spec spec = manifest.spec();
        String displayName = spec.displayName();
        String summary = spec.summary() != null
                ? spec.summary()
                : target.baseVersion() == null ? null : target.baseVersion().getSummary();
        String overview = spec.overview() != null
                ? spec.overview()
                : target.baseVersion() == null ? null : target.baseVersion().getOverview();
        if (summary == null || summary.isBlank()) {
            errors.add("Suite summary is required after inheritance");
        }
        if (overview == null || overview.isBlank()) {
            errors.add("Suite overview is required after inheritance");
        }
        return new ResolvedPresentation(displayName, summary, overview);
    }

    private List<String> requestedVersions(
            SkillSuiteBundlePackageAnalyzer.BundleAnalysis packageAnalysis
    ) {
        LinkedHashSet<String> versions = new LinkedHashSet<>();
        packageAnalysis.manifest().spec().members().stream()
                .filter(member -> member.referenceSource() != null)
                .map(member -> member.referenceSource().version())
                .forEach(versions::add);
        packageAnalysis.packageMembers().stream()
                .map(SkillSuiteBundlePackageAnalyzer.MemberPackageAnalysis::metadata)
                .filter(Objects::nonNull)
                .map(metadata -> metadata.version())
                .filter(Objects::nonNull)
                .filter(version -> !version.isBlank())
                .forEach(versions::add);
        versions.add(AUTO_VERSION_FORMATTER.format(clock.instant()));
        return List.copyOf(versions);
    }

    private Map<Long, List<SkillVersion>> groupVersions(List<SkillVersion> versions) {
        return versions.stream().collect(Collectors.groupingBy(SkillVersion::getSkillId));
    }

    private String fingerprint(List<SkillFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            files.stream().sorted(Comparator.comparing(SkillFile::getFilePath)).forEach(file ->
                    digest.update((file.getFilePath() + ":" + file.getSha256() + "\n")
                            .getBytes(StandardCharsets.UTF_8)));
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String warningDigest(List<String> warnings) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            warnings.stream().sorted().forEach(warning -> {
                digest.update(warning.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean audienceCompatible(
            SkillVisibility suiteVisibility,
            String suiteNamespace,
            SkillVisibility memberVisibility,
            String memberNamespace
    ) {
        if (memberVisibility == SkillVisibility.PUBLIC) {
            return true;
        }
        if (suiteVisibility == SkillVisibility.PUBLIC || !suiteNamespace.equals(memberNamespace)) {
            return false;
        }
        if (suiteVisibility == SkillVisibility.NAMESPACE_ONLY) {
            return memberVisibility == SkillVisibility.NAMESPACE_ONLY;
        }
        return suiteVisibility == SkillVisibility.PRIVATE;
    }

    private record Target(
            Long namespaceId,
            Long suiteId,
            Long baseVersionId,
            SkillSuiteVersion baseVersion,
            SkillSuite suite
    ) {
        private static Target empty() {
            return new Target(null, null, null, null, null);
        }

        private static Target empty(Long namespaceId) {
            return new Target(namespaceId, null, null, null, null);
        }
    }

    private record ReferenceCandidate(Skill skill, SkillVersion version) {
    }

    private record ResolvedPresentation(String displayName, String summary, String overview) {
    }

    public record PreviewPlan(
            SkillSuiteBundleMode mode,
            SkillSuiteBundleCoordinate target,
            Long targetNamespaceId,
            Long targetSuiteId,
            Long baseSuiteVersionId,
            String targetVersion,
            String displayName,
            String summary,
            String overview,
            SkillVisibility visibility,
            List<MemberPlan> members,
            List<RemovedMemberPlan> removedMembers,
            List<String> errors,
            List<String> warnings,
            String warningDigest
    ) {
        public boolean confirmable() {
            return errors.isEmpty();
        }

        public boolean requiresWarningConfirmation() {
            return !warnings.isEmpty();
        }
    }

    public record MemberPlan(
            SkillSuiteBundleCoordinate coordinate,
            SkillSuiteBundleMemberSourceType sourceType,
            SkillSuiteBundleRelationshipChange relationship,
            SkillSuiteBundlePublishAction publishAction,
            Long skillId,
            Long skillVersionId,
            SkillVisibility finalVisibility,
            String resolvedVersion,
            String fingerprint,
            List<SkillSuiteBundlePackageAnalyzer.StagedMemberFile> files,
            List<String> errors,
            List<String> warnings
    ) {
        private static MemberPlan invalidPackage(
                SkillSuiteBundleCoordinate coordinate, List<String> errors
        ) {
            return packageResult(
                    coordinate, null, null, null, null, null, null, List.of(), errors, List.of());
        }

        private static MemberPlan packageResult(
                SkillSuiteBundleCoordinate coordinate,
                Long skillId,
                Long skillVersionId,
                SkillSuiteBundlePublishAction action,
                String fingerprint,
                SkillVisibility visibility,
                String version,
                List<SkillSuiteBundlePackageAnalyzer.StagedMemberFile> files,
                List<String> errors,
                List<String> warnings
        ) {
            return new MemberPlan(
                    coordinate, SkillSuiteBundleMemberSourceType.PACKAGE,
                    SkillSuiteBundleRelationshipChange.ADDED, action, skillId, skillVersionId,
                    visibility, version, fingerprint, files,
                    List.copyOf(errors), List.copyOf(warnings));
        }

        private MemberPlan withRelationship(SkillSuiteBundleRelationshipChange value) {
            return new MemberPlan(
                    coordinate, sourceType, value, publishAction, skillId, skillVersionId,
                    finalVisibility, resolvedVersion, fingerprint, files, errors, warnings);
        }
    }

    public record RemovedMemberPlan(
            SkillSuiteBundleCoordinate coordinate,
            Long skillId,
            Long skillVersionId,
            String version,
            boolean entry,
            SkillSuiteBundleRelationshipChange relationship,
            SkillSuiteBundlePublishAction publishAction
    ) {
    }
}
