package com.iflytek.skillhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class GithubRepositoryCatalogResponse {
    private GithubRepositoryCatalogResponse() {}

    @Schema(requiredProperties = {"repositoryId", "owner", "name", "memberCount"})
    public record RepositorySummary(String repositoryId, String owner, String name, long memberCount, List<GithubCatalogMember> preview) {}

    @Schema(requiredProperties = {"items", "total", "page", "size"})
    public record GithubCatalogRepositoryPage(List<RepositorySummary> items, long total, int page, int size) {}

    @Schema(requiredProperties = {"id", "slug", "displayName", "version"})
    public record GithubCatalogMember(long id, String slug, String displayName, String summary,
                                      String version, Integer syncRevision) {}

    @Schema(requiredProperties = {"id", "commit", "capturedAt", "status", "memberCount", "availableCount"})
    public record GithubRepositorySnapshot(String id, String commit, Instant capturedAt, String status,
                                           long memberCount, long availableCount) {}

    @Schema(requiredProperties = {"slug", "kind"})
    public record GithubRepositorySnapshotChange(String slug, String kind, String previousVersion, String currentVersion) {}

    @Schema(requiredProperties = {"baseCommit", "total", "items"})
    public record GithubRepositorySnapshotComparison(String baseCommit, long total, List<GithubRepositorySnapshotChange> items) {}

    @Schema(requiredProperties = {"repository", "items", "total", "page", "size", "snapshots"})
    public record GithubCatalogRepositoryDetail(RepositorySummary repository, List<GithubCatalogMember> items,
                                                 long total, int page, int size,
                                                 List<GithubRepositorySnapshot> snapshots, String selectedSnapshotId,
                                                 GithubRepositorySnapshotComparison comparison) {}
}
