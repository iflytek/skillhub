package com.iflytek.skillhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public final class GithubRepositoryCatalogResponse {
    private GithubRepositoryCatalogResponse() {}

    @Schema(requiredProperties = {"repositoryId", "owner", "name", "memberCount"})
    public record RepositorySummary(String repositoryId, String owner, String name, long memberCount) {}

    @Schema(requiredProperties = {"items", "total", "page", "size"})
    public record GithubCatalogRepositoryPage(List<RepositorySummary> items, long total, int page, int size) {}

    @Schema(requiredProperties = {"id", "slug", "displayName", "version"})
    public record GithubCatalogMember(long id, String slug, String displayName, String summary,
                                      String version, Integer syncRevision) {}

    @Schema(requiredProperties = {"repository", "items", "page", "size"})
    public record GithubCatalogRepositoryDetail(RepositorySummary repository, List<GithubCatalogMember> items,
                                                 int page, int size) {}
}
