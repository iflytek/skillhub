import Schema from '@deepseek-ai/schemastery';

export const Config = Schema.object({
  registryUrl: Schema.string().default('https://skill.xfyun.cn').description('SkillHub Registry base URL; defaults to https://skill.xfyun.cn; an explicit empty value disconnects the source.'),
  detailTabs: Schema.object({
    versions: Schema.union(['auto', 'show', 'hide']).default('auto'),
    securityAudit: Schema.union(['auto', 'show', 'hide']).default('auto'),
    reviews: Schema.union(['auto', 'show', 'hide']).default('auto'),
  }).default({}),
}).default({}).volatile();
