import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { loadConfig } from "../core/config.js";
import { requireToken } from "../core/auth-token.js";
import { success, error, info, dim } from "../utils/logger.js";

export function registerRating(program: Command) {
  program
    .command("rating <slug>")
    .description("View your rating for a skill")
    .option("--namespace <ns>", "Namespace", "global")
    .action(async (slug: string, opts: { namespace: string }) => {
      try {
        const token = await requireToken();
        const config = loadConfig();
        const client = new ApiClient({ baseUrl: config.registry, token });

        const detail = await client.get<{ id: number }>(
          `/api/v1/skills/${opts.namespace}/${slug}`
        );

        const rating = await client.get<{ score: number; rated: boolean }>(
          `/api/v1/skills/${detail.id}/rating`
        );

        if (rating.rated) {
          info(`${slug}: ${"★".repeat(rating.score)}${"☆".repeat(5 - rating.score)} (${rating.score}/5)`);
        } else {
          info(`${slug}: Not rated yet`);
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
    .option("--namespace <ns>", "Namespace", "global")
    .action(async (slug: string, scoreStr: string, opts: { namespace: string }) => {
      const score = parseInt(scoreStr, 10);
      if (isNaN(score) || score < 1 || score > 5) {
        error("Score must be between 1 and 5");
        process.exit(1);
      }

      try {
        const token = await requireToken();
        const config = loadConfig();
        const client = new ApiClient({ baseUrl: config.registry, token });

        const detail = await client.get<{ id: number }>(
          `/api/v1/skills/${opts.namespace}/${slug}`
        );

        await client.put(`/api/v1/skills/${detail.id}/rating`, {
          body: JSON.stringify({ score }),
          headers: { "Content-Type": "application/json" },
        });
        success(`Rated ${slug}: ${"★".repeat(score)}${"☆".repeat(5 - score)}`);
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exit(1);
      }
    });
}
