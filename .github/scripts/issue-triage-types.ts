import { GitHubIssue } from "./github.ts";

export type IssueKind = "bug" | "feature" | "reward" | "other";
export type IssueRoute = "needs-info" | "deferred" | "core" | "agent-ready";
export type RiskLevel = "low" | "high";
export type LlmMode = "off" | "shadow" | "assist";
export type AnalysisMode = "rules-only" | "llm-shadow" | "llm-assist";

export interface ParsedIssueBody {
  sections: Record<string, string>;
  missingFields: string[];
}

export interface TriageSnapshot {
  route: IssueRoute;
  riskLevel: RiskLevel;
  requiresCoreMaintainer: boolean;
  openDays: number;
  impact: number;
  urgency: number;
  effort: number;
  confidence: number;
  priority: number;
  ageBoost: number;
  priorityFloor: number;
  engagementBoost: number;
  missingFields: string[];
  reasons: string[];
  highRiskReasons: string[];
  nextAction: string;
}

export interface MaintainerHandoffBrief {
  summary: string;
  whyCore: string[];
  reproduction: string[];
  suspectedAreas: string[];
  risks: string[];
  validation: string[];
}

export interface LlmAssessment {
  provider: string;
  model: string;
  mode: LlmMode;
  inputHash: string;
  summary: string;
  summaryEn?: string;
  summaryZh?: string;
  impact: number;
  urgency: number;
  effort: number;
  confidence: number;
  riskFlags: string[];
  missingInfo: string[];
  suggestedQuestions: string[];
  recommendedRoute: IssueRoute;
  rationale: string[];
  reused: boolean;
  failed: boolean;
  failureReason?: string;
}

export interface TriageResult extends TriageSnapshot {
  issue: GitHubIssue;
  issueKind: IssueKind;
  sections: Record<string, string>;
  mode: AnalysisMode;
  inputHash: string;
  rule: TriageSnapshot;
  llm?: LlmAssessment;
  handoffBrief?: MaintainerHandoffBrief;
}

export interface TriageMachineState {
  version: number;
  issue: number;
  inputHash?: string;
  mode?: AnalysisMode;
  route: IssueRoute;
  priority: number;
  requiresCoreMaintainer?: boolean;
  impact: number;
  urgency: number;
  effort: number;
  confidence: number;
  riskLevel: RiskLevel;
  ageBoost: number;
  engagementBoost: number;
  missingFields: string[];
  updatedAt: string;
  llm?: LlmAssessment;
}
