#!/usr/bin/env node
// PreToolUse hook: 阻止编辑自动生成文件和已有 migration

const input = require('fs').readFileSync(0, 'utf-8');
const data = JSON.parse(input);
const file = data.tool_input?.file_path || data.tool_input?.path;

if (!file) process.exit(0);

// 禁止手动编辑 schema.d.ts
if (file.includes('web/src/api/generated/schema.d.ts')) {
  console.error('[HARNESS] 禁止手动编辑 schema.d.ts，该文件由 make generate-api 自动生成。');
  process.exit(1);
}

// 禁止修改已有 Flyway migration
if (/server\/skillhub-app\/src\/main\/resources\/db\/migration\/V\d+__.*\.sql$/.test(file)) {
  if (require('fs').existsSync(file)) {
    console.error(`[HARNESS] 禁止修改已有 Flyway migration 文件: ${file}。请新建更高版本号的文件。`);
    process.exit(1);
  }
}
