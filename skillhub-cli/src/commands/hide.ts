import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { success, error, dim } from "../utils/logger.js";

async function hideSkill(
  program: Command,
  slug: string,
  opts: { yes?: boolean; namespace?: string },
  action: "hide" | "unhide"
) {
  const { parseSkillNamespace } = await import("../core/skill-resolver.js");
  const { namespace, slug: skillSlug } = parseSkillNamespace(slug, opts.namespace);
  if (!opts.yes) {
    const { createInterface } = await import("node:readline");
    const rl = createInterface({ input: process.stdin, output: process.stdout });
    const actionText = action === "hide" ? "Hide" : "Unhide";
    const answer = await new Promise<string>((r) =>
      rl.question(`${actionText} ${skillSlug} from ${namespace}? [y/N] `, r)
    );
    rl.close();
    if (answer.toLowerCase() !== "y") {
      console.log("Cancelled.");
      return;
    }
  }

  try {
    const token = await requireToken();
    const config = loadConfigFromProgram(program);
    const client = new ApiClient({ baseUrl: config.registry, token });

    const detail = await client.get<{ id: number }>(
      `/api/v1/skills/${namespace}/${skillSlug}`
    );

    await client.post(`/api/v1/skills/${namespace}/${skillSlug}/${action}`, {
      body: JSON.stringify({}),
      headers: { "Content-Type": "application/json" },
    });

    success(`${action === "hide" ? "Hidden" : "Unhidden"} ${skillSlug}`);
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
}

export function registerHide(program: Command) {
  program
    .command("hide")
    .description("Hide a skill")
    .argument("<skill>", "Skill name or namespace/skill-name")
    .option("-y, --yes", "Skip confirmation")
    .option("--namespace <ns>", "Override namespace (default: parsed from skill or 'global')")
    .action(async (slug: string, opts: { yes?: boolean; namespace?: string }) => {
      await hideSkill(program, slug, opts, "hide");
    });
}

export function registerUnhide(program: Command) {
  program
    .command("unhide")
    .description("Unhide a skill")
    .argument("<skill>", "Skill name or namespace/skill-name")
    .option("-y, --yes", "Skip confirmation")
    .option("--namespace <ns>", "Override namespace (default: parsed from skill or 'global')")
    .action(async (slug: string, opts: { yes?: boolean; namespace?: string }) => {
      await hideSkill(program, slug, opts, "unhide");
    });
}
