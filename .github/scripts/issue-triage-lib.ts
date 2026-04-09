import { GitHubClient, GitHubIssue, GitHubIssueComment } from "./github.ts";
import {
  coreSurfaceKeywords,
  criticalWorkflowKeywords,
  effortLabel,
  highRiskKeywords,
  LABEL_DEFINITIONS,
  MANAGED_LABEL_PREFIXES,
  priorityLabel,
  REQUIRED_SECTIONS,
  riskLabels,
  routeLabel,
  smallFixKeywords,
  TRIAGE_COMMENT_MARKER,
  urgentKeywords,
} from "./issue-triage-config.ts";
import {
  IssueKind,
  ParsedIssueBody,
  TriageMachineState,
  TriageResult,
  TriageSnapshot,
} from "./issue-triage-types.ts";
import { describeNextAction, determineRoute } from "./issue-triage-merge.ts";
import { buildMaintainerHandoffBrief } from "./issue-handoff-brief.ts";

export function parseIssueBody(body: string | null): ParsedIssueBody {
  const sections: Record<string, string> = {};
  const rawBody = body ?? "";
  const headingMatches = [...rawBody.matchAll(/^###\s+(.+)$/gm)];

  for (let index = 0; index < headingMatches.length; index += 1) {
    const current = headingMatches[index];
    const next = headingMatches[index + 1];
    const heading = normalizeHeading(current[1]);
    const contentStart = current.index! + current[0].length;
    const contentEnd = next ? next.index! : rawBody.length;
    const content = rawBody.slice(contentStart, contentEnd).trim();

    sections[heading] = cleanupSectionContent(content);
  }

  return {
    sections,
    missingFields: [],
  };
}

export function detectIssueKind(
  issue: GitHubIssue,
  sections: Record<string, string>,
) {
  const labelNames = issue.labels.map((label) =>
    label.name?.toLowerCase() ?? ""
  );
  const title = issue.title.toLowerCase();

  if (
    labelNames.includes("bug") || title.startsWith("[bug]") ||
    sections["steps to reproduce"]
  ) {
    return "bug" as IssueKind;
  }

  if (
    labelNames.includes("enhancement") ||
    title.startsWith("[feature]") ||
    sections["proposed solution"]
  ) {
    return "feature" as IssueKind;
  }

  if (
    labelNames.includes("reward") ||
    title.startsWith("[reward]") ||
    sections["reward amount"]
  ) {
    return "reward" as IssueKind;
  }

  return "other" as IssueKind;
}

export function analyzeIssue(
  issue: GitHubIssue,
  comments: GitHubIssueComment[],
  now = new Date(),
): TriageResult {
  const { sections } = parseIssueBody(issue.body);
  const issueKind = detectIssueKind(issue, sections);
  const searchText = buildSearchText(issue, sections);
  const riskText = buildRiskText(issue, sections);
  const workflowText = buildWorkflowText(issue, sections);
  const agePolicy = calculateAgePolicy(issue.created_at, now);
  const reasons: string[] = [];
  const highRiskReasons: string[] = [];
  const missingFields = requiredFields(issueKind).filter((field) =>
    !hasMeaningfulSection(sections[field])
  );

  const matchedCoreKeywords = coreSurfaceKeywords(searchText);
  if (matchedCoreKeywords.length > 0) {
    reasons.push(
      `涉及 SkillHub 核心流程（${
        matchedCoreKeywords.slice(0, 3).join(", ")
      }） / Touches core SkillHub workflows (${
        matchedCoreKeywords.slice(0, 3).join(", ")
      }).`,
    );
  }

  const matchedUrgentKeywords = urgentKeywords(searchText);
  if (matchedUrgentKeywords.length > 0) {
    reasons.push(
      `包含紧急信号（${
        matchedUrgentKeywords.slice(0, 3).join(", ")
      }） / Contains urgency signals (${
        matchedUrgentKeywords.slice(0, 3).join(", ")
      }).`,
    );
  }

  const matchedCriticalWorkflowKeywords = criticalWorkflowKeywords(
    workflowText,
  );
  const requiresCoreMaintainer = matchedCriticalWorkflowKeywords.length > 0;
  if (matchedCriticalWorkflowKeywords.length > 0) {
    reasons.push(
      `阻塞已定义的用户主流程（${
        matchedCriticalWorkflowKeywords.slice(0, 3).join(", ")
      }） / Blocks a documented user workflow (${
        matchedCriticalWorkflowKeywords.slice(0, 3).join(", ")
      }).`,
    );
  }

  if (agePolicy.reason) {
    reasons.push(agePolicy.reason);
  }

  const matchedHighRiskKeywords = highRiskKeywords(riskText);
  if (matchedHighRiskKeywords.length > 0) {
    highRiskReasons.push(
      `提到敏感区域（${
        matchedHighRiskKeywords.slice(0, 3).join(", ")
      }） / Mentions sensitive areas (${
        matchedHighRiskKeywords.slice(0, 3).join(", ")
      }).`,
    );
  }

  if (
    hasMeaningfulSection(sections["api contract impact"]) ||
    hasMeaningfulSection(sections["contract or sdk impact"])
  ) {
    highRiskReasons.push(
      "提到 API、SDK 或契约变更 / Issue mentions API, SDK, or contract changes.",
    );
  }

  if (hasMeaningfulSection(sections["impact"])) {
    reasons.push(
      "包含用户或产品影响说明 / Issue includes operator or product impact details.",
    );
  }

  const impactBase = issueKind === "feature" ? 2 : 3;
  let impact = impactBase;
  impact += matchedCoreKeywords.length > 0 ? 1 : 0;
  impact += matchedCriticalWorkflowKeywords.length > 0 ? 1 : 0;
  impact += issue.comments >= 5 ? 1 : 0;
  impact += highRiskReasons.length > 0 ? 1 : 0;
  impact = clamp(impact, 1, 5);

  const urgencyBase = issueKind === "bug" ? 2 : 1;
  let urgency = urgencyBase;
  urgency += matchedUrgentKeywords.length > 0 ? 2 : 0;
  urgency += matchedCriticalWorkflowKeywords.length > 0 ? 1 : 0;
  urgency += highRiskReasons.length > 0 ? 1 : 0;
  urgency += issue.comments >= 3 ? 1 : 0;
  urgency = clamp(urgency, 1, 5);

  const matchedSmallFixKeywords = smallFixKeywords(searchText);
  let effort = issueKind === "feature" ? 4 : 3;
  if (matchedSmallFixKeywords.length > 0) {
    effort -= 2;
    reasons.push(
      `文本显示改动范围较可控（${
        matchedSmallFixKeywords.slice(0, 3).join(", ")
      }） / Text suggests a bounded change (${
        matchedSmallFixKeywords.slice(0, 3).join(", ")
      }).`,
    );
  }

  if (
    sections["api contract impact"] || sections["contract or sdk impact"] ||
    sections["impact"]
  ) {
    effort += 1;
  }

  if (highRiskReasons.length > 0) {
    effort += 1;
  }

  if (matchedCoreKeywords.length >= 2) {
    effort += 1;
  }

  effort = clamp(effort, 1, 5);

  const confidence = calculateConfidence(
    issueKind,
    issue.body ?? "",
    sections,
    missingFields,
  );

  const ageBoost = agePolicy.ageBoost;
  const engagementBoost = calculateEngagementBoost(
    issue.comments,
    sections["reward amount"],
  );
  const workflowPriorityBoost =
    issueKind === "bug" && matchedCriticalWorkflowKeywords.length > 0 ? 0.8 : 0;
  let priority = clamp(
    roundToOneDecimal(
      impact * 0.45 + urgency * 0.35 + ageBoost + engagementBoost +
        workflowPriorityBoost,
    ),
    1,
    5,
  );

  if (
    issueKind === "bug" && matchedCriticalWorkflowKeywords.length > 0 &&
    confidence >= 4
  ) {
    priority = Math.max(priority, 3.8);
  }

  if (agePolicy.priorityFloor > 0) {
    priority = Math.max(priority, agePolicy.priorityFloor);
  }

  if (missingFields.length > 0) {
    reasons.push(
      `缺少关键上下文（${
        missingFields.join(", ")
      }） / Issue is missing key context (${missingFields.join(", ")}).`,
    );
  }

  if (comments.length >= 3) {
    reasons.push(
      "已有后续讨论，积压压力在上升 / Thread already has follow-up discussion, so backlog pressure is rising.",
    );
  }

  const riskLevel = highRiskReasons.length > 0 ? "high" : "low";
  const route = determineRoute(
    priority,
    effort,
    confidence,
    riskLevel,
    missingFields,
    requiresCoreMaintainer,
  );
  const nextAction = describeNextAction(route, missingFields);

  const snapshot: TriageSnapshot = {
    route,
    riskLevel,
    requiresCoreMaintainer,
    openDays: agePolicy.openDays,
    impact,
    urgency,
    effort,
    confidence,
    priority,
    ageBoost: roundToOneDecimal(ageBoost),
    priorityFloor: agePolicy.priorityFloor,
    engagementBoost: roundToOneDecimal(engagementBoost),
    missingFields,
    reasons: uniqueNonEmpty(reasons).slice(0, 5),
    highRiskReasons,
    nextAction,
  };

  return {
    issue,
    issueKind,
    sections,
    mode: "rules-only",
    inputHash: "",
    rule: snapshot,
    handoffBrief: route === "core"
      ? buildMaintainerHandoffBrief({
        issue,
        issueKind,
        sections,
        mode: "rules-only",
        inputHash: "",
        rule: snapshot,
        ...snapshot,
      })
      : undefined,
    ...snapshot,
  };
}

export async function ensureManagedLabels(client: GitHubClient) {
  for (const definition of LABEL_DEFINITIONS) {
    await client.upsertLabel(definition);
  }
}

export async function syncManagedLabels(
  client: GitHubClient,
  issue: GitHubIssue,
  result: TriageResult,
) {
  const nextLabels = buildManagedLabels(issue, result);
  await client.replaceIssueLabels(issue.number, uniqueNonEmpty(nextLabels));
}

export async function upsertTriageComment(
  client: GitHubClient,
  issueNumber: number,
  result: TriageResult,
  comments: GitHubIssueComment[],
) {
  const preview = previewTriageMutation(result, comments);
  const existing = preview.existingComment;

  if (existing) {
    await client.updateIssueComment(existing.id, preview.commentBody);
    return;
  }

  await client.createIssueComment(issueNumber, preview.commentBody);
}

export function renderTriageComment(result: TriageResult) {
  const handoffBrief = result.route === "core"
    ? buildMaintainerHandoffBrief(result)
    : undefined;
  const englishLines = buildRenderedLanguageBlock(result, handoffBrief, "en");
  const chineseLines = buildRenderedLanguageBlock(result, handoffBrief, "zh");

  return [
    ...englishLines,
    "",
    "---",
    "",
    ...chineseLines,
    "",
    renderMachineState(result),
  ].join("\n");
}

function buildRenderedLanguageBlock(
  result: TriageResult,
  handoffBrief: ReturnType<typeof buildMaintainerHandoffBrief>,
  language: "en" | "zh",
) {
  const isEnglish = language === "en";
  const lines = [
    isEnglish ? "## Issue Triage" : "## 问题分流结果",
    "",
    isEnglish
      ? `- Route: \`${routeLabel(result.route)}\``
      : `- 路由: \`${routeLabel(result.route)}\``,
    isEnglish
      ? `- Priority: \`${priorityLabel(result.priority)}\` (${
        result.priority.toFixed(1)
      }/5)`
      : `- 优先级: \`${priorityLabel(result.priority)}\` (${
        result.priority.toFixed(1)
      }/5)`,
    isEnglish
      ? `- Effort: \`${effortLabel(result.effort)}\` (${result.effort}/5)`
      : `- 修复投入: \`${effortLabel(result.effort)}\` (${result.effort}/5)`,
    isEnglish
      ? `- Confidence: \`${result.confidence}/5\``
      : `- 信息完整度: \`${result.confidence}/5\``,
    isEnglish
      ? `- Risk: \`${renderRiskLevel(language, result.riskLevel)}\``
      : `- 风险: \`${renderRiskLevel(language, result.riskLevel)}\``,
    isEnglish
      ? `- Analysis Mode: \`${result.mode}\``
      : `- 分析模式: \`${result.mode}\``,
    "",
    isEnglish ? "### Why" : "### 原因",
    ...result.reasons.map((reason) =>
      `- ${renderBilingualText(reason, language)}`
    ),
  ];

  appendLlmSection(lines, result, language);

  if (result.highRiskReasons.length > 0) {
    lines.push(
      "",
      isEnglish ? "### High-Risk Signals" : "### 高风险信号",
      ...result.highRiskReasons.map((reason) =>
        `- ${renderBilingualText(reason, language)}`
      ),
    );
  }

  appendHandoffBriefSection(lines, result, handoffBrief, language);

  if (result.missingFields.length > 0) {
    lines.push(
      "",
      isEnglish ? "### Missing Info" : "### 缺失信息",
      ...result.missingFields.map((field) =>
        isEnglish ? `- Please add \`${field}\`.` : `- 请补充 \`${field}\`.`
      ),
    );
  }

  lines.push(
    "",
    isEnglish ? "### Next Action" : "### 下一步",
    `- ${renderBilingualText(result.nextAction, language)}`,
  );

  return lines;
}

function appendLlmSection(
  lines: string[],
  result: TriageResult,
  language: "en" | "zh",
) {
  if (!result.llm) {
    return;
  }

  const isEnglish = language === "en";
  lines.push("", isEnglish ? "### LLM Assist" : "### AI 辅助");
  lines.push(
    isEnglish
      ? `- Provider: \`${result.llm.provider}\``
      : `- 服务商: \`${result.llm.provider}\``,
  );
  lines.push(
    isEnglish
      ? `- Model: \`${result.llm.model}\``
      : `- 模型: \`${result.llm.model}\``,
  );
  lines.push(
    isEnglish
      ? `- Mode: \`${result.llm.mode}\``
      : `- 模式: \`${result.llm.mode}\``,
  );

  if (result.llm.failed) {
    lines.push(
      isEnglish
        ? `- Status: fallback to rules-only (${
          result.llm.failureReason ?? "unknown error"
        })`
        : `- 状态: 回退到规则 (${result.llm.failureReason ?? "unknown error"})`,
    );
    return;
  }

  lines.push(
    isEnglish
      ? `- Status: ${
        result.llm.reused ? "reused cached assessment" : "fresh assessment"
      }`
      : `- 状态: ${result.llm.reused ? "复用缓存评估" : "新鲜评估"}`,
  );

  const llmSummary = resolveLlmSummary(result, language);
  if (llmSummary) {
    lines.push(
      isEnglish ? `- Summary: ${llmSummary}` : `- 摘要: ${llmSummary}`,
    );
  }

  if (result.llm.suggestedQuestions.length > 0) {
    lines.push(
      ...result.llm.suggestedQuestions.map((question) =>
        isEnglish
          ? `- Suggested question: ${renderBilingualText(question, language)}`
          : `- 建议追问: ${renderBilingualText(question, language)}`
      ),
    );
  }

  if (
    result.mode === "llm-shadow" &&
    result.llm.recommendedRoute !== result.rule.route
  ) {
    lines.push(
      isEnglish
        ? `- LLM suggested \`${
          routeLabel(result.llm.recommendedRoute)
        }\`, but labels remain on the rule-only route.`
        : `- LLM 建议路由为 \`${
          routeLabel(result.llm.recommendedRoute)
        }\`，但当前仍保持规则路由标签。`,
    );
  }

  if (result.mode === "llm-assist" && result.route !== result.rule.route) {
    lines.push(
      isEnglish
        ? `- Rule-only route was \`${
          routeLabel(result.rule.route)
        }\`; final route after bounded LLM merge is \`${
          routeLabel(result.route)
        }\`.`
        : `- 纯规则路由为 \`${
          routeLabel(result.rule.route)
        }\`；经过受限 LLM 合并后最终路由为 \`${routeLabel(result.route)}\`。`,
    );
  }
}

function appendHandoffBriefSection(
  lines: string[],
  result: TriageResult,
  handoffBrief: ReturnType<typeof buildMaintainerHandoffBrief>,
  language: "en" | "zh",
) {
  if (!handoffBrief) {
    return;
  }

  const isEnglish = language === "en";
  lines.push("", isEnglish ? "### Maintainer Brief" : "### 维护者交接摘要");
  lines.push(
    isEnglish
      ? `Summary: ${
        resolveBriefSummary(result, handoffBrief.summary, language)
      }`
      : `概要: ${resolveBriefSummary(result, handoffBrief.summary, language)}`,
  );
  lines.push(isEnglish ? "Why core:" : "为何进入 core:");
  lines.push(
    ...handoffBrief.whyCore.map((item) =>
      `- ${renderBilingualText(item, language)}`
    ),
  );
  lines.push(
    isEnglish ? "Reproduction or operator path:" : "复现路径或操作路径:",
  );
  lines.push(
    ...handoffBrief.reproduction.map((item) =>
      `- ${renderBilingualText(item, language)}`
    ),
  );
  lines.push(isEnglish ? "Suspected areas:" : "怀疑影响区域:");
  lines.push(
    ...handoffBrief.suspectedAreas.map((item) =>
      `- ${renderBilingualText(item, language)}`
    ),
  );
  lines.push(isEnglish ? "Risks to watch:" : "重点风险:");
  lines.push(
    ...handoffBrief.risks.map((item) =>
      `- ${renderBilingualText(item, language)}`
    ),
  );
  lines.push(isEnglish ? "Validation checklist:" : "验证清单:");
  lines.push(
    ...handoffBrief.validation.map((item) =>
      `- ${renderBilingualText(item, language)}`
    ),
  );
}

function resolveBriefSummary(
  result: TriageResult,
  fallbackSummary: string,
  language: "en" | "zh",
) {
  const llmSummary = resolveLlmSummary(result, language);

  if (llmSummary) {
    return llmSummary;
  }

  return renderBilingualText(fallbackSummary, language);
}

function resolveLlmSummary(result: TriageResult, language: "en" | "zh") {
  if (!result.llm || result.llm.failed) {
    return "";
  }

  if (language === "en") {
    return result.llm.summaryEn?.trim() || result.llm.summary?.trim() || "";
  }

  return result.llm.summaryZh?.trim() || result.llm.summary?.trim() || "";
}

function renderBilingualText(text: string, language: "en" | "zh") {
  const split = splitBilingualText(text);

  if (!split) {
    return text;
  }

  return language === "en" ? split.en : split.zh;
}

function splitBilingualText(text: string) {
  const separator = " / ";
  const separatorIndex = text.indexOf(separator);

  if (separatorIndex < 0) {
    return null;
  }

  const left = text.slice(0, separatorIndex).trim();
  const right = text.slice(separatorIndex + separator.length).trim();

  if (!left || !right) {
    return null;
  }

  if (containsCjk(left) && !containsCjk(right)) {
    return { zh: left, en: right };
  }

  if (!containsCjk(left) && containsCjk(right)) {
    return { en: left, zh: right };
  }

  return { zh: left, en: right };
}

function containsCjk(value: string) {
  return /[\u3400-\u9fff]/.test(value);
}

function renderRiskLevel(
  language: "en" | "zh",
  riskLevel: TriageResult["riskLevel"],
) {
  if (language === "en") {
    return riskLevel;
  }

  return riskLevel === "high" ? "高" : "低";
}

function renderMachineState(result: TriageResult) {
  const payload = JSON.stringify(
    {
      version: 2,
      issue: result.issue.number,
      inputHash: result.inputHash,
      mode: result.mode,
      route: result.route,
      priority: result.priority,
      openDays: result.openDays,
      requiresCoreMaintainer: result.requiresCoreMaintainer,
      impact: result.impact,
      urgency: result.urgency,
      effort: result.effort,
      confidence: result.confidence,
      riskLevel: result.riskLevel,
      ageBoost: result.ageBoost,
      priorityFloor: result.priorityFloor,
      engagementBoost: result.engagementBoost,
      missingFields: result.missingFields,
      updatedAt: new Date().toISOString(),
      llm: result.llm,
    },
    null,
    2,
  );

  return `${TRIAGE_COMMENT_MARKER}\n${payload}\n-->`;
}

function calculateConfidence(
  issueKind: IssueKind,
  rawBody: string,
  sections: Record<string, string>,
  missingFields: string[],
) {
  const required = requiredFields(issueKind);
  const requiredFilled =
    required.filter((field) => hasMeaningfulSection(sections[field])).length;
  const supportFields = Object.entries(sections).filter(
    ([key, value]) => !required.includes(key) && hasMeaningfulSection(value),
  ).length;

  let score = 1;
  score += requiredFilled;
  score += supportFields >= 1 ? 0.5 : 0;
  score += supportFields >= 3 ? 0.5 : 0;
  score += rawBody.length >= 400 ? 0.5 : 0;
  score -= missingFields.length > 0 ? 1 : 0;

  return clamp(Math.round(score), 1, 5);
}

function calculateAgePolicy(createdAt: string, now: Date) {
  const created = new Date(createdAt);
  const openDays = Math.floor(
    (now.getTime() - created.getTime()) / (24 * 60 * 60 * 1000),
  );
  const safeOpenDays = Math.max(0, openDays);

  if (safeOpenDays >= 14) {
    return {
      openDays: safeOpenDays,
      ageBoost: 1.5,
      priorityFloor: 4.4,
      reason:
        `已打开 ${safeOpenDays} 天，超过 14 天闭环 SLA，优先级强制提升到 P0 / Open for ${safeOpenDays} days; the 14-day closure SLA is breached, so priority is forced to P0.`,
    };
  }

  if (safeOpenDays >= 10) {
    return {
      openDays: safeOpenDays,
      ageBoost: 1,
      priorityFloor: 3.6,
      reason:
        `已打开 ${safeOpenDays} 天，为避免超过 14 天仍未闭环，强制进入 active lane / Open for ${safeOpenDays} days; forced into an active lane before the 14-day closure SLA is missed.`,
    };
  }

  if (safeOpenDays >= 7) {
    return {
      openDays: safeOpenDays,
      ageBoost: 0.6,
      priorityFloor: 2.6,
      reason:
        `已打开 ${safeOpenDays} 天，开始进入 2 周闭环预热窗口 / Open for ${safeOpenDays} days; entering the 2-week closure warm-up window.`,
    };
  }

  return {
    openDays: safeOpenDays,
    ageBoost: 0,
    priorityFloor: 0,
    reason: "",
  };
}

function calculateEngagementBoost(
  commentCount: number,
  rewardAmountText?: string,
) {
  let boost = Math.min(0.8, commentCount * 0.1);
  const rewardAmount = Number.parseFloat(
    (rewardAmountText ?? "").replaceAll(/[^0-9.]/g, ""),
  );

  if (!Number.isNaN(rewardAmount)) {
    if (rewardAmount >= 500) {
      boost += 0.6;
    } else if (rewardAmount >= 100) {
      boost += 0.3;
    } else if (rewardAmount > 0) {
      boost += 0.1;
    }
  }

  return Math.min(1, boost);
}

function requiredFields(issueKind: IssueKind) {
  return REQUIRED_SECTIONS[issueKind] ?? [];
}

function buildSearchText(issue: GitHubIssue, sections: Record<string, string>) {
  return [issue.title, issue.body ?? "", ...Object.values(sections)].join("\n")
    .toLowerCase();
}

function buildRiskText(issue: GitHubIssue, sections: Record<string, string>) {
  const preferredSections = [
    "summary",
    "problem",
    "proposed solution",
    "expected behavior",
    "steps to reproduce",
    "impact",
    "api contract impact",
    "contract or sdk impact",
  ];

  return [
    issue.title,
    ...preferredSections.map((section) => sections[section] ?? ""),
  ]
    .join("\n")
    .toLowerCase();
}

function buildWorkflowText(
  issue: GitHubIssue,
  sections: Record<string, string>,
) {
  const preferredSections = [
    "summary",
    "problem",
    "steps to reproduce",
    "expected behavior",
    "impact",
  ];

  return [
    issue.title,
    ...preferredSections.map((section) => sections[section] ?? ""),
  ]
    .join("\n")
    .toLowerCase();
}

function normalizeHeading(value: string) {
  return value.trim().toLowerCase();
}

function cleanupSectionContent(value: string) {
  return value
    .replaceAll(/^_No response_\s*$/gim, "")
    .replaceAll(/^no response\s*$/gim, "")
    .trim();
}

function hasMeaningfulSection(value: string | undefined) {
  return Boolean(value && cleanupSectionContent(value).length >= 3);
}

function uniqueNonEmpty(values: string[]) {
  return [...new Set(values.filter((value) => value.trim().length > 0))];
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

function roundToOneDecimal(value: number) {
  return Math.round(value * 10) / 10;
}

export function findTriageComment(comments: GitHubIssueComment[]) {
  return comments.find((comment) =>
    comment.body.includes(TRIAGE_COMMENT_MARKER)
  );
}

export function buildManagedLabels(issue: GitHubIssue, result: TriageResult) {
  const existingLabels = issue.labels
    .map((label) => label.name)
    .filter((label): label is string => Boolean(label));

  const unmanagedLabels = existingLabels.filter(
    (label) =>
      !MANAGED_LABEL_PREFIXES.some((prefix) => label.startsWith(prefix)),
  );

  return [
    ...unmanagedLabels,
    routeLabel(result.route),
    priorityLabel(result.priority),
    effortLabel(result.effort),
    ...riskLabels(result.riskLevel),
  ];
}

export function previewTriageMutation(
  result: TriageResult,
  comments: GitHubIssueComment[],
) {
  return {
    labels: uniqueNonEmpty(buildManagedLabels(result.issue, result)),
    commentBody: renderTriageComment(result),
    existingComment: findTriageComment(comments) ?? null,
  };
}

export function parseTriageMachineState(
  commentBody: string,
): TriageMachineState | null {
  const start = commentBody.indexOf(TRIAGE_COMMENT_MARKER);

  if (start < 0) {
    return null;
  }

  const jsonStart = start + TRIAGE_COMMENT_MARKER.length;
  const end = commentBody.indexOf("-->", jsonStart);

  if (end < 0) {
    return null;
  }

  const rawJson = commentBody.slice(jsonStart, end).trim();

  try {
    const parsed = JSON.parse(rawJson) as TriageMachineState;

    if (
      typeof parsed !== "object" ||
      parsed === null ||
      typeof parsed.issue !== "number" ||
      typeof parsed.route !== "string"
    ) {
      return null;
    }

    return parsed;
  } catch {
    return null;
  }
}
