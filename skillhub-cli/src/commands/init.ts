import { Command } from "commander";
import { mkdirSync, writeFileSync, existsSync } from "node:fs";
import { join, resolve } from "node:path";
import { success, error } from "../utils/logger.js";

export function registerInit(program: Command) {
  program
    .command("init [name]")
    .description("Create a new SKILL.md template")
    .action((name?: string) => {
      const dir = name ? resolve(process.cwd(), name) : process.cwd();

      if (!existsSync(dir)) {
        mkdirSync(dir, { recursive: true });
      }

      const skillMd = join(dir, "SKILL.md");
      if (existsSync(skillMd)) {
        error("SKILL.md already exists");
        process.exitCode = 1;
      }

      const slug = name || "my-skill";
      const content = `---
name: ${slug}
description: What this skill does and when to use it
---

# ${slug}

Instructions for the agent to follow when this skill is activated.

## When to Use

Describe the scenarios where this skill should be used.

## Steps

1. First, do this
2. Then, do that
`;

      writeFileSync(skillMd, content);
      success(`Created SKILL.md at ${skillMd}`);
    });
}
