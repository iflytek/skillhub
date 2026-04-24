package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.PublishResponse;
import com.iflytek.skillhub.dto.PublishResultDetailResponse;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.service.support.SkillPackageArchiveExtractor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SkillPublishAppService {

    private static final String SKILL_MD = "SKILL.md";
    private static final String SKILL_MD_SUFFIX = "/" + SKILL_MD;

    private final SkillPublishService skillPublishService;
    private final SkillPackageArchiveExtractor skillPackageArchiveExtractor;
    private final SkillHubMetrics skillHubMetrics;

    public SkillPublishAppService(SkillPublishService skillPublishService,
                                  SkillPackageArchiveExtractor skillPackageArchiveExtractor,
                                  SkillHubMetrics skillHubMetrics) {
        this.skillPublishService = skillPublishService;
        this.skillPackageArchiveExtractor = skillPackageArchiveExtractor;
        this.skillHubMetrics = skillHubMetrics;
    }

    @Transactional
    public PublishResponse publish(String namespace,
                                   MultipartFile file,
                                   SkillVisibility visibility,
                                   String publisherId,
                                   Set<String> platformRoles,
                                   boolean confirmWarnings) throws IOException {
        List<PackageEntry> entries;
        try {
            entries = skillPackageArchiveExtractor.extract(file);
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", e.getMessage());
        }

        List<DiscoveredPackage> packages = discoverPackages(entries);
        List<PublishResultDetailResponse> details = new ArrayList<>();
        for (DiscoveredPackage skillPackage : packages) {
            SkillPublishService.PublishResult result = skillPublishService.publishFromEntries(
                    namespace,
                    skillPackage.entries(),
                    publisherId,
                    visibility,
                    platformRoles,
                    confirmWarnings
            );
            details.add(toDetail(namespace, skillPackage.packagePath(), result));
        }

        for (PublishResultDetailResponse detail : details) {
            skillHubMetrics.incrementSkillPublish(namespace, detail.status());
        }

        PublishResultDetailResponse first = details.getFirst();
        return new PublishResponse(
                first.skillId(),
                first.namespace(),
                first.slug(),
                first.version(),
                first.status(),
                first.fileCount(),
                first.totalSize(),
                List.copyOf(details)
        );
    }

    private List<DiscoveredPackage> discoverPackages(List<PackageEntry> entries) {
        if (entries.stream().anyMatch(entry -> SKILL_MD.equals(entry.path()))) {
            return List.of(new DiscoveredPackage("", entries));
        }

        List<String> packageRoots = entries.stream()
                .map(PackageEntry::path)
                .filter(path -> path.endsWith(SKILL_MD_SUFFIX))
                .map(path -> path.substring(0, path.length() - SKILL_MD_SUFFIX.length()))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        if (packageRoots.isEmpty()) {
            throw new DomainBadRequestException("error.skill.publish.skillMd.notFound");
        }

        rejectNestedPackageRoots(packageRoots);

        List<DiscoveredPackage> packages = new ArrayList<>();
        for (String root : packageRoots) {
            String prefix = root + "/";
            List<PackageEntry> packageEntries = entries.stream()
                    .filter(entry -> entry.path().startsWith(prefix))
                    .map(entry -> new PackageEntry(
                            entry.path().substring(prefix.length()),
                            entry.content(),
                            entry.size(),
                            entry.contentType()
                    ))
                    .toList();
            packages.add(new DiscoveredPackage(root, packageEntries));
        }

        return packages;
    }

    private void rejectNestedPackageRoots(List<String> packageRoots) {
        for (int i = 0; i < packageRoots.size(); i++) {
            String parent = packageRoots.get(i) + "/";
            for (int j = i + 1; j < packageRoots.size(); j++) {
                if (packageRoots.get(j).startsWith(parent)) {
                    throw new DomainBadRequestException(
                            "error.skill.publish.package.invalid",
                            "Nested skill packages are not supported: " + packageRoots.get(j)
                    );
                }
            }
        }
    }

    private PublishResultDetailResponse toDetail(String namespace,
                                                 String packagePath,
                                                 SkillPublishService.PublishResult result) {
        return new PublishResultDetailResponse(
                packagePath,
                result.skillId(),
                namespace,
                result.slug(),
                result.version().getVersion(),
                result.version().getStatus().name(),
                result.version().getFileCount(),
                result.version().getTotalSize()
        );
    }

    private record DiscoveredPackage(String packagePath, List<PackageEntry> entries) {}
}
