import { Command } from "commander";
import { success, error, info, warn } from "../utils/logger.js";
import { getAllLockedSkills, getSkillLockPath } from "../core/skill-lock.js";
import { existsSync } from "node:fs";
import { execSync } from "node:child_process";

export function registerUpdate(program: Command) {
  program
    .command("update [slug]")
    .alias("up")
    .description("Update installed skills from their source")
    .option("-a, --all", "Update all installed skills")
    .option("-g, --global", "Update global scope skills")
    .action(async (slug: string | undefined, opts: Record<string, boolean>) => {
      const lockPath = getSkillLockPath();

      if (!existsSync(lockPath)) {
        error("No skillhub.lock found. Have you installed any skills?");
        process.exit(1);
      }

      const lockedSkills = await getAllLockedSkills();
      const skillsToUpdate = opts.all ? Object.keys(lockedSkills) : slug ? [slug] : [];

      if (skillsToUpdate.length === 0) {
        if (slug) {
          error(`Skill not found in lock: ${slug}`);
        } else {
          error("No skills specified. Use --all to update or provide a skill name.");
        }
        process.exit(1);
      }

      const scope = opts.global ? "--global" : "";

      let updated = 0;
      let failed = 0;

      for (const name of skillsToUpdate) {
        const entry = lockedSkills[name];
        if (!entry) {
          warn(`Skill not found in lock: ${name}`);
          continue;
        }

        try {
          info(`Updating ${name} from ${entry.source}...`);
          const source = entry.sourceType === "registry" 
            ? entry.source 
            : entry.sourceUrl;
          
          const cmd = `skillhub install ${source} ${scope}`.trim();
          execSync(cmd, { stdio: "inherit" });
          updated++;
        } catch (e: any) {
          error(`Failed to update ${name}: ${e.message}`);
          failed++;
        }
      }

      console.log("");
      if (failed === 0) {
        success(`Updated ${updated} skill(s)`);
      } else {
        warn(`Updated ${updated}, failed ${failed}`);
      }
    });
}
