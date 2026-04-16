import { Command } from "commander";
import { createWriteStream } from "node:fs";
import { resolve } from "node:path";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes } from "../schema/routes.js";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { success, error } from "../utils/logger.js";
import { parseSkillName } from "../core/skill-name.js";
import ora from "ora";

export function registerDownload(program: Command) {
  program
    .command("download <slug>")
    .description("Download a skill package to local directory")
    .option("-v, --skill-version <ver>", "Specific version")
    .option("--tag <tag>", "Tag to download", "latest")
    .option("--output <dir>", "Output directory")
    .action(async (slug: string, opts: Record<string, string>) => {
      const { namespace, slug: skillSlug } = parseSkillName(slug);
      const config = loadConfig();
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
            process.exit(1);
          }
          response = await request(location as string, { method: "GET" });
        }
        const { statusCode, body } = response;

        if (statusCode >= 400) {
          spinner.fail(`Download failed: HTTP ${statusCode}`);
          process.exit(1);
        }

        const outPath = resolve(outputDir, `${skillSlug}.zip`);
        const fileStream = createWriteStream(outPath);
        await body.pipe(fileStream);

        spinner.succeed(`Downloaded ${skillSlug} to ${outPath}`);
      } catch (e: any) {
        error(`Download failed: ${e.message}`);
        process.exit(1);
      }
    });
}
