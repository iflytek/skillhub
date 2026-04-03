import { Command } from "commander";
import { stat, readFile } from "node:fs/promises";
import { basename, resolve } from "node:path";
import { FormData } from "undici";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes, PublishResponse } from "../schema/routes.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig } from "../core/config.js";
import { success, error, info } from "../utils/logger.js";
import ora from "ora";
import semver from "semver";

export function registerPublish(program: Command) {
  program
    .command("publish [path]")
    .description("Publish a skill to SkillHub registry")
    .option("--namespace <ns>", "Target namespace (default: global)")
    .option("--slug <slug>", "Skill slug")
    .option("--version <ver>", "Version (semver)")
    .option("--name <name>", "Display name")
    .option("--changelog <text>", "Changelog text")
    .option("--tags <tags>", "Comma-separated tags", "latest")
    .action(async (path: string | undefined, opts: Record<string, string>) => {
      const folder = path ? resolve(process.cwd(), path) : process.cwd();
      const folderStat = await stat(folder).catch(() => null);
      if (!folderStat || !folderStat.isDirectory()) {
        error("Path must be a directory containing SKILL.md");
        process.exit(1);
      }

      const slug = opts.slug || basename(folder);
      const version = opts.version;
      if (!version || !semver.valid(version)) {
        error("--version must be a valid semver (e.g. 1.0.0)");
        process.exit(1);
      }

      const namespace = opts.namespace || "global";
      const changelog = opts.changelog || "";
      const tags = opts.tags.split(",").map((t) => t.trim()).filter(Boolean);

      try {
        const token = await requireToken();
        const config = loadConfig();
        const client = new ApiClient({ baseUrl: config.registry, token });

        const spinner = ora(`Publishing ${slug}@${version} to ${namespace}`).start();

        const skillMdPath = resolve(folder, "SKILL.md");
        const skillMdStat = await stat(skillMdPath).catch(() => null);
        if (!skillMdStat) {
          spinner.fail("SKILL.md not found in directory");
          process.exit(1);
        }

        const skillMdContent = await readFile(skillMdPath, "utf-8");

        const form = new FormData();
        form.set("payload", JSON.stringify({
          slug,
          displayName: opts.name || slug,
          version,
          changelog,
          acceptLicenseTerms: true,
          tags,
        }));
        const blob = new Blob([Buffer.from(skillMdContent)], { type: "text/markdown" });
        form.append("files", blob, "SKILL.md");

        const result = await client.postForm<PublishResponse>(
          ApiRoutes.skills,
          form,
          { namespace }
        );

        spinner.succeed(`Published ${slug}@${version} (${result.skillId})`);
        info(`Namespace: ${result.namespace}`);
        info(`Status:    ${result.status}`);
      } catch (e: any) {
        error(`Publish failed: ${e.message}`);
        process.exit(1);
      }
    });
}
