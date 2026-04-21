import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
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

interface VersionSearchResult {
  namespace: string;
  name: string;
  exists: boolean;
}

async function resolveWithVersion(
  client: ApiClient,
  namespace: string,
  slug: string,
  version: string
): Promise<ResolveResponse | null> {
  try {
    const result = await client.get<ResolveResponse>(
      `/api/v1/skills/${namespace}/${slug}/resolve?version=${version}`
    );
    return result;
  } catch (e: any) {
    const status = e.status || e.statusCode;
    if (status === 404 || status === 400) return null;
    throw e;
  }
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
        const config = loadConfigFromProgram(program);
        const token = await readToken();
        const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

        let targetNamespace = namespace;
        let targetSlug = skillSlug;
        // Strip 'v' prefix if present (both 1.0.0 and v1.0.0 should work)
        const specifiedVersion = opts.skillVersion?.replace(/^v/, "");

        // Case 1: User specified a version
        if (specifiedVersion) {
          if (namespace && namespace !== "global") {
            const result = await resolveWithVersion(client, namespace, skillSlug, specifiedVersion);
            if (result) {
              printResolveResult(result);
              return;
            }
            error(`Version ${specifiedVersion} not found for ${namespace}/${skillSlug}`);
            error(`Please check if the version number is correct.`);
            process.exitCode = 1;
          }

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

          const resolvePromises = uniqueResults.map(async (r) => ({
            ...r,
            result: await resolveWithVersion(client, r.namespace, r.name, specifiedVersion),
          }));
          const resolvedResults = await Promise.all(resolvePromises);
          const matches = resolvedResults.filter((r) => r.result !== null);

          if (matches.length === 0) {
            error(`Version ${specifiedVersion} not found for ${skillSlug}`);
            error(`Please check if the version number is correct.`);
            process.exitCode = 1;
          }

          if (matches.length === 1) {
            // Only one match, auto-select
            printResolveResult(matches[0].result!);
            return;
          }

          // Multiple matches, list them for user to choose manually
          info(`Found multiple skills with version ${specifiedVersion}:`);
          for (const m of matches) {
            console.log(`  ${m.namespace}/${m.name}`);
          }
          dim(`\nUse: resolve <namespace>/<skill> --skill-version ${specifiedVersion}`);
          process.exitCode = 1;
        }

        // Case 2: No version specified (original behavior)
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

        const params = new URLSearchParams();
        if (opts.tag) {
          params.set("tag", opts.tag);
        }
        if (opts.hash) params.set("hash", opts.hash);

        const qs = params.toString();
        const path = `/api/v1/skills/${targetNamespace}/${targetSlug}/resolve${qs ? "?" + qs : ""}`;
        const result = await client.get<ResolveResponse>(path);
        printResolveResult(result);
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exitCode = 1;
      }
    });
}

function printResolveResult(result: ResolveResponse) {
  info(`${result.slug}@${result.version}`);
  dim(`Namespace:    ${result.namespace}`);
  dim(`Version ID:   ${result.versionId}`);
  dim(`Fingerprint:  ${result.fingerprint}`);
  dim(`Matched:      ${result.matched}`);
  dim(`Download URL: ${result.downloadUrl}`);
}
