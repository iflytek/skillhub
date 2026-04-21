import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { readToken } from "../core/auth-token.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { error, info, dim, success } from "../utils/logger.js";
import { parseSkillName } from "../core/skill-name.js";
import { searchSkills, runInteractiveSearch } from "../core/interactive-search.js";

export interface SkillVersionItem {
  id: number;
  version: string;
  status: string;
  changelog: string | null;
  fileCount: number;
  totalSize: number;
  publishedAt: string;
  downloadAvailable: boolean;
}

export interface VersionsResponse {
  items: SkillVersionItem[];
  total: number;
  page: number;
  size: number;
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

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

export function registerVersions(program: Command) {
  program
    .command("versions <slug>")
    .description("List skill versions")
    .option("--detail", "Show additional skill metadata (stars, downloads, summary)")
    .action(async (slug: string, opts: { detail?: boolean }) => {
      try {
        const { namespace, slug: skillSlug } = parseSkillName(slug);
        const config = loadConfigFromProgram(program);
        const token = await readToken();
        const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

        let targetNamespace = namespace;
        let targetSlug = skillSlug;

        if (namespace === "global") {
          const results = await searchSkills(client, skillSlug, 50);

          const seen = new Set<string>();
          const uniqueResults = results.filter((r) => {
            const key = `${r.namespace}/${r.name}`;
            if (!seen.has(key)) {
              seen.add(key);
              return true;
            }
            return false;
          });

          if (uniqueResults.length === 0) {
            error(`Skill not found: ${skillSlug}`);
            process.exitCode = 1;
          }

          if (uniqueResults.length === 1) {
            targetNamespace = uniqueResults[0].namespace;
            targetSlug = uniqueResults[0].name;
          } else {
            const selected = await runInteractiveSearch(client, skillSlug);
            if (!selected) {
              info("Cancelled.");
              return;
            }
            const [ns, name] = selected.split("/", 2);
            targetNamespace = ns;
            targetSlug = name;
          }
        }

        const resp = await client.get<VersionsResponse>(
          `/api/v1/skills/${targetNamespace}/${targetSlug}/versions`
        );
        const versions = resp.items || [];
        if (versions.length === 0) {
          console.log("No versions found.");
          return;
        }

        if (opts.detail) {
          try {
            const detail = await client.get<SkillDetailResponse>(
              `/api/v1/skills/${targetNamespace}/${targetSlug}`
            );
            console.log("");
            info(`${detail.displayName} (${detail.slug})`);
            dim(`Namespace: ${detail.namespace}`);
            dim(`Author:    ${detail.ownerDisplayName}`);
            dim(`Stars:     ${detail.starCount}  Downloads: ${detail.downloadCount}`);
            if (detail.summary) console.log(`\n${detail.summary}`);
            if (detail.labels && detail.labels.length > 0) {
              dim(`Labels:    ${detail.labels.map((l) => l.name || l.slug).join(", ")}`);
            }
            console.log("");
          } catch {}
        }

        if (targetNamespace !== "global" && !opts.detail) {
          success(`${targetNamespace}/${targetSlug}`);
        }
        for (const v of versions) {
          info(`v${v.version}`);
          dim(`  ${v.status} · ${v.fileCount} files · ${formatBytes(v.totalSize)} · ${v.publishedAt}`);
        }
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exitCode = 1;
      }
    });
}
