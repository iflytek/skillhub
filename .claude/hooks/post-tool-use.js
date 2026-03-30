#!/usr/bin/env node
// PostToolUse hook: 检测 Controller 变更，提醒更新 OpenAPI 类型

const input = require('fs').readFileSync(0, 'utf-8');
const data = JSON.parse(input);
const file = data.tool_input?.file_path || data.tool_input?.path;

if (!file) process.exit(0);

// 检测 Controller 文件变更
if (/server\/skillhub-app\/src\/main\/java\/.*\/controller\/.*\.java$/.test(file)) {
  console.error(`[HARNESS] 检测到 Controller 文件变更: ${file}。请运行 make generate-api 更新 OpenAPI 类型，并将 web/src/api/generated/schema.d.ts 加入本次提交。`);
}
