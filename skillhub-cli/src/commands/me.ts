import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
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
  const me = program.command("me").description("View your profile information");

  me
    .command("skills")
    .alias("ls")
    .description("List your published skills")
    .action(async () => {
      try {
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
        const client = new ApiClient({ baseUrl: config.registry, token, debug: program.opts().debug });
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
        process.exitCode = 1;
      }
    });

  me
    .command("stars")
    .description("List your starred skills")
    .action(async () => {
      try {
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
        const client = new ApiClient({ baseUrl: config.registry, token, debug: program.opts().debug });
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
        process.exitCode = 1;
      }
    });

  me
    .command("namespaces")
    .description("List namespaces you have access to")
    .action(async () => {
      try {
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
        const client = new ApiClient({ baseUrl: config.registry, token, debug: program.opts().debug });
        const namespaces = await client.get<{ slug: string; displayName: string; currentUserRole: string; status: string }[]>("/api/v1/me/namespaces");
        const isJson = program.opts().json;
        if (isJson) {
          console.log(JSON.stringify(namespaces, null, 2));
        } else {
          if (!namespaces || namespaces.length === 0) {
            console.log("No namespaces found.");
            return;
          }
          for (const ns of namespaces) {
            console.log(`${ns.slug} — ${ns.displayName} [${ns.currentUserRole}] (${ns.status})`);
          }
        }
      } catch (e: any) {
        error(`Failed to list namespaces: ${e.message}`);
        process.exitCode = 1;
      }
    });
}
