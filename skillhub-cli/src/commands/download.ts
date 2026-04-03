import { Command } from "commander";
import { createWriteStream } from "node:fs";
import { mkdir, stat } from "node:fs/promises";
import { basename, resolve } from "node:path";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes } from "../schema/routes.js";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { success, error, info } from "../utils/logger.js";
import ora from "ora";

export function registerDownload(program: Command) {
  program
    .command("download <slug>")
    .description("Download a skill package to local directory")
    .option("--namespace <ns>", "Namespace", "global")
    .option("--version <ver>", "Specific version")
    .option("--tag <tag>", "Tag to download", "latest")
    .option("--output <dir>", "Output directory")
    .action(async (slug: string, opts: Record<string, string>) => {
      const config = loadConfig();
      const token = await readToken();
      const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

      const ns = opts.namespace || "global";
      const outputDir = opts.output ? resolve(process.cwd(), opts.output) : process.cwd();

      try {
        const spinner = ora(`Downloading ${slug} from ${ns}`).start();

        let downloadUrl = `${ApiRoutes.skillDownload.replace("{namespace}", ns).replace("{slug}", slug)}`;
        if (opts.version) {
          downloadUrl = `/api/v1/skills/${ns}/${slug}/versions/${opts.version}/download`;
        } else if (opts.tag) {
          downloadUrl = `/api/v1/skills/${ns}/${slug}/tags/${opts.tag}/download`;
        }

        const { request } = await import("undici");
        const url = new URL(downloadUrl, config.registry);
        const { statusCode, body } = await request(url.toString(), {
          method: "GET",
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });

        if (statusCode >= 400) {
          spinner.fail(`Download failed: HTTP ${statusCode}`);
          process.exit(1);
        }

        const outPath = resolve(outputDir, `${slug}.zip`);
        const fileStream = createWriteStream(outPath);
        await body.pipe(fileStream);

        spinner.succeed(`Downloaded ${slug} to ${outPath}`);
      } catch (e: any) {
        error(`Download failed: ${e.message}`);
        process.exit(1);
      }
    });
}
