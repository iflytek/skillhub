import { TriageResult, TriageSnapshot } from "./issue-triage-types.ts";
import { buildMaintainerHandoffBrief } from "./issue-handoff-brief.ts";

export function mergeRuleAndLlm(ruleResult: TriageResult): TriageResult {
  const llm = ruleResult.llm;

  if (!llm || llm.failed || llm.mode !== "assist") {
    return {
      ...ruleResult,
      handoffBrief: ruleResult.route === "core"
        ? buildMaintainerHandoffBrief(ruleResult)
        : undefined,
      mode: llm && !llm.failed && llm.mode === "shadow"
        ? "llm-shadow"
        : "rules-only",
      inputHash: llm?.inputHash ?? ruleResult.inputHash,
    };
  }

  const impact = nudgeScore(ruleResult.impact, llm.impact);
  const urgency = nudgeScore(ruleResult.urgency, llm.urgency);
  const effort = nudgeScore(ruleResult.effort, llm.effort);
  const confidence = nudgeScore(ruleResult.confidence, llm.confidence);
  const missingFields = unique([
    ...ruleResult.missingFields,
    ...llm.missingInfo,
  ]);
  const highRiskReasons = unique([
    ...ruleResult.highRiskReasons,
    ...llm.riskFlags.map((flag) =>
      `LLM 标记了高风险区域：${flag} / LLM flagged high-risk area: ${flag}.`
    ),
  ]);
  const requiresCoreMaintainer = ruleResult.requiresCoreMaintainer;
  const riskLevel = highRiskReasons.length > 0 ? "high" : "low";
  const priority = clamp(
    roundToOneDecimal(
      impact * 0.45 +
        urgency * 0.35 +
        ruleResult.ageBoost +
        ruleResult.engagementBoost,
    ),
    1,
    5,
  );
  const route = determineRoute(
    priority,
    effort,
    confidence,
    riskLevel,
    missingFields,
    requiresCoreMaintainer,
  );
  const nextAction = describeNextAction(route, missingFields);
  const reasons = unique([
    ...ruleResult.reasons,
    ...llm.rationale,
    llm.summary
      ? `LLM 摘要：${llm.summaryZh || llm.summary} / LLM summary: ${
        llm.summaryEn || llm.summary
      }`
      : "",
  ]).slice(0, 6);

  const mergedSnapshot: TriageSnapshot = {
    route,
    riskLevel,
    requiresCoreMaintainer,
    openDays: ruleResult.openDays,
    impact,
    urgency,
    effort,
    confidence,
    priority,
    ageBoost: ruleResult.ageBoost,
    priorityFloor: ruleResult.priorityFloor,
    engagementBoost: ruleResult.engagementBoost,
    missingFields,
    reasons,
    highRiskReasons,
    nextAction,
  };

  return {
    ...ruleResult,
    ...mergedSnapshot,
    mode: "llm-assist",
    inputHash: llm.inputHash,
    handoffBrief: route === "core"
      ? buildMaintainerHandoffBrief({
        ...ruleResult,
        ...mergedSnapshot,
        mode: "llm-assist",
        inputHash: llm.inputHash,
      })
      : undefined,
  };
}

export function determineRoute(
  priority: number,
  effort: number,
  confidence: number,
  riskLevel: "low" | "high",
  missingFields: string[],
  requiresCoreMaintainer = false,
) {
  if (requiresCoreMaintainer) {
    return "core";
  }

  if (missingFields.length > 0 || confidence <= 2) {
    return "needs-info";
  }

  if (priority < 3.6) {
    return "deferred";
  }

  if (riskLevel === "high" || effort >= 4 || confidence <= 3) {
    return "core";
  }

  return "agent-ready";
}

export function describeNextAction(
  route: TriageResult["route"],
  missingFields: string[],
) {
  if (route === "needs-info") {
    return `等待补充更多信息；作者更新 issue 或评论 \`/retriage\` 后重新分流 / Wait for more detail, then rerun triage after the author edits the issue or comments \`/retriage\`. Missing: ${
      missingFields.join(", ")
    }.`;
  }

  if (route === "deferred") {
    return "将 issue 保留在 deferred 队列，并由 6 小时一次的 rescore 持续抬升；最晚在第 10 天强制进入 active lane。若第 14 天仍未闭环，应按 SLA 视为 P0 升级目标，并在下一次 triage 中重点处理 / Keep the issue in the deferred queue and let the 6-hour rescore keep lifting it; it is forced into an active lane by day 10. If it is still open on day 14, treat it as a P0 escalation target under the SLA and prioritize it in the next triage pass.";
  }

  if (route === "core") {
    return "交给 core maintainer，并结合本地编程Agent协助完成复现、收敛范围与验证闭环 / Hand the issue to a core maintainer and use a local programming agent for reproduction, scoping, and validation.";
  }

  return "在 self-hosted issue-agent runner 启用后，将其标记为低风险 agent 可执行候选 / Mark as a candidate for low-risk agent execution once the self-hosted issue-agent runner is enabled.";
}

function nudgeScore(ruleScore: number, llmScore: number) {
  if (llmScore === ruleScore) {
    return ruleScore;
  }

  return clamp(ruleScore + Math.sign(llmScore - ruleScore), 1, 5);
}

function unique(values: string[]) {
  return [...new Set(values.filter((value) => value.trim().length > 0))];
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

function roundToOneDecimal(value: number) {
  return Math.round(value * 10) / 10;
}
