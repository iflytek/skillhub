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
  { key: "claude", name: "Claude Code", skillsDir: ".claude/skills", globalSkillsDir: ".claude/skills" },
  { key: "opencode", name: "OpenCode", skillsDir: ".agents/skills", globalSkillsDir: ".config/opencode/skills" },
  { key: "cursor", name: "Cursor", skillsDir: ".agents/skills", globalSkillsDir: ".cursor/skills" },
  { key: "codex", name: "Codex", skillsDir: ".agents/skills", globalSkillsDir: ".codex/skills" },
  { key: "cline", name: "Cline", skillsDir: ".agents/skills" },
  { key: "windsurf", name: "Windsurf", skillsDir: ".windsurf/skills", globalSkillsDir: ".codeium/windsurf/skills" },
  { key: "gemini-cli", name: "Gemini CLI", skillsDir: ".agents/skills", globalSkillsDir: ".gemini/skills" },
  { key: "github-copilot", name: "GitHub Copilot", skillsDir: ".agents/skills", globalSkillsDir: ".copilot/skills" },
  { key: "goose", name: "Goose", skillsDir: ".goose/skills", globalSkillsDir: ".config/goose/skills" },
  { key: "continue", name: "Continue", skillsDir: ".continue/skills" },
  { key: "roo", name: "Roo Code", skillsDir: ".roo/skills" },
  { key: "trae", name: "Trae", skillsDir: ".trae/skills" },
  { key: "openhands", name: "OpenHands", skillsDir: ".openhands/skills" },
  { key: "bolt", name: "Bolt", skillsDir: ".bolt/skills" },
  { key: "kiro-cli", name: "Kiro CLI", skillsDir: ".kiro/skills" },
  { key: "qoder", name: "Qoder", skillsDir: ".qoder/skills" },
  { key: "qwen-code", name: "Qwen Code", skillsDir: ".qwen/skills" },
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
