import { Command } from "commander";
import { stat, readFile } from "node:fs/promises";
import { basename, resolve } from "node:path";
import { FormData } from "undici";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes, PublishResponse } from "../schema/routes.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { success, error, info } from "../utils/logger.js";
import ora from "ora";
import semver from "semver";

export function registerPublish(program: Command) {
  program
    .command("publish [path]")
    .description("Publish a skill to SkillHub registry")
    .option("--namespace <ns>", "Target namespace (default: global)")
    .option("--slug <slug>", "Skill slug")
    .option("-v, --skill-version <ver>", "Version (semver)")
    .option("--name <name>", "Display name")
    .option("--changelog <text>", "Changelog text")
    .option("--tag <tags>", "Comma-separated tags (e.g. beta,stable)", "latest")
    .action(async (path: string | undefined, opts: Record<string, string>) => {
      const folder = path ? resolve(process.cwd(), path) : process.cwd();
      const folderStat = await stat(folder).catch(() => null);
      if (!folderStat || !folderStat.isDirectory()) {
        error("Path must be a directory containing SKILL.md");
        process.exit(1);
      }

      const slug = opts.slug || basename(folder);
      let version = opts["skill-version"] || opts.ver;
      if (!version) {
        const now = new Date();
        const yyyymmdd = now.getFullYear() * 10000 + (now.getMonth() + 1) * 100 + now.getDate();
        const hhmmss = now.getHours() * 10000 + now.getMinutes() * 100 + now.getSeconds();
        version = `${yyyymmdd}.${hhmmss}`;
      }
      // Allow timestamp format (YYYYMMDD.HHMMSS) or standard semver
      const isValidVersion = semver.valid(version) || /^\d{8}\.\d+$/.test(version);
      if (!isValidVersion) {
        error("--skill-version must be a valid semver (e.g. 1.0.0) or timestamp (e.g. 20260414.123045)");
        process.exit(1);
      }

      const namespace = opts.namespace || "global";
      const changelog = opts.changelog || "";
      const tags = opts.tag.split(",").map((t: string) => t.trim()).filter(Boolean);

      try {
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
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
        const actualNamespace = result.namespace || namespace;
        const isDefaultNamespace = actualNamespace === namespace && !opts.namespace;
        info(`Namespace: ${actualNamespace}${isDefaultNamespace ? " (default)" : ""}`);
        info(`Status:    Published`);
        info(`Tags:      ${tags.join(", ")}`);
        if (changelog) {
          info(`Changelog: ${changelog}`);
        }
      } catch (e: any) {
        error(`Publish failed: ${e.message}`);
        process.exit(1);
      }
    });
}
