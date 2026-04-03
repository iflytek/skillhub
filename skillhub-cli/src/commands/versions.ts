import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig } from "../core/config.js";
import { success, error, info, dim } from "../utils/logger.js";

export interface SkillVersion {
  version: string;
  status: string;
  createdAt: string;
  downloads: number;
}

export function registerVersions(program: Command) {
  program
    .command("versions <slug>")
    .description("List skill versions")
    .option("--namespace <ns>", "Namespace", "global")
    .action(async (slug: string, opts: { namespace: string }) => {
      try {
        const token = await requireToken();
        const config = loadConfig();
        const client = new ApiClient({ baseUrl: config.registry, token });
        const versions = await client.get<SkillVersion[]>(
          `/api/v1/skills/${opts.namespace}/${slug}/versions`
        );
        if (!versions || versions.length === 0) {
          console.log("No versions found.");
          return;
        }
        for (const v of versions) {
          info(`v${v.version}`);
          dim(`  ${v.status} · ↓ ${v.downloads} · ${v.createdAt}`);
        }
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exit(1);
      }
    });
}
