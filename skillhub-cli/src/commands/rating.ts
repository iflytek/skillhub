import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { requireToken } from "../core/auth-token.js";
import { success, error, info, dim } from "../utils/logger.js";
export function registerRating(program: Command) {
  program
    .command("rating")
    .description("View your rating for a skill")
    .argument("<skill>", "Skill name or namespace/skill-name")
    .option("--namespace <ns>", "Override namespace (default: parsed from skill or 'global')")
    .action(async (slug: string, opts: { namespace?: string }) => {
      try {
        const { parseSkillNamespace } = await import("../core/skill-resolver.js");
        const { namespace, slug: skillSlug } = parseSkillNamespace(slug, opts.namespace);
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
          dim("Use: skillhub rate <skill> <score>");
        }
      } catch (e: any) {
        const status = e.status || e.statusCode;
        if (status === 404) {
          error(`Skill not found: ${namespace}/${skillSlug}`);
          if (!slug.includes("/")) {
            dim("Tip: Use namespace/skill-name format, e.g., vision2group/docker-build-push");
          }
        } else {
          error(`Failed: ${e.message}`);
        }
        process.exitCode = 1;
      }
    });
}

export function registerRate(program: Command) {
  program
    .command("rate")
    .description("Rate a skill (1-5)")
    .argument("<skill>", "Skill name or namespace/skill-name")
    .argument("<score>", "Rating score (1-5)")
    .option("--namespace <ns>", "Override namespace (default: parsed from skill or 'global')")
    .action(async (slug: string, scoreStr: string, opts: { namespace?: string }) => {
      const score = parseInt(scoreStr, 10);
      if (isNaN(score) || score < 1 || score > 5) {
        error("Score must be between 1 and 5");
        process.exitCode = 1;
      }

      try {
        const { parseSkillNamespace } = await import("../core/skill-resolver.js");
        const { namespace, slug: skillSlug } = parseSkillNamespace(slug, opts.namespace);
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
        const status = e.status || e.statusCode;
        if (status === 404) {
          error(`Skill not found: ${namespace}/${skillSlug}`);
          if (!slug.includes("/")) {
            dim("Tip: Use namespace/skill-name format, e.g., vision2group/docker-build-push");
          }
        } else {
          error(`Failed: ${e.message}`);
        }
        process.exitCode = 1;
      }
    });
}
