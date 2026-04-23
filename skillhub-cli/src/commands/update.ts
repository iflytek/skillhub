import { Command } from "commander";
import { success, error, info, warn, dim } from "../utils/logger.js";
import { getAllLockedSkills, getSkillLockPath, type SkillLockEntry } from "../core/skill-lock.js";
import { existsSync } from "node:fs";
import { execSync } from "node:child_process";
import { searchMultiselect, cancelSymbol } from "../utils/search-multiselect.js";
import { ApiClient } from "../core/api-client.js";
import { loadConfigFromProgram } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import * as p from "@clack/prompts";
import ora from "ora";

function getCliCommand(): string {
  const cliPath = process.argv[1];
  return `node "${cliPath}"`;
}

interface UpdateInfo {
  name: string;
  currentVersion: string;
  latestVersion: string;
  namespace: string;
  slug: string;
  source: string;
  sourceType: string;
  hasUpdate: boolean;
}

export function registerUpdate(program: Command) {
  program
    .command("update [skill]")
    .description("Update installed skills from their source")
    .option("-a, --all", "Update all installed skills")
    .option("-g, --global", "Update global scope skills")
    .option("-y, --yes", "Skip confirmation and update all outdated skills")
    .action(async (slug: string | undefined, opts: Record<string, boolean>) => {
      const lockPath = getSkillLockPath();

      if (!existsSync(lockPath)) {
        error("No skillhub.lock found. Have you installed any skills?");
        process.exitCode = 1;
        return;
      }

      const lockedSkills = await getAllLockedSkills();
      const allSkillNames = Object.keys(lockedSkills).sort((a, b) => a.localeCompare(b));

      if (allSkillNames.length === 0) {
        error("No skills in lock file.");
        process.exitCode = 1;
        return;
      }

      const config = loadConfigFromProgram(program);
      const token = await readToken();
      const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

      let skillsToCheck: string[] = [];

      if (opts.all) {
        skillsToCheck = allSkillNames;
      } else if (slug) {
        if (!lockedSkills[slug]) {
          error(`Skill not found in lock file: ${slug}`);
          error(`Installed skills: ${allSkillNames.join(", ")}`);
          process.exitCode = 1;
          return;
        }
        skillsToCheck = [slug];
      } else {
        const selected = await searchMultiselect({
          message: "Select skills to check for updates",
          items: allSkillNames.map((name) => ({
            value: name,
            label: name,
            hint: `${lockedSkills[name].namespace}/${lockedSkills[name].slug} @ ${lockedSkills[name].version}`,
          })),
          required: true,
        });

        if (selected === cancelSymbol) {
          console.log("Cancelled.");
          return;
        }

        skillsToCheck = selected as string[];
      }

      const spinner = ora("Checking for updates...").start();
      const updates: UpdateInfo[] = [];
      const upToDate: string[] = [];
      const checkFailed: string[] = [];

      for (const name of skillsToCheck) {
        const entry = lockedSkills[name];
        if (!entry) continue;

        if (entry.sourceType !== "registry") {
          checkFailed.push(name);
          continue;
        }

        try {
          const versionsResp = await client.get<{ items: Array<{ version: string }> }>(
            `/api/v1/skills/${entry.namespace}/${entry.slug}/versions`
          );
          const versions = versionsResp.items || [];
          
          if (versions.length === 0) {
            checkFailed.push(name);
            continue;
          }

          const latestVersion = versions[0].version;
          const currentVersion = entry.version;

          if (latestVersion === currentVersion) {
            upToDate.push(name);
          } else {
            updates.push({
              name,
              currentVersion,
              latestVersion,
              namespace: entry.namespace,
              slug: entry.slug,
              source: entry.source,
              sourceType: entry.sourceType,
              hasUpdate: true,
            });
          }
        } catch (e: any) {
          checkFailed.push(name);
        }
      }

      spinner.stop();

      if (upToDate.length > 0) {
        console.log("");
        info(`Up to date (${upToDate.length}):`);
        for (const name of upToDate) {
          dim(`  ✓ ${name} @ ${lockedSkills[name].version}`);
        }
      }

      if (checkFailed.length > 0) {
        console.log("");
        warn(`Check failed (${checkFailed.length}):`);
        for (const name of checkFailed) {
          dim(`  ✗ ${name}`);
        }
      }

      if (updates.length === 0) {
        console.log("");
        success("All skills are up to date!");
        return;
      }

      console.log("");
      info(`Updates available (${updates.length}):`);
      for (const u of updates) {
        console.log(`  ↑ ${u.name}: ${u.currentVersion} → ${u.latestVersion}`);
      }

      let skillsToUpdate = updates;

      if (!opts.yes && !opts.all && !slug) {
        const selected = await searchMultiselect({
          message: "Select skills to update",
          items: updates.map((u) => ({
            value: u.name,
            label: u.name,
            hint: `${u.currentVersion} → ${u.latestVersion}`,
          })),
          required: true,
        });

        if (selected === cancelSymbol) {
          console.log("Cancelled.");
          return;
        }

        skillsToUpdate = updates.filter((u) => (selected as string[]).includes(u.name));
      }

      if (!opts.yes) {
        const confirmed = await p.confirm({
          message: `Update ${skillsToUpdate.length} skill(s)?`,
        });

        if (p.isCancel(confirmed) || !confirmed) {
          console.log("Cancelled.");
          return;
        }
      }

      const scope = opts.global ? "--global" : "";
      const cliCmd = getCliCommand();

      let updated = 0;
      let failed = 0;

      for (const info of skillsToUpdate) {
        try {
          info(`Updating ${info.name} from ${info.currentVersion} to ${info.latestVersion}...`);
          const cmd = `${cliCmd} install ${info.namespace}/${info.slug} --skill-version ${info.latestVersion} ${scope}`.trim();
          execSync(cmd, { stdio: "inherit" });
          updated++;
        } catch (e: any) {
          error(`Failed to update ${info.name}: ${e.message}`);
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
