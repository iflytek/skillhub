import { Command } from "commander";
import { createWriteStream } from "node:fs";
import { resolve } from "node:path";
import { finished } from "node:stream/promises";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes } from "../schema/routes.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { success, error } from "../utils/logger.js";

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
      const { parseSkillNamespace } = await import("../core/skill-resolver.js");
      const { namespace, slug: skillSlug } = parseSkillNamespace(slug, opts.namespace);
      const config = loadConfigFromProgram(program);
      const token = await readToken();
      const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

      const outputDir = opts.output ? resolve(process.cwd(), opts.output) : process.cwd();

      try {
        const spinner = ora(`Downloading ${skillSlug} from ${namespace}`).start();

        let downloadUrl = `${ApiRoutes.skillDownload.replace("{namespace}", namespace).replace("{slug}", skillSlug)}`;
        if (opts.skillVersion) {
          downloadUrl = `/api/v1/skills/${namespace}/${skillSlug}/versions/${opts.skillVersion}/download`;
        } else if (opts.tag) {
          downloadUrl = `/api/v1/skills/${namespace}/${skillSlug}/tags/${opts.tag}/download`;
        }

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
          }
          response = await request(location as string, { method: "GET" });
        }
        const { statusCode, body } = response;

        if (statusCode >= 400) {
          spinner.fail(`Download failed: HTTP ${statusCode}`);
          process.exitCode = 1;
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
