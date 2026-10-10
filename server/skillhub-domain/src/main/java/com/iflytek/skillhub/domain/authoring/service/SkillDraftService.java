package com.iflytek.skillhub.domain.authoring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.authoring.DraftFile;
import com.iflytek.skillhub.domain.authoring.FilePatch;
import com.iflytek.skillhub.domain.authoring.SkillDraft;
import com.iflytek.skillhub.domain.authoring.SkillDraftRepository;
import com.iflytek.skillhub.domain.authoring.DraftFileRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service for skill authoring drafts: creation with a scaffold, content-addressed
 * file storage, revision and digest bookkeeping, patch application, and materialization
 * into package entries for validation and submission.
 *
 * <p>Revisions advance only when content actually changes; a no-op save keeps the
 * revision (and therefore a prior validation verdict) intact. Every content change
 * recomputes the digest, which binds validation runs to exact draft content.
 */
@Service
public class SkillDraftService {

    private static final Logger log = LoggerFactory.getLogger(SkillDraftService.class);
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_REQUIREMENT_LENGTH = 8_000;
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    public record SaveFileOutcome(SkillDraft draft, DraftFile file, boolean created, boolean revisionAdvanced) {}

    private final SkillDraftRepository draftRepository;
    private final DraftFileRepository fileRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final ObjectStorageService objectStorageService;
    private final SkillScaffoldGenerator scaffoldGenerator;
    private final ObjectMapper objectMapper;

    public SkillDraftService(SkillDraftRepository draftRepository,
                             DraftFileRepository fileRepository,
                             NamespaceRepository namespaceRepository,
                             NamespaceMemberRepository namespaceMemberRepository,
                             ObjectStorageService objectStorageService,
                             SkillScaffoldGenerator scaffoldGenerator,
                             ObjectMapper objectMapper) {
        this.draftRepository = draftRepository;
        this.fileRepository = fileRepository;
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.objectStorageService = objectStorageService;
        this.scaffoldGenerator = scaffoldGenerator;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- draft lifecycle

    /**
     * Creates a draft in the target namespace and seeds it with a SKILL.md scaffold
     * derived from the requirement description.
     */
    @Transactional
    public SkillDraft createDraft(String namespaceSlug, String ownerId, String name,
                                  String requirement, Set<String> platformRoles) {
        validateName(name);
        if (requirement != null && requirement.length() > MAX_REQUIREMENT_LENGTH) {
            throw new DomainBadRequestException("error.authoring.draft.requirement.tooLong",
                    MAX_REQUIREMENT_LENGTH);
        }

        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new DomainNotFoundException("error.authoring.namespace.notFound", namespaceSlug));
        assertNamespaceWritable(namespace);
        assertNamespaceMember(namespace, ownerId, platformRoles);

        if (draftRepository.findByOwnerIdAndNameIgnoreCase(ownerId, name).isPresent()) {
            throw new DomainConflictException("error.authoring.draft.name.duplicate", name);
        }

        String scaffold = scaffoldGenerator.generateSkillMd(name, requirement);
        SkillDraft draft = draftRepository.save(
                new SkillDraft(namespace.getId(), ownerId, name, requirement, "pending"));
        DraftFile scaffoldFile = storeFile(draft.getId(), SkillPackagePolicy.SKILL_MD_PATH,
                scaffold.getBytes(StandardCharsets.UTF_8), "text/markdown");
        fileRepository.save(scaffoldFile);
        // the scaffold must be readable immediately: editor, structure validation,
        // and submit all read it back from object storage
        putContent(scaffoldFile.getStorageKey(), scaffold.getBytes(StandardCharsets.UTF_8),
                "text/markdown");
        draft.applyContentChange(1, computeDigest(draft.getId()));
        return draftRepository.save(draft);
    }

    @Transactional(readOnly = true)
    public List<SkillDraft> listDrafts(String ownerId) {
        return draftRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId);
    }

    @Transactional(readOnly = true)
    public SkillDraft getOwnedDraft(Long draftId, String userId, Set<String> platformRoles) {
        SkillDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new DomainNotFoundException("error.authoring.draft.notFound", draftId));
        assertOwner(draft, userId, platformRoles);
        return draft;
    }

    /** Trusted accessor for internal executors (validation pipeline, submit flow). */
    @Transactional(readOnly = true)
    public SkillDraft getDraft(Long draftId) {
        return draftRepository.findById(draftId)
                .orElseThrow(() -> new DomainNotFoundException("error.authoring.draft.notFound", draftId));
    }

    @Transactional
    public void deleteDraft(Long draftId, String userId, Set<String> platformRoles) {
        SkillDraft draft = getOwnedDraft(draftId, userId, platformRoles);
        List<String> storageKeys = fileRepository.findByDraftIdOrderByFilePath(draftId).stream()
                .map(DraftFile::getStorageKey)
                .toList();
        if (!storageKeys.isEmpty()) {
            try {
                objectStorageService.deleteObjects(storageKeys);
            } catch (Exception exception) {
                // storage garbage is not worth blocking a draft deletion; keys are
                // namespaced under drafts/{draftId} and become unreachable
                log.warn("Failed to clean up draft storage for draft {}: {}",
                        draftId, exception.getMessage());
            }
        }
        draftRepository.delete(draft);
    }

    // ---------------------------------------------------------------- file editing

    /**
     * Saves one file. When {@code expectedRevision} is provided it must match the current
     * revision, otherwise the save is rejected with a conflict (optimistic concurrency).
     */
    @Transactional
    public SaveFileOutcome saveFile(Long draftId, String userId, String rawPath, byte[] content,
                                    String contentType, Integer expectedRevision,
                                    Set<String> platformRoles) {
        SkillDraft draft = getOwnedDraft(draftId, userId, platformRoles);
        assertRevisionMatches(draft, expectedRevision);
        String path = normalizeAndCheckPath(rawPath);

        if (content == null) {
            throw new DomainBadRequestException("error.authoring.file.content.required");
        }
        if (content.length > SkillPackagePolicy.MAX_SINGLE_FILE_SIZE) {
            throw new DomainBadRequestException("error.authoring.file.tooLarge", path,
                    SkillPackagePolicy.MAX_SINGLE_FILE_SIZE);
        }
        String mismatch = SkillPackagePolicy.validateContentMatchesExtension(path, content);
        if (mismatch != null) {
            throw new DomainBadRequestException("error.authoring.file.contentMismatch", path, mismatch);
        }

        Optional<DraftFile> existing = fileRepository.findByDraftIdAndFilePath(draftId, path);
        if (existing.isEmpty() && fileRepository.countByDraftId(draftId) >= SkillPackagePolicy.MAX_FILE_COUNT) {
            throw new DomainBadRequestException("error.authoring.file.countExceeded",
                    SkillPackagePolicy.MAX_FILE_COUNT);
        }
        long currentTotal = fileRepository.sumSizeByDraftId(draftId)
                - existing.map(DraftFile::getSize).orElse(0L);
        if (currentTotal + content.length > SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE) {
            throw new DomainBadRequestException("error.authoring.package.tooLarge",
                    SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE);
        }

        DraftFile file = existing
                .map(current -> {
                    current.updateContent(sha256Hex(content), (long) content.length,
                            resolveContentType(path, contentType), storageKey(draftId, content));
                    return current;
                })
                .orElseGet(() -> storeFile(draftId, path, content, resolveContentType(path, contentType)));
        file = fileRepository.save(file);
        // write content after resolving the key so the same digest always lands in the same object
        putContent(storageKey(draftId, content), content, resolveContentType(path, contentType));

        boolean revisionAdvanced = advanceRevisionIfContentChanged(draft);
        return new SaveFileOutcome(draft, file, existing.isEmpty(), revisionAdvanced);
    }

    @Transactional
    public void deleteFile(Long draftId, String userId, String rawPath, Integer expectedRevision,
                           Set<String> platformRoles) {
        SkillDraft draft = getOwnedDraft(draftId, userId, platformRoles);
        assertRevisionMatches(draft, expectedRevision);
        String path = normalizeAndCheckPath(rawPath);
        DraftFile file = fileRepository.findByDraftIdAndFilePath(draftId, path)
                .orElseThrow(() -> new DomainNotFoundException("error.authoring.file.notFound", path));
        fileRepository.delete(file);
        advanceRevisionIfContentChanged(draft);
    }

    @Transactional(readOnly = true)
    public List<DraftFile> listFiles(Long draftId) {
        return fileRepository.findByDraftIdOrderByFilePath(draftId);
    }

    @Transactional(readOnly = true)
    public FileContent readFile(Long draftId, String userId, String rawPath,
                                Set<String> platformRoles) {
        getOwnedDraft(draftId, userId, platformRoles);
        String path;
        try {
            path = SkillPackagePolicy.canonicalizeSkillMdPath(
                    SkillPackagePolicy.normalizeEntryPath(rawPath));
        } catch (IllegalArgumentException exception) {
            throw new DomainBadRequestException("error.authoring.file.path.invalid", rawPath);
        }
        DraftFile file = fileRepository.findByDraftIdAndFilePath(draftId, path)
                .orElseThrow(() -> new DomainNotFoundException("error.authoring.file.notFound", path));
        byte[] content;
        try (InputStream input = objectStorageService.getObject(file.getStorageKey())) {
            content = input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read draft file: " + path, exception);
        }
        return new FileContent(file, content);
    }

    public record FileContent(DraftFile file, byte[] content) {
        public String asText() {
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    // ---------------------------------------------------------------- patches

    /**
     * Applies a confirmed set of file patches atomically: every patch must target the
     * exact current content (sha256 match), and the whole batch produces exactly one
     * new draft revision.
     */
    @Transactional
    public SkillDraft applyPatch(Long draftId, String userId, List<FilePatch> patches,
                                 Integer expectedRevision, Set<String> platformRoles) {
        SkillDraft draft = getOwnedDraft(draftId, userId, platformRoles);
        assertRevisionMatches(draft, expectedRevision);
        if (patches == null || patches.isEmpty()) {
            throw new DomainBadRequestException("error.authoring.patch.empty");
        }

        for (FilePatch patch : patches) {
            String path = normalizeAndCheckPath(patch.filePath());
            byte[] content = patch.newValue().getBytes(StandardCharsets.UTF_8);
            String contentType = resolveContentType(path, null);
            Optional<DraftFile> existing = fileRepository.findByDraftIdAndFilePath(draftId, path);
            if (patch.isCreation()) {
                if (existing.isPresent()) {
                    throw new DomainConflictException("error.authoring.patch.fileExists", path);
                }
                fileRepository.save(storeFile(draftId, path, content, contentType));
            } else {
                DraftFile current = existing.orElseThrow(() ->
                        new DomainConflictException("error.authoring.patch.fileMissing", path));
                String currentSha = current.getSha256();
                if (patch.oldSha256() == null || !patch.oldSha256().equalsIgnoreCase(currentSha)) {
                    throw new DomainConflictException("error.authoring.patch.stale", path);
                }
                current.updateContent(sha256Hex(content), (long) content.length,
                        contentType, storageKey(draftId, content));
                fileRepository.save(current);
            }
            putContent(storageKey(draftId, content), content, contentType);
        }

        boolean advanced = advanceRevisionIfContentChanged(draft);
        if (!advanced) {
            // a patch that does not change the digest is a programming error upstream
            throw new DomainBadRequestException("error.authoring.patch.noChange");
        }
        return draft;
    }

    // ---------------------------------------------------------------- materialization

    /**
     * Builds streaming package entries from the draft's current files. Used by the
     * validation orchestrator (snapshot) and the submit flow (publish input).
     */
    @Transactional(readOnly = true)
    public List<PackageEntry> materializeEntries(Long draftId) {
        List<PackageEntry> entries = new ArrayList<>();
        for (DraftFile file : fileRepository.findByDraftIdOrderByFilePath(draftId)) {
            entries.add(PackageEntry.streaming(
                    file.getFilePath(),
                    file.getSize(),
                    file.getContentType(),
                    () -> objectStorageService.getObject(file.getStorageKey())));
        }
        return entries;
    }

    @Transactional(readOnly = true)
    public String computeDigest(Long draftId) {
        List<DraftFile> files = new ArrayList<>(fileRepository.findByDraftIdOrderByFilePath(draftId));
        files.sort(Comparator.comparing(DraftFile::getFilePath));
        StringBuilder manifest = new StringBuilder();
        for (DraftFile file : files) {
            manifest.append(file.getFilePath()).append('\u0000')
                    .append(file.getSha256()).append('\n');
        }
        return sha256Hex(manifest.toString());
    }

    @Transactional(readOnly = true)
    public String resolveNamespaceSlug(SkillDraft draft) {
        return namespaceRepository.findById(draft.getNamespaceId())
                .map(Namespace::getSlug)
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.authoring.namespace.notFound", draft.getNamespaceId()));
    }

    // ---------------------------------------------------------------- internals

    private DraftFile storeFile(Long draftId, String path, byte[] content, String contentType) {
        return new DraftFile(draftId, path, sha256Hex(content), (long) content.length,
                contentType, storageKey(draftId, content));
    }

    private void putContent(String key, byte[] content, String contentType) {
        if (!objectStorageService.exists(key)) {
            objectStorageService.putObject(key, new ByteArrayInputStream(content),
                    content.length, contentType);
        }
    }

    private boolean advanceRevisionIfContentChanged(SkillDraft draft) {
        String newDigest = computeDigest(draft.getId());
        if (newDigest.equals(draft.getContentDigest())) {
            return false;
        }
        draft.applyContentChange(draft.getRevision() + 1, newDigest);
        draftRepository.save(draft);
        return true;
    }

    private String storageKey(Long draftId, byte[] content) {
        return "drafts/" + draftId + "/" + sha256Hex(content);
    }

    private String resolveContentType(String path, String provided) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return "application/yaml";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".py")) {
            return "text/x-python";
        }
        if (lower.endsWith(".sh")) {
            return "text/x-shellscript";
        }
        return "application/octet-stream";
    }

    private String normalizeAndCheckPath(String rawPath) {
        String normalized;
        try {
            normalized = SkillPackagePolicy.canonicalizeSkillMdPath(
                    SkillPackagePolicy.normalizeEntryPath(rawPath));
        } catch (IllegalArgumentException exception) {
            throw new DomainBadRequestException("error.authoring.file.path.invalid", rawPath);
        }
        if (!SkillPackagePolicy.hasAllowedExtension(normalized)) {
            throw new DomainBadRequestException("error.authoring.file.extension.disallowed", normalized);
        }
        return normalized;
    }

    private void assertRevisionMatches(SkillDraft draft, Integer expectedRevision) {
        if (expectedRevision != null && !expectedRevision.equals(draft.getRevision())) {
            throw new DomainConflictException("error.authoring.draft.revision.conflict",
                    expectedRevision, draft.getRevision());
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainBadRequestException("error.authoring.draft.name.required");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new DomainBadRequestException("error.authoring.draft.name.tooLong", MAX_NAME_LENGTH);
        }
    }

    private void assertNamespaceWritable(Namespace namespace) {
        if (namespace.getStatus() == NamespaceStatus.FROZEN) {
            throw new DomainForbiddenException("error.authoring.namespace.frozen", namespace.getSlug());
        }
        if (namespace.getStatus() == NamespaceStatus.ARCHIVED) {
            throw new DomainForbiddenException("error.authoring.namespace.archived", namespace.getSlug());
        }
    }

    private void assertNamespaceMember(Namespace namespace, String userId, Set<String> platformRoles) {
        if (platformRoles != null && platformRoles.contains(SUPER_ADMIN)) {
            return;
        }
        if (namespaceMemberRepository
                .findByNamespaceIdAndUserId(namespace.getId(), userId).isEmpty()) {
            throw new DomainForbiddenException("error.authoring.draft.notMember", namespace.getSlug());
        }
    }

    private void assertOwner(SkillDraft draft, String userId, Set<String> platformRoles) {
        if (draft.getOwnerId().equals(userId)
                || (platformRoles != null && platformRoles.contains(SUPER_ADMIN))) {
            return;
        }
        throw new DomainForbiddenException("error.authoring.draft.forbidden", draft.getId());
    }

    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static String sha256Hex(String content) {
        return sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }
}
