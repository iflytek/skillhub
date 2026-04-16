import { GitHubLabelDefinition } from "./github.ts";
import { IssueRoute, RiskLevel } from "./issue-triage-types.ts";

export const TRIAGE_COMMENT_MARKER = "<!-- skillhub-issue-triage-state:";
export const TRIAGE_MANUAL_OVERRIDE_LABEL = "triage-manual";

export const MANAGED_LABEL_PREFIXES = [
  "triage/",
  "priority/",
  "effort/",
  "risk/",
];

export const LABEL_DEFINITIONS: GitHubLabelDefinition[] = [
  {
    name: TRIAGE_MANUAL_OVERRIDE_LABEL,
    color: "5319e7",
    description:
      "暂停此 issue 的自动分流更新 / Pause automated triage updates for this issue.",
  },
  {
    name: "triage/needs-info",
    color: "d4c5f9",
    description:
      "需要补充更多信息后才能分流 / Issue needs more detail before it can be routed.",
  },
  {
    name: "triage/deferred",
    color: "cfd3d7",
    description:
      "暂留 backlog，由自动化定期重新评分 / Issue stays in backlog and is rescored by automation.",
  },
  {
    name: "triage/core",
    color: "fbca04",
    description:
      "交由 core maintainer 结合 AI 协同处理 / Issue should be handled by a core maintainer with AI support.",
  },
  {
    name: "triage/agent-ready",
    color: "0e8a16",
    description:
      "适合作为低风险 agent 独立执行候选 / Issue is a candidate for low-risk agent execution.",
  },
  {
    name: "priority/p0",
    color: "b60205",
    description: "最高优先级 / Highest priority triage bucket.",
  },
  {
    name: "priority/p1",
    color: "d93f0b",
    description: "高优先级 / High priority triage bucket.",
  },
  {
    name: "priority/p2",
    color: "fbca04",
    description: "中优先级 / Medium priority triage bucket.",
  },
  {
    name: "priority/p3",
    color: "ededed",
    description: "低优先级 / Low priority triage bucket.",
  },
  {
    name: "effort/s",
    color: "bfd4f2",
    description: "小改动或边界明确 / Small or well-bounded change.",
  },
  {
    name: "effort/m",
    color: "5319e7",
    description:
      "中等改动，存在一定协同成本 / Medium change with noticeable coordination cost.",
  },
  {
    name: "effort/l",
    color: "1d76db",
    description:
      "大改动或高风险改动，需要 maintainer 负责 / Large or risky change requiring maintainer ownership.",
  },
  {
    name: "risk/high",
    color: "b60205",
    description:
      "涉及安全、鉴权、迁移或公共契约 / Touches security, auth, migrations, or public contracts.",
  },
];

export const REQUIRED_SECTIONS: Record<string, string[]> = {
  bug: ["summary", "steps to reproduce", "expected behavior"],
  feature: ["problem", "proposed solution"],
  reward: ["task description", "reward currency", "reward amount"],
};

const CORE_SURFACE_KEYWORDS = [
  "publish",
  "publishing",
  "review",
  "namespace",
  "search",
  "auth",
  "login",
  "token",
  "scanner",
  "skill detail",
  "registry",
  "api",
  "download",
  "install",
  "cli",
];

const CRITICAL_WORKFLOW_KEYWORDS = [
  "openclaw",
  "clawhub publish",
  "clawhub install",
  "clawhub update",
  "clawhub sync",
  "clawhub inspect",
  "publish skill",
  "install skill",
  "update skill",
  "sync skill",
  "user namespace",
  "namespace parameter",
];

const URGENT_KEYWORDS = [
  "urgent",
  "blocker",
  "broken",
  "fails",
  "failure",
  "regression",
  "crash",
  "500",
  "cannot",
  "can't",
  "unable",
  "production",
  "outage",
  "security",
  "data loss",
];

const HIGH_RISK_KEYWORDS = [
  "security",
  "auth",
  "token",
  "permission",
  "credential",
  "secret",
  "migration",
  "schema",
  "openapi",
  "sdk",
  "breaking change",
  "data loss",
  "account merge",
];

const SMALL_FIX_KEYWORDS = [
  "typo",
  "copy",
  "text",
  "docs",
  "documentation",
  "label",
  "placeholder",
  "link",
  "translation",
  "i18n",
  "style",
];

export function matchesKeywords(text: string, keywords: string[]) {
  const haystack = text.toLowerCase();
  return keywords.filter((keyword) => haystack.includes(keyword));
}

export function coreSurfaceKeywords(text: string) {
  return matchesKeywords(text, CORE_SURFACE_KEYWORDS);
}

export function urgentKeywords(text: string) {
  return matchesKeywords(text, URGENT_KEYWORDS);
}

export function criticalWorkflowKeywords(text: string) {
  return matchesKeywords(text, CRITICAL_WORKFLOW_KEYWORDS);
}

export function highRiskKeywords(text: string) {
  return matchesKeywords(text, HIGH_RISK_KEYWORDS);
}

export function smallFixKeywords(text: string) {
  return matchesKeywords(text, SMALL_FIX_KEYWORDS);
}

export function routeLabel(route: IssueRoute) {
  return `triage/${route}`;
}

export function priorityLabel(priority: number) {
  if (priority >= 4.4) {
    return "priority/p0";
  }

  if (priority >= 3.6) {
    return "priority/p1";
  }

  if (priority >= 2.6) {
    return "priority/p2";
  }

  return "priority/p3";
}

export function effortLabel(effort: number) {
  if (effort <= 2) {
    return "effort/s";
  }

  if (effort === 3) {
    return "effort/m";
  }

  return "effort/l";
}

export function riskLabels(riskLevel: RiskLevel) {
  return riskLevel === "high" ? ["risk/high"] : [];
}
