import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { success, error, info, dim } from "../utils/logger.js";
import { parseSkillName } from "../core/skill-name.js";

export interface ResolveResponse {
  skillId: number;
  namespace: string;
  slug: string;
  version: string;
  versionId: number;
  fingerprint: string;
  matched: string;
  downloadUrl: string;
}

export function registerResolve(program: Command) {
  program
    .command("resolve <slug>")
    .description("Resolve the latest version of a skill")
    .option("--version <ver>", "Specific version")
    .option("--tag <tag>", "Tag to resolve", "latest")
    .option("--hash <hash>", "Content hash")
    .action(async (slug: string, opts: Record<string, string>) => {
      try {
        const { namespace, slug: skillSlug } = parseSkillName(slug);
        const config = loadConfig();
        const token = await readToken();
        const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

        const params = new URLSearchParams();
        if (opts.version) params.set("version", opts.version);
        if (opts.tag) params.set("tag", opts.tag);
        if (opts.hash) params.set("hash", opts.hash);

        const qs = params.toString();
        const path = `/api/v1/skills/${namespace}/${skillSlug}/resolve${qs ? "?" + qs : ""}`;
        const result = await client.get<ResolveResponse>(path);

        info(`${result.slug}@${result.version}`);
        dim(`Namespace:    ${result.namespace}`);
        dim(`Version ID:   ${result.versionId}`);
        dim(`Fingerprint:  ${result.fingerprint}`);
        dim(`Matched:      ${result.matched}`);
        dim(`Download URL: ${result.downloadUrl}`);
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exit(1);
      }
    });
}
