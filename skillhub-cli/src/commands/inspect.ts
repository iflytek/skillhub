import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes } from "../schema/routes.js";
import { loadConfigFromProgram } from "../core/config.js";
import { readToken } from "../core/auth-token.js";

import { info, dim, error } from "../utils/logger.js";
import { searchSkills } from "../core/interactive-search.js";
import * as p from "@clack/prompts";
import ora from "ora";

interface SkillDetailResponse {
  id: number;
  namespace: string;
  slug: string;
  displayName: string;
  ownerDisplayName: string;
  summary: string;
  visibility: string;
  status: string;
  starCount: number;
  downloadCount: number;
  labels: Array<{ slug: string; name: string }>;
  publishedVersion?: { version: string };
}

interface NamespaceInfo {
  slug: string;
  displayName: string;
  currentUserRole: string;
  status: string;
}

interface SkillVersionItem {
  id: number;
  version: string;
  status: string;
  changelog: string | null;
  fileCount: number;
  totalSize: number;
  publishedAt: string;
  downloadAvailable: boolean;
}

interface VersionsResponse {
  items: SkillVersionItem[];
  total: number;
  page: number;
  size: number;
}

interface SkillTag {
  id: number;
  tagName: string;
  versionId: number;
  createdAt: string;
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function printSkillDetail(detail: SkillDetailResponse, versions?: SkillVersionItem[], tags?: SkillTag[]) {
  console.log("");
  info(`${detail.displayName} (${detail.slug})`);
  dim(`Namespace: ${detail.namespace}`);
  dim(`Version:   ${detail.publishedVersion?.version || "N/A"}`);
  dim(`Author:    ${detail.ownerDisplayName}`);
  dim(`Stars:     ${detail.starCount}  Downloads: ${detail.downloadCount}`);
  if (detail.summary) console.log(`\n${detail.summary}`);
  dim(`Status:    ${detail.status}`);
  if (detail.labels && detail.labels.length > 0) {
    dim(`Labels:    ${detail.labels.map((l) => l.name || l.slug).join(", ")}`);
  }

  if (versions && versions.length > 0) {
    console.log("");
    info("Versions:");
    const versionTagsMap = new Map<number, string[]>();
    if (tags) {
      for (const tag of tags) {
        if (!versionTagsMap.has(tag.versionId)) {
          versionTagsMap.set(tag.versionId, []);
        }
        versionTagsMap.get(tag.versionId)!.push(tag.tagName);
      }
    }
    for (const v of versions) {
      const tagStr = versionTagsMap.get(v.id)?.join(", ") || "";
      dim(`  v${v.version}  ${v.status} · ${v.fileCount} files · ${formatBytes(v.totalSize)} · ${v.publishedAt}${tagStr ? " · tags: " + tagStr : ""}`);
    }
  }

  console.log("");
}

function printInspectHeader(detail: SkillDetailResponse, versions?: SkillVersionItem[], tags?: SkillTag[]) {
  console.log("");
  info(`=== ${detail.displayName} ===`);
  dim(`Namespace: ${detail.namespace}`);
  dim(`Slug: ${detail.slug}`);
  dim(`Version: ${detail.publishedVersion?.version || "N/A"}`);
  dim(`Author: ${detail.ownerDisplayName}`);
  console.log("");
  info("Summary:");
  console.log(`  ${detail.summary || "N/A"}`);
  console.log("");
  dim(`Stars: ${detail.starCount} · Downloads: ${detail.downloadCount}`);
  dim(`Visibility: ${detail.visibility} · Status: ${detail.status}`);
  if (detail.labels && detail.labels.length > 0) {
    console.log("");
    dim(`Labels: ${detail.labels.map((l) => l.name || l.slug).join(", ")}`);
  }

  if (versions && versions.length > 0) {
    console.log("");
    info("Versions:");
    const versionTagsMap = new Map<number, string[]>();
    if (tags) {
      for (const tag of tags) {
        if (!versionTagsMap.has(tag.versionId)) {
          versionTagsMap.set(tag.versionId, []);
        }
        versionTagsMap.get(tag.versionId)!.push(tag.tagName);
      }
    }
    for (const v of versions) {
      const tagStr = versionTagsMap.get(v.id)?.join(", ") || "";
      dim(`  v${v.version}  ${v.status} · ${v.fileCount} files · ${formatBytes(v.totalSize)} · ${v.publishedAt}${tagStr ? " · tags: " + tagStr : ""}`);
    }
  }

  console.log("");
}

export function registerInspect(program: Command) {
  program
    .command("inspect")
    .aliases(["info", "view"])
    .description("View skill metadata without installing")
    .argument("<skill>", "Skill name or namespace/skill-name")
    .option("--namespace <ns>", "Search in specific namespace (searches all if not specified)")
    .option("--details", "Show all versions with tags")
    .option("-v, --skill-version <ver>", "Inspect specific version")
    .action(async (slug: string, opts: { namespace?: string; details?: boolean; skillVersion?: string }) => {
      const config = loadConfigFromProgram(program);
      const token = await readToken();
      const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

      const isJson = program.opts().json;

      async function fetchVersionsAndTags(ns: string, skillSlug: string) {
        try {
          const [versionsResp, tagsResp] = await Promise.all([
            client.get<VersionsResponse>(`/api/v1/skills/${ns}/${skillSlug}/versions`),
            client.get<SkillTag[]>(`/api/v1/skills/${ns}/${skillSlug}/tags`).catch(() => [] as SkillTag[]),
          ]);
          return { versions: versionsResp.items || [], tags: tagsResp || [] };
        } catch {
          return { versions: undefined, tags: undefined };
        }
      }

      async function displaySkillDetail(ns: string, skillSlug: string, version?: string) {
        try {
          const detail = await client.get<SkillDetailResponse>(
            `${ApiRoutes.skillDetail.replace("{namespace}", ns).replace("{slug}", skillSlug)}`
          );

          const { versions, tags } = await fetchVersionsAndTags(ns, skillSlug);

          if (version && versions) {
            const selectedVersion = versions.find(v => v.version === version);
            if (selectedVersion) {
              detail.publishedVersion = { version: selectedVersion.version };
            }
          }

          if (isJson) {
            const output = opts.details ? { ...detail, versions, tags } : detail;
            console.log(JSON.stringify(output, null, 2));
          } else {
            printSkillDetail(detail, opts.details ? versions : undefined, opts.details ? tags : undefined);
          }
        } catch (e: any) {
          if (e.statusCode === 403) {
            error(`Access denied: ${ns}/${skillSlug}`);
            dim("Run 'skillhub login' to authenticate.");
          } else if (e.statusCode === 404) {
            error(`Skill not found: ${ns}/${skillSlug}`);
          } else {
            error(`Failed to fetch skill details: ${e.message}`);
          }
          process.exitCode = 1;
        }
      }

      async function inspectWithVersionSelection(ns: string, skillSlug: string) {
        const { versions, tags } = await fetchVersionsAndTags(ns, skillSlug);

        if (!versions || versions.length === 0) {
          await displaySkillDetail(ns, skillSlug);
          return;
        }

        if (opts.details) {
          await displaySkillDetail(ns, skillSlug);
          return;
        }

        if (versions.length === 1) {
          await displaySkillDetail(ns, skillSlug);
          return;
        }

        const versionTagsMap = new Map<number, string[]>();
        if (tags) {
          for (const tag of tags) {
            if (!versionTagsMap.has(tag.versionId)) {
              versionTagsMap.set(tag.versionId, []);
            }
            versionTagsMap.get(tag.versionId)!.push(tag.tagName);
          }
        }

        const selected = await p.select({
          message: "Select version to inspect",
          options: versions.map((v) => ({
            value: v.version,
            label: `v${v.version}`,
            hint: versionTagsMap.get(v.id)?.join(", ") || "",
          })),
        });

        if (p.isCancel(selected)) {
          console.log("Cancelled.");
          return;
        }

        await displaySkillDetail(ns, skillSlug, selected as string);
      }

      const { resolveSkillNamespace } = await import("../core/skill-resolver.js");
      const { namespace: targetNamespace, slug: parsedSlug } = await resolveSkillNamespace(client, slug, opts.namespace);

      if (opts.skillVersion) {
        await displaySkillDetail(targetNamespace, parsedSlug, opts.skillVersion);
      } else {
        await inspectWithVersionSelection(targetNamespace, parsedSlug);
      }
    });
}
