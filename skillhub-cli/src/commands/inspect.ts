import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes } from "../schema/routes.js";
import { loadConfigFromProgram } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { parseSkillName } from "../core/skill-name.js";
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
    .command("inspect <slug>")
    .aliases(["info", "view"])
    .description("View skill metadata without installing")
    .option("--namespace <ns>", "Search in specific namespace (searches all if not specified)")
    .option("--details", "Show all versions with tags")
    .action(async (slug: string, opts: { namespace?: string; details?: boolean }) => {
      const config = loadConfigFromProgram(program);
      const token = await readToken();
      const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

      const isJson = program.opts().json;
      const { namespace: defaultNs, slug: parsedSlug } = parseSkillName(slug, "");
      const targetNamespace = opts.namespace || defaultNs;

      async function fetchVersionsAndTags(ns: string, skillSlug: string) {
        if (!opts.details) return { versions: undefined, tags: undefined };
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

      async function displaySkillDetail(ns: string, skillSlug: string) {
        const detail = await client.get<SkillDetailResponse>(
          `${ApiRoutes.skillDetail.replace("{namespace}", ns).replace("{slug}", skillSlug)}`
        );
        const { versions, tags } = await fetchVersionsAndTags(ns, skillSlug);
        if (isJson) {
          const output = opts.details ? { ...detail, versions, tags } : detail;
          console.log(JSON.stringify(output, null, 2));
        } else {
          printSkillDetail(detail, versions, tags);
        }
      }

      if (targetNamespace) {
        await displaySkillDetail(targetNamespace, parsedSlug);
        return;
      }

      const spinner = ora(`Searching for ${parsedSlug}`).start();

      try {
        const results = await searchSkills(client, parsedSlug, 50);

        const seen = new Set<string>();
        const uniqueResults = results.filter(r => {
          const key = `${r.namespace}/${r.name}`;
          if (!seen.has(key)) {
            seen.add(key);
            return true;
          }
          return false;
        });

        if (uniqueResults.length === 0) {
          spinner.fail(`Skill not found: ${parsedSlug}`);
          process.exitCode = 1;
          return;
        }

        if (uniqueResults.length === 1) {
          spinner.stop();
          const ns = uniqueResults[0].namespace;
          const name = uniqueResults[0].name;
          await displaySkillDetail(ns, name);
          return;
        }

        spinner.succeed(`Found ${uniqueResults.length} matches for ${parsedSlug}`);

        const selected = await p.select({
          message: "Select skill to inspect",
          options: uniqueResults.map((r) => ({
            value: `${r.namespace}/${r.name}`,
            label: `${r.namespace}/${r.name}`,
            hint: r.summary ? r.summary.slice(0, 50) : undefined,
          })),
        });

        if (p.isCancel(selected)) {
          console.log("Cancelled.");
          return;
        }

        const [selectedNs, selectedName] = (selected as string).split("/", 2);
        await displaySkillDetail(selectedNs, selectedName);
      } catch (e: any) {
        spinner.fail(e.message);
        process.exitCode = 1;
      }
    });
}
