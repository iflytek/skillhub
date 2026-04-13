import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { loadConfig } from "../core/config.js";
import { requireToken } from "../core/auth-token.js";
import { error, info, dim } from "../utils/logger.js";

export interface MeSkillItem {
  id: number;
  namespace: string;
  slug: string;
  displayName: string;
  status: string;
  starCount: number;
  downloadCount: number;
  headlineVersion?: { version: string };
  publishedVersion?: { version: string };
}

export interface MeSkillsResponse {
  items: MeSkillItem[];
  total: number;
  page: number;
  size: number;
}

export function registerMe(program: Command) {
  const me = program.command("me").description("View your skills and stars");

  me
    .command("skills")
    .alias("ls")
    .description("List your published skills")
    .action(async () => {
      try {
        const token = await requireToken();
        const config = loadConfig();
        const client = new ApiClient({ baseUrl: config.registry, token });
        const resp = await client.get<MeSkillsResponse>("/api/v1/me/skills");
        const skills = resp.items || [];
        const isJson = program.opts().json;
        if (isJson) {
          console.log(JSON.stringify(resp, null, 2));
        } else {
          if (skills.length === 0) {
            console.log("No skills published yet.");
            return;
          }
          for (const s of skills) {
            const version = s.headlineVersion?.version || s.publishedVersion?.version || "unknown";
            info(`${s.displayName} (${s.slug})`);
            dim(`  ${s.namespace} · v${version} · ⭐ ${s.starCount} · ↓ ${s.downloadCount} · ${s.status}`);
          }
        }
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exit(1);
      }
    });

  me
    .command("stars")
    .description("List your starred skills")
    .action(async () => {
      try {
        const token = await requireToken();
        const config = loadConfig();
        const client = new ApiClient({ baseUrl: config.registry, token });
        const resp = await client.get<MeSkillsResponse>("/api/v1/me/stars");
        const skills = resp.items || [];
        const isJson = program.opts().json;
        if (isJson) {
          console.log(JSON.stringify(resp, null, 2));
        } else {
          if (skills.length === 0) {
            console.log("No starred skills.");
            return;
          }
          for (const s of skills) {
            const version = s.headlineVersion?.version || s.publishedVersion?.version || "unknown";
            info(`${s.displayName} (${s.slug})`);
            dim(`  ${s.namespace} · v${version} · ⭐ ${s.starCount}`);
          }
        }
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exit(1);
      }
    });
}
