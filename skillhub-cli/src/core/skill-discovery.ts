import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

export interface DiscoveredSkill {
  name: string;
  description: string;
  dir: string;
}

const SKILL_DIRS = [
  "skills",
  ".agents/skills",
  ".claude/skills",
  ".augment/skills",
  ".cursor/skills",
  ".codex/skills",
];

export function discoverSkills(rootDir: string): DiscoveredSkill[] {
  const skills: DiscoveredSkill[] = [];

  for (const subDir of SKILL_DIRS) {
    const fullPath = join(rootDir, subDir);
    if (!existsSync(fullPath)) continue;
    skills.push(...scanDir(fullPath));
  }

  if (skills.length === 0) {
    skills.push(...scanDir(rootDir));
  }

  return skills;
}

function scanDir(dir: string): DiscoveredSkill[] {
  const skills: DiscoveredSkill[] = [];
  if (!existsSync(dir)) return skills;

  try {
    for (const entry of readdirSync(dir)) {
      const entryPath = join(dir, entry);
      const stat = statSync(entryPath);
      if (!stat.isDirectory()) continue;

      const skillMd = join(entryPath, "SKILL.md");
      if (!existsSync(skillMd)) continue;

      const content = readFileSync(skillMd, "utf-8");
      const name = extractFrontmatterField(content, "name");
      const description = extractFrontmatterField(content, "description");

      if (name) {
        skills.push({ name, description: description || name, dir: entryPath });
      }
    }
  } catch {
    // Skip unreadable directories
  }

  return skills;
}

function extractFrontmatterField(content: string, field: string): string | undefined {
  const match = content.match(/^---\n([\s\S]*?)\n---/);
  if (!match) return undefined;
  const frontmatter = match[1];
  const fieldMatch = frontmatter.match(new RegExp(`^${field}:\\s*(.+)$`, "m"));
  return fieldMatch ? fieldMatch[1].trim().replace(/^["']|["']$/g, "") : undefined;
}
