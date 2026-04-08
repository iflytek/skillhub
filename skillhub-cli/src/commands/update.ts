import { Command } from "commander";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { success, error, info } from "../utils/logger.js";
import { readFile } from "node:fs/promises";
import { resolve, join } from "node:path";
import { existsSync, readdirSync } from "node:fs";
import { getAllAgents, detectInstalledAgents } from "../core/agent-detector.js";

interface SkillLock {
  skills: Record<string, { version: string; installedVersion: string }>;
}

export function registerUpdate(program: Command) {
  program
    .command("update [slug]")
    .alias("up")
    .description("Update installed skills")
    .option("-a, --all", "Update all installed skills")
    .option("-g, --global", "Update global skills")
    .action(async (slug: string | undefined, opts: Record<string, boolean>) => {
      const config = loadConfig();
      const lockPath = join(config.dir || ".skills", "skillhub.lock");

      if (!existsSync(lockPath)) {
        error("No skillhub.lock found. Have you installed any skills?");
        process.exit(1);
      }

      try {
        const lockContent = await readFile(lockPath, "utf-8");
        const lock: SkillLock = JSON.parse(lockContent);
        const skillsToUpdate = opts.all ? Object.keys(lock.skills) : slug ? [slug] : [];

        if (skillsToUpdate.length === 0) {
          if (slug) {
            error(`Skill not found in lock: ${slug}`);
          } else {
            error("No skills specified. Use --all to update all or provide a skill name.");
          }
          process.exit(1);
        }

        let updated = 0;
        for (const name of skillsToUpdate) {
          const entry = lock.skills[name];
          if (!entry) continue;
          info(`Would update ${name} from ${entry.installedVersion} to ${entry.version}`);
          updated++;
        }

        if (updated > 0) {
          success(`Found ${updated} skill(s) with updates`);
          info("Note: Full update implementation requires re-downloading from registry");
        } else {
          info("All skills are up to date");
        }
      } catch (e: any) {
        error(`Update failed: ${e.message}`);
        process.exit(1);
      }
    });
}
