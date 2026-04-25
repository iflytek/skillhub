import { Command } from "commander";
import { stat, readFile } from "node:fs/promises";
import { basename, resolve } from "node:path";
import { FormData } from "undici";
import { existsSync } from "node:fs";
import { discoverSkills } from "../core/skill-discovery.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes } from "../schema/routes.js";
import { info, dim, success, error } from "../utils/logger.js";
import semver from "semver";

interface SyncResult {
  name: string;
  slug: string;
  namespace: string;
  success: boolean;
  message?: string;
}

export function registerSync(program: Command) {
  program
    .command("sync [path]")
    .description("Scan and publish all skills from a directory")
    .option("--namespace <ns>", "Target namespace", "global")
    .option("--all", "Include all skills (even with changes)")
    .option("-y, --yes", "Skip confirmation")
    .action(async (path: string | undefined, opts: { namespace: string; all?: boolean; yes?: boolean }) => {
      const scanPath = path ? resolve(path) : process.cwd();
      
      if (!existsSync(scanPath)) {
        error(`Directory not found: ${scanPath}`);
        process.exitCode = 1;
      }

      try {
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
        const client = new ApiClient({ baseUrl: config.registry, token });

        info(`Scanning ${scanPath} for skills...`);
        const skills = discoverSkills(scanPath);

        if (skills.length === 0) {
          console.log("No skills found. Ensure directories contain SKILL.md files.");
          return;
        }

        console.log("");
        info(`Found ${skills.length} skill(s):`);
        const sortedSkills = [...skills].sort((a, b) => a.name.localeCompare(b.name));
        for (const skill of sortedSkills) {
          console.log(`  - ${skill.name} (${skill.description})`);
        }
        console.log("");

        if (!opts.yes) {
          const { createInterface } = await import("node:readline");
          const rl = createInterface({ input: process.stdin, output: process.stdout });
          const answer = await new Promise<string>((r) =>
            rl.question(`Publish ${skills.length} skill(s) to ${opts.namespace}? [y/N] `, r)
          );
          rl.close();
          if (answer.toLowerCase() !== "y") {
            console.log("Cancelled.");
            return;
          }
        }

        const results: SyncResult[] = [];
        console.log("");

        for (const skill of skills) {
          const slug = skill.name;
          
          try {
            info(`Publishing ${slug}...`);
            
            const version = generateVersion();
            
            const skillMdPath = resolve(skill.dir, "SKILL.md");
            const skillMdStat = await stat(skillMdPath);
            if (!skillMdStat) {
              error(`SKILL.md not found in ${skill.dir}`);
              results.push({ name: skill.name, slug, namespace: opts.namespace, success: false, message: "SKILL.md not found" });
              continue;
            }
            
            const skillMdContent = await readFile(skillMdPath, "utf-8");

            const form = new FormData();
            form.set("payload", JSON.stringify({
              slug,
              displayName: skill.name,
              version,
              changelog: "Synced from local directory",
              acceptLicenseTerms: true,
              tags: ["latest"],
            }));
            const blob = new Blob([Buffer.from(skillMdContent)], { type: "text/markdown" });
            form.append("files", blob, "SKILL.md");

            const publishResponse = await client.postForm<{ ok: boolean; skillId: string; versionId: string }>(
              ApiRoutes.skills,
              form,
              { namespace: opts.namespace }
            );

            if (publishResponse.ok) {
              success(`Published ${slug}@${version}`);
              results.push({ name: skill.name, slug, namespace: opts.namespace, success: true });
            } else {
              error(`Failed to publish ${slug}`);
              results.push({ name: skill.name, slug, namespace: opts.namespace, success: false, message: "Server returned ok=false" });
            }
          } catch (e: any) {
            error(`Failed to publish ${slug}: ${e.message}`);
            results.push({ name: skill.name, slug, namespace: opts.namespace, success: false, message: e.message });
          }
        }

        console.log("");
        info("=== Sync Summary ===");
        const successCount = results.filter((r) => r.success).length;
        const failCount = results.filter((r) => !r.success).length;
        console.log(`  Total: ${results.length}`);
        console.log(`  Success: ${successCount}`);
        console.log(`  Failed: ${failCount}`);
        
        if (failCount > 0) {
          console.log("");
          dim("Failed skills:");
          for (const r of results.filter((r) => !r.success)) {
            console.log(`  - ${r.slug}: ${r.message}`);
          }
        }
        
        console.log("");
      } catch (e: any) {
        error(`Sync failed: ${e.message}`);
        process.exitCode = 1;
      }
    });
}

function generateVersion(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  const hours = String(now.getHours()).padStart(2, "0");
  const minutes = String(now.getMinutes()).padStart(2, "0");
  const seconds = String(now.getSeconds()).padStart(2, "0");
  return `${year}${month}${day}.${hours}${minutes}${seconds}`;
}
