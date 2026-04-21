import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { success, error, info, dim } from "../utils/logger.js";

export interface ReviewSubmission {
  id: number;
  skillSlug: string;
  skillDisplayName: string;
  namespace: string;
  version: string;
  status: string;
  createdAt: string;
}

export function registerReviews(program: Command) {
  const reviews = program.command("reviews").description("Manage skill reviews");

  reviews
    .command("my")
    .alias("submissions")
    .description("List your review submissions")
    .action(async () => {
      try {
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
        const client = new ApiClient({ baseUrl: config.registry, token });
        const submissions = await client.get<ReviewSubmission[]>("/api/v1/reviews/my-submissions");
        if (!submissions || submissions.length === 0) {
          console.log("No review submissions.");
          return;
        }
        for (const r of submissions) {
          info(`${r.skillDisplayName} (${r.skillSlug})`);
          dim(`  ${r.namespace} · v${r.version} · ${r.status} · ${r.createdAt}`);
        }
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exitCode = 1;
      }
    });
}
