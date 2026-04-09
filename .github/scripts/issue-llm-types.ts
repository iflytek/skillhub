import { IssueRoute } from "./issue-triage-types.ts";

export interface IssueLlmConfig {
  mode: "off" | "shadow" | "assist";
  provider: "openai-compatible";
  baseUrl: string;
  apiKey: string;
  model: string;
  timeoutMs: number;
  maxAttempts: number;
  retryBackoffMs: number;
  temperature: number;
  maxComments: number;
  maxCommentChars: number;
  maxBodyChars: number;
}

export interface IssueLlmPayload {
  issueNumber: number;
  issueUrl: string;
  issueTitle: string;
  issueKind: string;
  labels: string[];
  author: string;
  createdAt: string;
  updatedAt: string;
  commentsCount: number;
  issueBody: string;
  sections: Record<string, string>;
  latestComments: Array<{
    author: string;
    createdAt: string;
    body: string;
  }>;
  ruleEvaluation: {
    route: IssueRoute;
    impact: number;
    urgency: number;
    effort: number;
    confidence: number;
    priority: number;
    riskLevel: string;
    missingFields: string[];
    reasons: string[];
    highRiskReasons: string[];
  };
}

export interface IssueLlmResponse {
  summary: string;
  summary_en?: string;
  summary_zh?: string;
  impact: number;
  urgency: number;
  effort: number;
  confidence: number;
  risk_flags: string[];
  missing_info: string[];
  suggested_questions: string[];
  recommended_route: IssueRoute;
  rationale: string[];
}
