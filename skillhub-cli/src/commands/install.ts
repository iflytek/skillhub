import { Command } from "commander";
import { mkdtemp, rm, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createWriteStream, existsSync, mkdirSync } from "node:fs";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes } from "../schema/routes.js";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { discoverSkills } from "../core/skill-discovery.js";
import { installSkill } from "../core/installer.js";
import { getAllAgents, detectInstalledAgents } from "../core/agent-detector.js";
import { success, error, info } from "../utils/logger.js";
import ora from "ora";
import { execSync } from "node:child_process";

export function registerInstall(program: Command) {
  program
    .command("install <slug>")
    .alias("i")
    .description("Install a skill from SkillHub registry")
    .option("--namespace <ns>", "Namespace", "global")
    .option("-a, --agent <agents...>", "Target specific agents")
    .option("-g, --global", "Install to global scope")
    .option("-y, --yes", "Skip prompts")
    .option("--copy", "Copy instead of symlink")
    .action(async (slug: string, opts: Record<string, string | string[] | boolean>) => {
      const spinner = ora(`Fetching ${slug}`).start();
      const ns = opts.namespace as string || "global";

      try {
        const config = loadConfig();
        const token = await readToken();
        const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

        const downloadUrl = `/api/v1/skills/${ns}/${slug}/download`;
        const tmpDir = await mkdtemp(join(tmpdir(), "skillhub-install-"));
        const zipPath = join(tmpDir, `${slug}.zip`);

        const { request } = await import("undici");
        const url = new URL(downloadUrl, config.registry);
        const { statusCode, body } = await request(url.toString(), {
          method: "GET",
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });

        if (statusCode >= 400) {
          spinner.fail(`Skill not found: ${slug}`);
          process.exit(1);
        }

        spinner.text = "Downloading";
        const { createWriteStream } = await import("node:fs");
        const fileStream = createWriteStream(zipPath);
        await body.pipe(fileStream);

        spinner.text = "Extracting";
        const extractDir = join(tmpDir, "extracted");
        mkdirSync(extractDir, { recursive: true });
        execSync(`unzip -o "${zipPath}" -d "${extractDir}"`, { stdio: "pipe" });

        const skills = discoverSkills(extractDir);
        if (skills.length === 0) {
          spinner.fail("No SKILL.md found in package");
          process.exit(1);
        }

        spinner.succeed(`Found ${skills.length} skill(s) in ${slug}`);

        const targetAgents = opts.agent
          ? getAllAgents().filter((a) => (opts.agent as string[]).includes(a.key))
          : detectInstalledAgents();

        if (targetAgents.length === 0) {
          const claude = getAllAgents().find((a) => a.key === "claude-code");
          if (claude) targetAgents.push(claude);
        }

        const mode = opts.copy ? ("copy" as const) : ("symlink" as const);
        const isGlobal = !!opts.global;

        let installed = 0;
        for (const skill of skills) {
          for (const agent of targetAgents) {
            const result = installSkill(
              skill.dir,
              skill.name,
              agent.key,
              isGlobal ? agent.globalPath : agent.projectPath,
              mode,
              isGlobal,
            );
            if (result.success) installed++;
          }
        }

        success(`Installed ${installed} skill(s) from ${slug}`);
        await rm(tmpDir, { recursive: true, force: true });
      } catch (e: any) {
        spinner.fail(e.message);
        process.exit(1);
      }
    });
}
