import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig } from "../core/config.js";
import { success, error, info, dim } from "../utils/logger.js";
import { parseSkillName } from "../core/skill-name.js";

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

export function registerVersions(program: Command) {
  program
    .command("versions <slug>")
    .description("List skill versions")
    .action(async (slug: string) => {
      try {
        const { namespace, slug: skillSlug } = parseSkillName(slug);
        const token = await requireToken();
        const config = loadConfig();
        const client = new ApiClient({ baseUrl: config.registry, token });
        const resp = await client.get<VersionsResponse>(
          `/api/v1/skills/${namespace}/${skillSlug}/versions`
        );
        const versions = resp.items || [];
        if (versions.length === 0) {
          console.log("No versions found.");
          return;
        }
        for (const v of versions) {
          info(`v${v.version}`);
          dim(`  ${v.status} · ${v.fileCount} files · ${v.totalSize} bytes · ${v.publishedAt}`);
        }
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exit(1);
      }
    });
}
