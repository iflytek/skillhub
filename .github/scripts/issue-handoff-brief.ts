import { MaintainerHandoffBrief, TriageResult } from "./issue-triage-types.ts";

const AREA_RULES: Array<{ keywords: string[]; area: string }> = [
  {
    keywords: ["clawhub publish", "publish skill", "publish", "namespace"],
    area:
      "CLI 发布命令参数解析与 namespace 感知发布流程 / CLI publish command option parsing and namespace-aware publish flow",
  },
  {
    keywords: ["clawhub install", "install skill", "install"],
    area:
      "技能安装流程与 registry/lockfile 集成 / Skill installation flow and registry/lockfile integration",
  },
  {
    keywords: ["clawhub update", "update skill", "update"],
    area:
      "已安装技能更新流程与版本解析 / Installed skill update flow and version resolution",
  },
  {
    keywords: ["clawhub sync", "sync skill", "sync"],
    area:
      "本地技能同步流程与发布 diff 检测 / Local skill sync flow and publish diff detection",
  },
  {
    keywords: ["inspect", "search", "explore"],
    area:
      "Registry 发现与 CLI 查询流程 / Registry discovery and CLI query workflow",
  },
  {
    keywords: ["auth", "login", "ldap", "sso", "token"],
    area:
      "认证、会话与身份集成 / Authentication, session, and identity integration",
  },
  {
    keywords: ["openapi", "sdk", "api contract", "contract"],
    area:
      "公开 API 契约、生成 SDK 与兼容性表面 / Public API contract, generated SDKs, and compatibility surface",
  },
  {
    keywords: ["docs", "documentation", "manual", "help", "--help"],
    area:
      "文档、操作指引与 CLI help 输出 / Documentation, operator guidance, and CLI help output",
  },
  {
    keywords: ["scanner", "security", "audit"],
    area:
      "安全扫描流程与审计/报告行为 / Security scanner pipeline and audit/reporting behavior",
  },
];

export function buildMaintainerHandoffBrief(
  result: TriageResult,
): MaintainerHandoffBrief | undefined {
  if (result.route !== "core") {
    return undefined;
  }

  const summary = buildSummary(result);
  const whyCore = unique([
    result.requiresCoreMaintainer
      ? "阻塞 OpenClaw/ClawHub 核心工作流，因此即便改动范围看起来可控，也需要 maintainer judgment / Blocks an OpenClaw/ClawHub core workflow, so maintainer judgment is required even if the code change looks bounded."
      : "",
    result.riskLevel === "high"
      ? "触及高风险区域，未经 maintainer 审查不应直接信任自动修复 / Touches a higher-risk area where automated fixes should not be trusted without maintainer review."
      : "",
    result.effort >= 4
      ? "大概率跨多个模块或公共兼容面 / Likely spans multiple modules or a public compatibility surface."
      : "",
    result.confidence <= 3
      ? "问题本身重要，但仍需要 maintainer 先收敛范围再实施 / The issue is important, but a maintainer still needs to tighten scope before implementation."
      : "",
    ...result.highRiskReasons,
  ]).slice(0, 4);

  const reproduction = buildReproduction(result);
  const suspectedAreas = inferSuspectedAreas(result);
  const risks = buildRisks(result, suspectedAreas);
  const validation = buildValidation(result, suspectedAreas);

  return {
    summary,
    whyCore,
    reproduction,
    suspectedAreas,
    risks,
    validation,
  };
}

function buildSummary(result: TriageResult) {
  const llmSummary = result.llm?.summaryZh ?? result.llm?.summary ??
    result.llm?.summaryEn;

  if (llmSummary && llmSummary.trim().length > 0) {
    return llmSummary.trim();
  }

  const preferred = [
    result.sections["summary"],
    result.sections["problem"],
    result.sections["expected behavior"],
  ].find((value) => value && value.trim().length > 0);

  if (preferred) {
    return compact(preferred);
  }

  return result.issue.title.replace(/^\[[^\]]+\]\s*/, "").trim();
}

function buildReproduction(result: TriageResult) {
  const commandFocusedSteps = extractCommandAndErrorLines(
    result.sections["steps to reproduce"],
  );

  if (commandFocusedSteps.length > 0) {
    return commandFocusedSteps.slice(0, 4);
  }

  const steps = splitIntoBullets(result.sections["steps to reproduce"]);

  if (steps.length > 0) {
    return steps.slice(0, 5);
  }

  const problem = splitIntoBullets(result.sections["problem"]);

  if (problem.length > 0) {
    return problem.slice(0, 4);
  }

  return [
    "按 issue 中描述的操作路径复现，并确认当前失败模式 / Recreate the operator flow described in the issue and confirm the current failure mode.",
  ];
}

function inferSuspectedAreas(result: TriageResult) {
  const text = [
    result.issue.title,
    result.sections["summary"] ?? "",
    result.sections["problem"] ?? "",
    result.sections["steps to reproduce"] ?? "",
    result.sections["impact"] ?? "",
    result.sections["api contract impact"] ?? "",
    result.sections["contract or sdk impact"] ?? "",
  ]
    .join("\n")
    .toLowerCase();

  const areas = AREA_RULES.filter((rule) =>
    rule.keywords.some((keyword) => text.includes(keyword))
  ).map((rule) => rule.area);

  if (areas.length > 0) {
    return unique(areas).slice(0, 5);
  }

  return [
    "最接近该失败路径的 owner-facing 工作流模块 / The closest owner-facing workflow module for the issue's reported failure path",
    "当前对外承诺该行为的文档或 help 文本 / Any docs or help text that currently promise the affected behavior",
  ];
}

function buildRisks(result: TriageResult, suspectedAreas: string[]) {
  const risks = unique([
    ...result.highRiskReasons,
    result.llm?.riskFlags.includes("cli-protocol")
      ? "CLI 行为、文档和操作预期可能发生漂移，需要同步更新命令 help 与兼容性说明 / CLI behavior, docs, and operator expectations may drift unless command help and compatibility notes are updated together."
      : "",
    suspectedAreas.some((area) => area.toLowerCase().includes("namespace"))
      ? "namespace 范围行为如果没有保留 fallback routing，可能回归默认 publish/install 流程 / Namespace-scoped behavior can regress default publish/install flows if fallback routing is not preserved."
      : "",
    result.requiresCoreMaintainer
      ? "该问题影响已定义主流程，回归会很快被终端用户感知 / This issue affects a documented primary workflow, so regressions would be visible to end users quickly."
      : "",
  ]);

  return risks.length > 0 ? risks.slice(0, 4) : [
    "合并前检查相邻用户路径是否出现回归 / Check for regressions in adjacent user-facing workflow paths before merging.",
  ];
}

function buildValidation(result: TriageResult, suspectedAreas: string[]) {
  const validation = unique([
    result.sections["steps to reproduce"]
      ? "按 issue 中的复现步骤逐条回放，确认报告的问题已消失 / Replay the exact reproduction steps from the issue and confirm the reported failure disappears."
      : "修复后端到端验证主报告流程 / Validate the primary reported workflow end-to-end after the fix.",
    result.sections["expected behavior"]
      ? `确认最终行为符合 issue 期望 / Confirm the final behavior matches the issue's expected outcome: ${
        compact(result.sections["expected behavior"])
      }`
      : "",
    suspectedAreas.some((area) => area.toLowerCase().includes("documentation"))
      ? "更新或核对文档与 CLI help 输出，确保其与实现行为一致 / Update or verify documentation and CLI help output so they match the implemented behavior."
      : "",
    suspectedAreas.some((area) => area.toLowerCase().includes("api contract"))
      ? "发布前检查下游 API/SDK/CLI 的兼容性预期 / Check for downstream API/SDK/CLI compatibility expectations before shipping."
      : "",
    suspectedAreas.some((area) => area.toLowerCase().includes("namespace"))
      ? "同时验证 namespace 范围行为与默认非 namespace 流程 / Verify both namespace-scoped behavior and the default non-namespace flow."
      : "",
    result.requiresCoreMaintainer
      ? "围绕受影响的 OpenClaw/ClawHub 用户路径执行最小必要回归测试 / Run the smallest relevant regression test around the affected OpenClaw/ClawHub user journey."
      : "",
  ]);

  return validation.slice(0, 5);
}

function splitIntoBullets(value: string | undefined) {
  if (!value) {
    return [];
  }

  return value
    .split("\n")
    .map((line) => line.trim())
    .filter((line) =>
      line.length > 0 &&
      line !== "```" &&
      !line.startsWith("PS ") &&
      !line.startsWith("Usage:") &&
      !line.startsWith("Options:") &&
      !line.startsWith("Arguments:")
    )
    .map((line) => line.replace(/^[*-]\s*/, ""))
    .slice(0, 6);
}

function extractCommandAndErrorLines(value: string | undefined) {
  if (!value) {
    return [];
  }

  return value
    .split("\n")
    .map((line) => line.trim())
    .filter((line) =>
      line.length > 0 &&
      (
        line.toLowerCase().includes("clawhub ") ||
        line.toLowerCase().startsWith("error:") ||
        line.toLowerCase().includes("unknown option") ||
        line.toLowerCase().includes("usage:")
      )
    )
    .map((line) => line.replace(/^[>*-]\s*/, ""))
    .slice(0, 4);
}

function compact(value: string) {
  return value.replace(/\s+/g, " ").trim();
}

function unique(values: string[]) {
  return [...new Set(values.filter((value) => value.trim().length > 0))];
}
