import Schema from '@deepseek-ai/schemastery';

export const Config = Schema.object({
  registryUrl: Schema.string().default('').description('SkillHub Registry base URL; empty leaves the source unconfigured.'),
  detailTabs: Schema.object({
    versions: Schema.union(['auto', 'show', 'hide']).default('auto'),
    securityAudit: Schema.union(['auto', 'show', 'hide']).default('auto'),
    reviews: Schema.union(['auto', 'show', 'hide']).default('auto'),
  }).default({}),
}).default({}).volatile();
