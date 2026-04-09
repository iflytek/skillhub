import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes } from "../schema/routes.js";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { parseSkillName } from "../core/skill-name.js";
import { info, dim, error } from "../utils/logger.js";

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

function printSkillDetail(detail: SkillDetailResponse) {
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
  console.log("");
}

function printInspectHeader(detail: SkillDetailResponse) {
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
  console.log("");
}

export function registerInspect(program: Command) {
  program
    .command("inspect <slug>")
    .aliases(["info", "view"])
    .description("View skill metadata without installing")
    .option("--namespace <ns>", "Namespace (searches all accessible namespaces if not specified)")
    .action(async (slug: string, opts: { namespace?: string }) => {
      const config = loadConfig();
      const token = await readToken();
      const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

      const isJson = program.opts().json;
      const { namespace: defaultNs, slug: parsedSlug } = parseSkillName(slug, "global");
      const targetNamespace = opts.namespace || defaultNs;

      if (targetNamespace !== "global" || opts.namespace) {
        const detail = await client.get<SkillDetailResponse>(
          `${ApiRoutes.skillDetail.replace("{namespace}", targetNamespace).replace("{slug}", parsedSlug)}`
        );
        if (isJson) {
          console.log(JSON.stringify(detail, null, 2));
        } else {
          printSkillDetail(detail);
        }
        return;
      }

      const namespaces = await client.get<NamespaceInfo[]>(ApiRoutes.meNamespaces);
      
      if (!namespaces || namespaces.length === 0) {
        error("No namespaces found. You may need to log in.");
        process.exit(1);
      }

      const searchPromises = namespaces.map(async (ns) => {
        try {
          const detail = await client.get<SkillDetailResponse>(
            `${ApiRoutes.skillDetail.replace("{namespace}", ns.slug).replace("{slug}", parsedSlug)}`
          );
          return { found: true, detail, namespace: ns.slug };
        } catch {
          return { found: false, detail: null, namespace: ns.slug };
        }
      });

      const results = await Promise.all(searchPromises);
      const matches = results.filter((r) => r.found && r.detail).map((r) => r.detail!);

      if (matches.length === 0) {
        error(`Skill not found: ${parsedSlug}`);
        if (namespaces.length > 1) {
          dim(`Tried namespaces: ${namespaces.map((n) => n.slug).join(", ")}`);
        }
        process.exit(1);
      }

      if (isJson) {
        if (matches.length === 1) {
          console.log(JSON.stringify(matches[0], null, 2));
        } else {
          console.log(JSON.stringify(matches, null, 2));
        }
      } else if (matches.length === 1) {
        printSkillDetail(matches[0]);
      } else {
        for (const detail of matches) {
          printInspectHeader(detail);
        }
      }
    });
}
