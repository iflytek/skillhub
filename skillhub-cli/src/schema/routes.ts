export const ApiRoutes = {
  whoami: "/api/v1/whoami",
  skills: "/api/v1/skills",
  search: "/api/v1/search",
  meNamespaces: "/api/v1/me/namespaces",
  skillDetail: "/api/v1/skills/{namespace}/{slug}",
  skillStar: "/api/v1/skills/{namespace}/{slug}/star",
  skillVersions: "/api/v1/skills/{namespace}/{slug}/versions",
  skillDownload: "/api/v1/skills/{namespace}/{slug}/download",
  skillResolve: "/api/v1/skills/{namespace}/{slug}/resolve",
} as const;

export interface PublishResponse {
  skillId: string;
  namespace: string;
  slug: string;
  version: string;
  status: string;
}

export interface WhoamiResponse {
  userId: string;
  displayName: string;
  email?: string;
}

export interface NamespaceResponse {
  id: number;
  slug: string;
  displayName: string;
  currentUserRole: string;
  status: string;
}

export interface SearchResponse {
  results: Array<{
    slug: string;
    displayName: string;
    summary: string;
    version: string;
  }>;
}
