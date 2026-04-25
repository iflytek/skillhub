import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

export interface AgentInfo {
  key: string;
  name: string;
  skillsDir: string;
  globalSkillsDir?: string;
  /** Whether to show this agent in the Universal section of interactive prompts.
   *  Agents with skillsDir === ".agents/skills" are universal by default,
   *  but some (like "replit" for cloud environments) should be hidden. */
  showInUniversalList?: boolean;
}

const home = homedir();

const AGENTS: AgentInfo[] = [
  // Universal agents (.agents/skills) — share the canonical .agents/skills directory
  { key: "amp", name: "Amp", skillsDir: ".agents/skills", globalSkillsDir: ".config/agents/skills" },
  { key: "antigravity", name: "Antigravity", skillsDir: ".agents/skills", globalSkillsDir: ".gemini/antigravity/skills" },
  { key: "cline", name: "Cline", skillsDir: ".agents/skills", globalSkillsDir: ".agents/skills" },
  { key: "codex", name: "Codex", skillsDir: ".agents/skills", globalSkillsDir: ".codex/skills" },
  { key: "cursor", name: "Cursor", skillsDir: ".agents/skills", globalSkillsDir: ".cursor/skills" },
  { key: "deepagents", name: "Deep Agents", skillsDir: ".agents/skills", globalSkillsDir: ".deepagents/agent/skills" },
  { key: "firebender", name: "Firebender", skillsDir: ".agents/skills", globalSkillsDir: ".firebender/skills" },
  { key: "gemini-cli", name: "Gemini CLI", skillsDir: ".agents/skills", globalSkillsDir: ".gemini/skills" },
  { key: "github-copilot", name: "GitHub Copilot", skillsDir: ".agents/skills", globalSkillsDir: ".copilot/skills" },
  { key: "kimi-cli", name: "Kimi Code CLI", skillsDir: ".agents/skills", globalSkillsDir: ".config/agents/skills" },
  { key: "opencode", name: "OpenCode", skillsDir: ".agents/skills", globalSkillsDir: ".config/opencode/skills" },
  { key: "warp", name: "Warp", skillsDir: ".agents/skills", globalSkillsDir: ".agents/skills" },
  // Universal agents hidden from the interactive list
  { key: "replit", name: "Replit", skillsDir: ".agents/skills", globalSkillsDir: ".config/agents/skills", showInUniversalList: false },
  { key: "universal", name: "Universal", skillsDir: ".agents/skills", globalSkillsDir: ".config/agents/skills", showInUniversalList: false },

  // Agent-specific path agents (non-universal)
  { key: "claude-code", name: "Claude Code", skillsDir: ".claude/skills", globalSkillsDir: ".claude/skills" },
  { key: "augment", name: "Augment", skillsDir: ".augment/skills", globalSkillsDir: ".augment/skills" },
  { key: "bob", name: "IBM Bob", skillsDir: ".bob/skills", globalSkillsDir: ".bob/skills" },
  { key: "openclaw", name: "OpenClaw", skillsDir: "skills", globalSkillsDir: ".openclaw/skills" },
  { key: "codebuddy", name: "CodeBuddy", skillsDir: ".codebuddy/skills", globalSkillsDir: ".codebuddy/skills" },
  { key: "command-code", name: "Command Code", skillsDir: ".commandcode/skills", globalSkillsDir: ".commandcode/skills" },
  { key: "continue", name: "Continue", skillsDir: ".continue/skills", globalSkillsDir: ".continue/skills" },
  { key: "cortex", name: "Cortex Code", skillsDir: ".cortex/skills", globalSkillsDir: ".snowflake/cortex/skills" },
  { key: "crush", name: "Crush", skillsDir: ".crush/skills", globalSkillsDir: ".config/crush/skills" },
  { key: "droid", name: "Droid", skillsDir: ".factory/skills", globalSkillsDir: ".factory/skills" },
  { key: "goose", name: "Goose", skillsDir: ".goose/skills", globalSkillsDir: ".config/goose/skills" },
  { key: "junie", name: "Junie", skillsDir: ".junie/skills", globalSkillsDir: ".junie/skills" },
  { key: "iflow-cli", name: "iFlow CLI", skillsDir: ".iflow/skills", globalSkillsDir: ".iflow/skills" },
  { key: "kilo", name: "Kilo Code", skillsDir: ".kilocode/skills", globalSkillsDir: ".kilocode/skills" },
  { key: "kiro-cli", name: "Kiro CLI", skillsDir: ".kiro/skills", globalSkillsDir: ".kiro/skills" },
  { key: "kode", name: "Kode", skillsDir: ".kode/skills", globalSkillsDir: ".kode/skills" },
  { key: "mcpjam", name: "MCPJam", skillsDir: ".mcpjam/skills", globalSkillsDir: ".mcpjam/skills" },
  { key: "mistral-vibe", name: "Mistral Vibe", skillsDir: ".vibe/skills", globalSkillsDir: ".vibe/skills" },
  { key: "mux", name: "Mux", skillsDir: ".mux/skills", globalSkillsDir: ".mux/skills" },
  { key: "openhands", name: "OpenHands", skillsDir: ".openhands/skills", globalSkillsDir: ".openhands/skills" },
  { key: "pi", name: "Pi", skillsDir: ".pi/skills", globalSkillsDir: ".pi/agent/skills" },
  { key: "qoder", name: "Qoder", skillsDir: ".qoder/skills", globalSkillsDir: ".qoder/skills" },
  { key: "qwen-code", name: "Qwen Code", skillsDir: ".qwen/skills", globalSkillsDir: ".qwen/skills" },
  { key: "roo", name: "Roo Code", skillsDir: ".roo/skills", globalSkillsDir: ".roo/skills" },
  { key: "trae", name: "Trae", skillsDir: ".trae/skills", globalSkillsDir: ".trae/skills" },
  { key: "trae-cn", name: "Trae CN", skillsDir: ".trae/skills", globalSkillsDir: ".trae-cn/skills" },
  { key: "windsurf", name: "Windsurf", skillsDir: ".windsurf/skills", globalSkillsDir: ".codeium/windsurf/skills" },
  { key: "zencoder", name: "Zencoder", skillsDir: ".zencoder/skills", globalSkillsDir: ".zencoder/skills" },
  { key: "neovate", name: "Neovate", skillsDir: ".neovate/skills", globalSkillsDir: ".neovate/skills" },
  { key: "pochi", name: "Pochi", skillsDir: ".pochi/skills", globalSkillsDir: ".pochi/skills" },
  { key: "adal", name: "AdaL", skillsDir: ".adal/skills", globalSkillsDir: ".adal/skills" },
];

export function getAllAgents(): AgentInfo[] {
  return AGENTS;
}

export function detectInstalledAgents(): AgentInfo[] {
  return AGENTS.filter((agent) => {
    const globalPath = agent.globalSkillsDir ? join(home, agent.globalSkillsDir) : join(home, agent.skillsDir);
    return existsSync(globalPath);
  });
}

export function getAgentByKey(key: string): AgentInfo | undefined {
  return AGENTS.find((a) => a.key === key);
}

const CANONICAL_SKILLS_DIR = ".agents/skills";

/**
 * Check if an agent uses the canonical .agents/skills directory at the project level.
 * Used for UI grouping (Universal section in interactive prompts).
 */
export function isUniversalAgent(agent: AgentInfo): boolean {
  return agent.skillsDir === CANONICAL_SKILLS_DIR;
}

/**
 * Get the target installation directory for an agent in the given scope.
 * In global scope, uses globalSkillsDir if defined, otherwise falls back to skillsDir.
 * In project scope, always uses skillsDir.
 */
export function getAgentTargetDir(agent: AgentInfo, isGlobal: boolean): string {
  return isGlobal
    ? (agent.globalSkillsDir || agent.skillsDir)
    : agent.skillsDir;
}

/**
 * Dynamically determine if an agent is "universal" for the given scope.
 * An agent is universal when its target installation directory equals the canonical
 * .agents/skills directory — meaning no symlink is needed because the canonical
 * location IS the agent's own directory.
 *
 * This differs from isUniversalAgent() which only checks project-level skillsDir.
 * For example, Codex has skillsDir=".agents/skills" (universal at project level)
 * but globalSkillsDir=".codex/skills" (NOT universal at global level — needs symlink).
 */
export function isUniversalForScope(agent: AgentInfo, isGlobal: boolean): boolean {
  return getAgentTargetDir(agent, isGlobal) === CANONICAL_SKILLS_DIR;
}

/**
 * Returns universal agents that should appear in the interactive selection list.
 * Excludes agents with showInUniversalList === false (e.g. replit, which is cloud-only).
 */
export function getUniversalAgents(): AgentInfo[] {
  return AGENTS.filter((a) => isUniversalAgent(a) && a.showInUniversalList !== false);
}

export function getNonUniversalAgents(): AgentInfo[] {
  return AGENTS.filter((a) => !isUniversalAgent(a));
}

/**
 * Ensure that all universal agents are included in the target agent list.
 * This guarantees that skills are always installed to ~/.agents/skills (the canonical location),
 * making them available to any agent that reads from that directory.
 */
export function ensureUniversalAgents(targetAgents: AgentInfo[]): AgentInfo[] {
  const universalAgents = getUniversalAgents();
  const result = [...targetAgents];
  for (const ua of universalAgents) {
    if (!result.some((a) => a.key === ua.key)) {
      result.push(ua);
    }
  }
  return result;
}
