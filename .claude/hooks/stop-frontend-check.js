#!/usr/bin/env node
// Stop hook: 前端代码检查（TypeScript + ESLint）

const { execSync } = require('child_process');
const fs = require('fs');

const input = fs.readFileSync(0, 'utf-8');
const data = JSON.parse(input);

if (data.stop_hook_active === true) process.exit(0);

let changed = '';
try {
  changed = execSync('git diff --name-only HEAD~1 HEAD 2>/dev/null', { encoding: 'utf-8' });
} catch (e) {
  process.exit(0);
}

if (changed.includes('web/src/')) {
  try {
    execSync('cd web && pnpm exec tsc --noEmit 2>&1 && pnpm exec eslint src --max-warnings 0 2>&1', { stdio: 'inherit' });
  } catch (e) {
    console.log(JSON.stringify({
      decision: 'block',
      reason: '[HARNESS] TypeScript 或 ESLint 检查失败，请修复后再完成任务。运行 make typecheck-web 和 make lint-web 查看详情。'
    }));
    process.exit(0);
  }
}

if (changed.includes('server/')) {
  console.error('[HARNESS] 检测到后端文件变更，建议运行 make test-backend-app 验证测试通过。');
}
