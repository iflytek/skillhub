import { Command } from "commander";
import { success, error, info, warn } from "../utils/logger.js";
import { getAllLockedSkills, getSkillLockPath } from "../core/skill-lock.js";
import { existsSync } from "node:fs";
import { execSync } from "node:child_process";
import { searchMultiselect, cancelSymbol } from "../utils/search-multiselect.js";

function getCliCommand(): string {
  const cliPath = process.argv[1];
  return `node "${cliPath}"`;
}

export function registerUpdate(program: Command) {
  program
    .command("update [skill]")
    .description("Update installed skills from their source")
    .option("-a, --all", "Update all installed skills")
    .option("-g, --global", "Update global scope skills")
    .action(async (slug: string | undefined, opts: Record<string, boolean>) => {
      const lockPath = getSkillLockPath();

      if (!existsSync(lockPath)) {
        error("No skillhub.lock found. Have you installed any skills?");
        process.exitCode = 1;
      }

      const lockedSkills = await getAllLockedSkills();
      const allSkillNames = Object.keys(lockedSkills).sort((a, b) => a.localeCompare(b));

      if (allSkillNames.length === 0) {
        error("No skills in lock file.");
        process.exitCode = 1;
      }

      let skillsToUpdate: string[] = [];

      if (opts.all) {
        skillsToUpdate = allSkillNames;
      } else if (slug) {
        skillsToUpdate = [slug];
      } else {
        const selected = await searchMultiselect({
          message: "Select skills to update",
          items: allSkillNames.map((name) => ({
            value: name,
            label: name,
            hint: lockedSkills[name].sourceType,
          })),
          required: true,
        });

        if (selected === cancelSymbol) {
          console.log("Cancelled.");
          return;
        }

        skillsToUpdate = selected as string[];
      }

      const scope = opts.global ? "--global" : "";
      const cliCmd = getCliCommand();

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
          
          const cmd = `${cliCmd} install ${source} ${scope}`.trim();
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
