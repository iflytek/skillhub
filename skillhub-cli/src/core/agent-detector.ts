import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

export interface AgentInfo {
  key: string;
  name: string;
  skillsDir: string;
  globalSkillsDir?: string;
}

const home = homedir();

const AGENTS: AgentInfo[] = [
  // Universal agents (.agents/skills)
  { key: "amp", name: "Amp", skillsDir: ".agents/skills", globalSkillsDir: ".config/agents/skills" },
  { key: "antigravity", name: "Antigravity", skillsDir: ".agents/skills", globalSkillsDir: ".gemini/antigravity/skills" },
  { key: "cline", name: "Cline", skillsDir: ".agents/skills" },
  { key: "codex", name: "Codex", skillsDir: ".agents/skills", globalSkillsDir: ".codex/skills" },
  { key: "cursor", name: "Cursor", skillsDir: ".agents/skills", globalSkillsDir: ".cursor/skills" },
  { key: "deepagents", name: "Deep Agents", skillsDir: ".agents/skills", globalSkillsDir: ".deepagents/agent/skills" },
  { key: "firebender", name: "Firebender", skillsDir: ".agents/skills", globalSkillsDir: ".firebender/skills" },
  { key: "gemini-cli", name: "Gemini CLI", skillsDir: ".agents/skills", globalSkillsDir: ".gemini/skills" },
  { key: "github-copilot", name: "GitHub Copilot", skillsDir: ".agents/skills", globalSkillsDir: ".copilot/skills" },
  { key: "kimi-cli", name: "Kimi Code CLI", skillsDir: ".agents/skills", globalSkillsDir: ".config/agents/skills" },
  { key: "kilo", name: "Kilo Code", skillsDir: ".agents/skills", globalSkillsDir: ".kilocode/skills" },
  { key: "mux", name: "Mux", skillsDir: ".agents/skills" },
  { key: "opencode", name: "OpenCode", skillsDir: ".agents/skills", globalSkillsDir: ".config/opencode/skills" },
  { key: "replit", name: "Replit", skillsDir: ".agents/skills" },
  { key: "warp", name: "Warp", skillsDir: ".agents/skills" },

  // Agent-specific path agents
  { key: "claude-code", name: "Claude Code", skillsDir: ".claude/skills", globalSkillsDir: ".claude/skills" },
  { key: "augment", name: "Augment", skillsDir: ".augment/skills" },
  { key: "bob", name: "IBM Bob", skillsDir: ".bob/skills" },
  { key: "openclaw", name: "OpenClaw", skillsDir: "skills", globalSkillsDir: ".openclaw/skills" },
  { key: "codebuddy", name: "CodeBuddy", skillsDir: ".codebuddy/skills" },
  { key: "continue", name: "Continue", skillsDir: ".continue/skills" },
  { key: "cortex", name: "Cortex Code", skillsDir: ".cortex/skills", globalSkillsDir: ".snowflake/cortex/skills" },
  { key: "crush", name: "Crush", skillsDir: ".crush/skills", globalSkillsDir: ".config/crush/skills" },
  { key: "droid", name: "Droid", skillsDir: ".factory/skills" },
  { key: "goose", name: "Goose", skillsDir: ".goose/skills", globalSkillsDir: ".config/goose/skills" },
  { key: "junie", name: "Junie", skillsDir: ".junie/skills" },
  { key: "iflow-cli", name: "iFlow CLI", skillsDir: ".iflow/skills" },
  { key: "kode", name: "Kode", skillsDir: ".kode/skills" },
  { key: "mcpjam", name: "MCPJam", skillsDir: ".mcpjam/skills" },
  { key: "mistral-vibe", name: "Mistral Vibe", skillsDir: ".vibe/skills" },
  { key: "openhands", name: "OpenHands", skillsDir: ".openhands/skills" },
  { key: "pi", name: "Pi", skillsDir: ".pi/skills", globalSkillsDir: ".pi/agent/skills" },
  { key: "qoder", name: "Qoder", skillsDir: ".qoder/skills" },
  { key: "qwen-code", name: "Qwen Code", skillsDir: ".qwen/skills" },
  { key: "roo", name: "Roo Code", skillsDir: ".roo/skills" },
  { key: "trae", name: "Trae", skillsDir: ".trae/skills" },
  { key: "trae-cn", name: "Trae CN", skillsDir: ".trae/skills", globalSkillsDir: ".trae-cn/skills" },
  { key: "windsurf", name: "Windsurf", skillsDir: ".windsurf/skills", globalSkillsDir: ".codeium/windsurf/skills" },
  { key: "zencoder", name: "Zencoder", skillsDir: ".zencoder/skills" },
  { key: "neovate", name: "Neovate", skillsDir: ".neovate/skills" },
  { key: "pochi", name: "Pochi", skillsDir: ".pochi/skills" },
  { key: "adal", name: "AdaL", skillsDir: ".adal/skills" },
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

const UNIVERSAL_PATH = ".agents/skills";

export function isUniversalAgent(agent: AgentInfo): boolean {
  return agent.skillsDir === UNIVERSAL_PATH;
}

export function getUniversalAgents(): AgentInfo[] {
  return AGENTS.filter((a) => isUniversalAgent(a));
}

export function getNonUniversalAgents(): AgentInfo[] {
  return AGENTS.filter((a) => !isUniversalAgent(a));
}