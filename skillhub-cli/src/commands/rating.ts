import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { requireToken } from "../core/auth-token.js";
import { success, error, info, dim } from "../utils/logger.js";
import { parseSkillName } from "../core/skill-name.js";

export function registerRating(program: Command) {
  program
    .command("rating <slug>")
    .description("View your rating for a skill")
    .action(async (slug: string) => {
      try {
        const { namespace, slug: skillSlug } = parseSkillName(slug);
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
        const client = new ApiClient({ baseUrl: config.registry, token });

        const detail = await client.get<{ id: number }>(
          `/api/v1/skills/${namespace}/${skillSlug}`
        );

        const rating = await client.get<{ score: number; rated: boolean }>(
          `/api/v1/skills/${detail.id}/rating`
        );

        if (rating.rated) {
          info(`${skillSlug}: ${"★".repeat(rating.score)}${"☆".repeat(5 - rating.score)} (${rating.score}/5)`);
        } else {
          info(`${skillSlug}: Not rated yet`);
          dim("Use: skillhub rate <slug> <score>");
        }
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exit(1);
      }
    });
}

export function registerRate(program: Command) {
  program
    .command("rate <slug> <score>")
    .description("Rate a skill (1-5)")
    .action(async (slug: string, scoreStr: string) => {
      const score = parseInt(scoreStr, 10);
      if (isNaN(score) || score < 1 || score > 5) {
        error("Score must be between 1 and 5");
        process.exit(1);
      }

      try {
        const { namespace, slug: skillSlug } = parseSkillName(slug);
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
        const client = new ApiClient({ baseUrl: config.registry, token });

        const detail = await client.get<{ id: number }>(
          `/api/v1/skills/${namespace}/${skillSlug}`
        );

        await client.put(`/api/v1/skills/${detail.id}/rating`, {
          body: JSON.stringify({ score }),
          headers: { "Content-Type": "application/json" },
        });
        success(`Rated ${skillSlug}: ${"★".repeat(score)}${"☆".repeat(5 - score)}`);
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exit(1);
      }
    });
}
