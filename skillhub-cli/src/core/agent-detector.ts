import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

export interface AgentInfo {
  key: string;
  name: string;
  projectPath: string;
  globalPath: string;
}

const AGENTS: AgentInfo[] = [
  { key: "claude", name: "Claude", projectPath: ".claude/skills", globalPath: ".claude/skills" },
  { key: "claude-code", name: "Claude Code", projectPath: ".claude/skills", globalPath: ".claude/skills" },
  { key: "cursor", name: "Cursor", projectPath: ".agents/skills", globalPath: ".cursor/skills" },
  { key: "codex", name: "Codex", projectPath: ".agents/skills", globalPath: ".codex/skills" },
  { key: "opencode", name: "OpenCode", projectPath: ".agents/skills", globalPath: ".config/opencode/skills" },
  { key: "github-copilot", name: "GitHub Copilot", projectPath: ".agents/skills", globalPath: ".copilot/skills" },
  { key: "cline", name: "Cline", projectPath: ".agents/skills", globalPath: ".agents/skills" },
  { key: "windsurf", name: "Windsurf", projectPath: ".windsurf/skills", globalPath: ".codeium/windsurf/skills" },
  { key: "gemini", name: "Gemini", projectPath: ".agents/skills", globalPath: ".gemini/skills" },
  { key: "gemini-cli", name: "Gemini CLI", projectPath: ".agents/skills", globalPath: ".gemini/skills" },
  { key: "roo", name: "Roo Code", projectPath: ".roo/skills", globalPath: ".roo/skills" },
  { key: "continue", name: "Continue", projectPath: ".continue/skills", globalPath: ".continue/skills" },
  { key: "openhands", name: "OpenHands", projectPath: ".openhands/skills", globalPath: ".openhands/skills" },
  { key: "qoder", name: "Qoder", projectPath: ".qoder/skills", globalPath: ".qoder/skills" },
  { key: "trae", name: "Trae", projectPath: ".trae/skills", globalPath: ".trae/skills" },
  { key: "kiro-cli", name: "Kiro CLI", projectPath: ".kiro/skills", globalPath: ".kiro/skills" },
  { key: "qwen-code", name: "Qwen Code", projectPath: ".qwen/skills", globalPath: ".qwen/skills" },
  { key: "ollama", name: "Ollama", projectPath: ".agents/skills", globalPath: ".ollama/skills" },
  { key: "llama", name: "Llama", projectPath: ".agents/skills", globalPath: ".llama/skills" },
  { key: "codellama", name: "Code Llama", projectPath: ".agents/skills", globalPath: ".codellama/skills" },
  { key: "wizardcoder", name: "WizardCoder", projectPath: ".agents/skills", globalPath: ".wizardcoder/skills" },
  { key: "phi", name: "Phi", projectPath: ".agents/skills", globalPath: ".phi/skills" },
  { key: "mistral", name: "Mistral", projectPath: ".agents/skills", globalPath: ".mistral/skills" },
  { key: "anthropic", name: "Anthropic", projectPath: ".agents/skills", globalPath: ".anthropic/skills" },
  { key: "cohere", name: "Cohere", projectPath: ".agents/skills", globalPath: ".cohere/skills" },
  { key: "ai21", name: "AI21", projectPath: ".agents/skills", globalPath: ".ai21/skills" },
  { key: "stability", name: "Stability AI", projectPath: ".agents/skills", globalPath: ".stability/skills" },
  { key: "deepseek", name: "DeepSeek", projectPath: ".agents/skills", globalPath: ".deepseek/skills" },
  { key: "local", name: "Local AI", projectPath: ".agents/skills", globalPath: ".local/skills" },
  { key: "jina", name: "Jina AI", projectPath: ".agents/skills", globalPath: ".jina/skills" },
  { key: "perplexity", name: "Perplexity", projectPath: ".agents/skills", globalPath: ".perplexity/skills" },
  { key: "groq", name: "Groq", projectPath: ".agents/skills", globalPath: ".groq/skills" },
  { key: "fireworks", name: "Fireworks AI", projectPath: ".agents/skills", globalPath: ".fireworks/skills" },
  { key: "together", name: "Together AI", projectPath: ".agents/skills", globalPath: ".together/skills" },
  { key: "litellm", name: "LiteLLM", projectPath: ".agents/skills", globalPath: ".litellm/skills" },
  { key: "vllm", name: "vLLM", projectPath: ".agents/skills", globalPath: ".vllm/skills" },
  { key: "anyscale", name: "Anyscale", projectPath: ".agents/skills", globalPath: ".anyscale/skills" },
  { key: "baseten", name: "Baseten", projectPath: ".agents/skills", globalPath: ".baseten/skills" },
  { key: "modal", name: "Modal", projectPath: ".agents/skills", globalPath: ".modal/skills" },
  { key: "replicate", name: "Replicate", projectPath: ".agents/skills", globalPath: ".replicate/skills" },
  { key: "bolt", name: "Bolt", projectPath: ".bolt/skills", globalPath: ".bolt/skills" },
  { key: "goose", name: "Goose", projectPath: ".goose/skills", globalPath: ".goose/skills" },
  { key: "devin", name: "Devin", projectPath: ".agents/skills", globalPath: ".devin/skills" },
  { key: "swethe", name: "Swethe", projectPath: ".agents/skills", globalPath: ".swethe/skills" },
];

export function getAllAgents(): AgentInfo[] {
  return AGENTS;
}

export function detectInstalledAgents(): AgentInfo[] {
  const home = homedir();
  return AGENTS.filter((agent) => {
    const globalPath = join(home, agent.globalPath);
    return existsSync(globalPath);
  });
}

export function getAgentByKey(key: string): AgentInfo | undefined {
  return AGENTS.find((a) => a.key === key);
}
