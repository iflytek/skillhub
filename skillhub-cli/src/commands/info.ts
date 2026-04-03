import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes } from "../schema/routes.js";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { error, info, dim } from "../utils/logger.js";

export interface SkillDetailResponse {
  id: number;
  namespace: string;
  slug: string;
  displayName: string;
  description: string;
  latestVersion: string;
  visibility: string;
  status: string;
  stars: number;
  downloads: number;
  author: {
    userId: string;
    displayName: string;
  };
  labels: string[];
  createdAt: string;
  updatedAt: string;
}

export function registerInfo(program: Command) {
  program
    .command("info <slug>")
    .alias("view")
    .description("Show skill details")
    .option("--namespace <ns>", "Namespace", "global")
    .action(async (slug: string, opts: { namespace: string }) => {
      const config = loadConfig();
      const token = await readToken();
      const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

      try {
        const skill = await client.get<SkillDetailResponse>(
          `${ApiRoutes.skillDetail.replace("{namespace}", opts.namespace).replace("{slug}", slug)}`
        );
        console.log("");
        info(`${skill.displayName} (${skill.slug})`);
        dim(`Namespace: ${skill.namespace}`);
        dim(`Version:   ${skill.latestVersion}`);
        dim(`Author:    ${skill.author.displayName}`);
        dim(`Stars:     ${skill.stars}  Downloads: ${skill.downloads}`);
        if (skill.description) console.log(`\n${skill.description}`);
        if (skill.labels.length > 0) dim(`Labels:    ${skill.labels.join(", ")}`);
        console.log("");
      } catch (e: any) {
        error(`Skill not found: ${e.message}`);
        process.exit(1);
      }
    });
}
