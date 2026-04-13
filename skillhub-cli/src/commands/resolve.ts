import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { success, error, info, dim } from "../utils/logger.js";
import { parseSkillName } from "../core/skill-name.js";
import { runInteractiveSearch, searchSkills } from "../core/interactive-search.js";

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
    .option("-v, --skill-version <ver>", "Specific version")
    .option("--tag <tag>", "Tag to resolve (default: latest, ignored if --skill-version)")
    .option("--hash <hash>", "Content hash")
    .action(async (slug: string, opts: Record<string, string>) => {
      try {
        const { namespace, slug: skillSlug } = parseSkillName(slug);
        const config = loadConfig();
        const token = await readToken();
        const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

        let targetNamespace = namespace;
        let targetSlug = skillSlug;

        if (namespace === "global") {
          const results = await searchSkills(client, skillSlug, 50);

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
            error(`Skill not found: ${skillSlug}`);
            process.exit(1);
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

        const params = new URLSearchParams();
        if (opts["skill-version"]) {
          params.set("version", opts["skill-version"]);
        } else if (opts.tag) {
          params.set("tag", opts.tag);
        }
        if (opts.hash) params.set("hash", opts.hash);

        const qs = params.toString();
        const path = `/api/v1/skills/${targetNamespace}/${targetSlug}/resolve${qs ? "?" + qs : ""}`;
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
