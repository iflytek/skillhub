import { Command } from "commander";
import { createWriteStream } from "node:fs";
import { resolve } from "node:path";
import { finished } from "node:stream/promises";
import { ApiClient } from "../core/api-client.js";
import { loadConfigFromProgram } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { success, error } from "../utils/logger.js";
import * as p from "@clack/prompts";

import ora from "ora";

function buildDownloadHelp(cmd: Command): string {
  const lines: string[] = [];
  const BOLD = "\x1b[1m";
  const RESET = "\x1b[0m";
  const CYAN = "\x1b[36m";
  const DIM = "\x1b[38;5;102m";

  lines.push(`${BOLD}Usage:${RESET} skillhub download [options] <skill>`);
  lines.push("");
  lines.push("Download a skill package as a .zip file");
  lines.push("");

  lines.push(`${BOLD}Arguments:${RESET}`);
  lines.push(`  ${CYAN}skill${RESET}                      Skill name or namespace/skill-name`);
  lines.push("");

  lines.push(`${BOLD}Options:${RESET}`);
  lines.push(`  ${CYAN}-v, --skill-version <ver>${RESET}  Specific version to download`);
  lines.push(`  ${CYAN}--tag <tag>${RESET}                Tag to download (default: "latest")`);
  lines.push(`  ${CYAN}--output <dir>${RESET}             Output directory (default: current directory)`);
  lines.push(`  ${CYAN}--namespace <ns>${RESET}           Override namespace (default: parsed from skill or 'global')`);
  lines.push(`  ${CYAN}-h, --help${RESET}                 Display help for command`);
  lines.push("");

  lines.push(`${BOLD}Examples:${RESET}`);
  lines.push(`${DIM}  skillhub download docker-build-push${RESET}`);
  lines.push(`${DIM}  skillhub download vision2group/docker-build-push${RESET}`);
  lines.push(`${DIM}  skillhub download docker-build-push --output ./skills${RESET}`);
  lines.push(`${DIM}  skillhub download docker-build-push --skill-version 1.0.0${RESET}`);
  lines.push(`${DIM}  skillhub download docker-build-push --tag v1.0.0${RESET}`);

  return lines.join("\n");
}

export function registerDownload(program: Command) {
  const downloadCmd = program
    .command("download")
    .description("Download a skill package as a .zip file")
    .argument("<skill>", "Skill name or namespace/skill-name")
    .option("-v, --skill-version <ver>", "Specific version")
    .option("--tag <tag>", "Tag to download", "latest")
    .option("--output <dir>", "Output directory")
    .option("--namespace <ns>", "Override namespace (default: parsed from skill or 'global')");

  downloadCmd.helpInformation = () => buildDownloadHelp(downloadCmd);

  downloadCmd.action(async (slug: string, opts: Record<string, string>) => {
      const { resolveSkillNamespace, parseSkillNamespace } = await import("../core/skill-resolver.js");
      const config = loadConfigFromProgram(program);
      const token = await readToken();
      const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

      const outputDir = opts.output ? resolve(process.cwd(), opts.output) : process.cwd();

      try {
        let namespace: string;
        let skillSlug: string;

        if (opts.namespace || slug.includes("/")) {
          const parsed = parseSkillNamespace(slug, opts.namespace);
          namespace = parsed.namespace;
          skillSlug = parsed.slug;
        } else {
          const spinner = ora(`Searching for ${slug}`).start();
          try {
            const resolved = await resolveSkillNamespace(client, slug);
            namespace = resolved.namespace;
            skillSlug = resolved.slug;
            spinner.succeed(`Found ${namespace}/${skillSlug}`);
          } catch (e: any) {
            spinner.fail(e.message);
            process.exitCode = 1;
            return;
          }
        }

        const spinner = ora(`Downloading ${skillSlug} from ${namespace}`).start();

        let selectedVersion: string;
        if (opts.skillVersion) {
          selectedVersion = opts.skillVersion;
        } else if (opts.tag && opts.tag !== "latest") {
          spinner.text = `Resolving tag ${opts.tag}`;
          try {
            const tagsResp = await client.get<Array<{ tagName: string; version: string }>>(
              `/api/v1/skills/${namespace}/${skillSlug}/tags`
            );
            const tags = tagsResp || [];
            const matchedTag = tags.find((t) => t.tagName === opts.tag);
            if (matchedTag) {
              selectedVersion = matchedTag.version;
            } else {
              spinner.fail(`Tag not found: ${opts.tag}`);
              process.exitCode = 1;
              return;
            }
          } catch (e: any) {
            spinner.fail(`Failed to fetch tags: ${e.message}`);
            process.exitCode = 1;
            return;
          }
        } else {
          spinner.stop();
          try {
            const versionsResp = await client.get<{ items: Array<{ version: string; publishedAt: string }> }>(
              `/api/v1/skills/${namespace}/${skillSlug}/versions`
            );
            const versions = versionsResp.items || [];
            if (versions.length === 0) {
              error(`No versions found for ${namespace}/${skillSlug}`);
              process.exitCode = 1;
              return;
            }
            if (versions.length === 1) {
              selectedVersion = versions[0].version;
            } else {
              const picked = await p.select({
                message: "Select version to download",
                options: versions.map((v) => ({
                  value: v.version,
                  label: `v${v.version}`,
                  hint: new Date(v.publishedAt).toLocaleDateString(),
                })),
              });
              if (p.isCancel(picked)) {
                console.log("Cancelled.");
                return;
              }
              selectedVersion = picked as string;
            }
            spinner.start(`Downloading ${skillSlug}@${selectedVersion}`);
          } catch (e: any) {
            error(`Failed to fetch versions: ${e.message}`);
            process.exitCode = 1;
            return;
          }
        }

        const downloadUrl = `${config.registry.replace(/\/$/, "")}/api/v1/skills/${namespace}/${skillSlug}/versions/${selectedVersion}/download`;

        const { request } = await import("undici");
        const url = new URL(downloadUrl, config.registry);
        let response = await request(url.toString(), {
          method: "GET",
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        if (response.statusCode === 302 || response.statusCode === 307 || response.statusCode === 308) {
          const location = response.headers.location;
          if (!location) {
            spinner.fail(`Redirect response has no Location header`);
            process.exitCode = 1;
            return;
          }
          response = await request(location as string, { method: "GET" });
        }
        const { statusCode, body } = response;

        if (statusCode >= 400) {
          spinner.fail(`Download failed: HTTP ${statusCode}`);
          if (statusCode === 503) {
            error("\n💡 Service Unavailable (503). This could mean:");
            error("   - The server is temporarily overloaded or under maintenance");
            error("   - The storage service is unavailable");
            error("   - Network connectivity issues");
            error("\nSuggestions:");
            error("   - Wait a moment and try again");
            error("   - Check your internet connection");
            error("   - Contact your administrator if the problem persists");
          } else if (statusCode === 404) {
            error(`\n💡 Skill version not found: ${namespace}/${skillSlug}@${selectedVersion}`);
            error("   - Try listing available versions: skillhub inspect " + slug);
          } else if (statusCode === 403) {
            error("\n💡 Access denied. You may not have permission to download this skill.");
            error("   - Try: skillhub login");
          }
          process.exitCode = 1;
          return;
        }

        const outPath = resolve(outputDir, `${skillSlug}.zip`);
        const fileStream = createWriteStream(outPath);
        await finished(body.pipe(fileStream));

        spinner.succeed(`Downloaded ${skillSlug} to ${outPath}`);
      } catch (e: any) {
        error(`Download failed: ${e.message}`);
        process.exitCode = 1;
      }
    });
}
