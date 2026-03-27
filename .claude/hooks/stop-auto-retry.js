#!/usr/bin/env node
// Stop hook: 任务异常自动重试

const fs = require('fs');
const path = require('path');

const input = fs.readFileSync(0, 'utf-8');
const data = JSON.parse(input);

if (data.stop_hook_active === true) process.exit(0);

const msg = (data.last_assistant_message || '').toLowerCase();
const retryFile = path.join(require('os').tmpdir(), 'skillhub-claude-retry-count');

const errorPatterns = [
  /(error|exception) occurred/,
  /task (failed|could not|cannot be completed)/,
  /(was|got) interrupted/,
  /timed? out waiting/,
  /connection (lost|dropped|timed? out)/,
  /i (could not|was unable to|am unable to) (complete|finish|continue)/
];

if (errorPatterns.some(pattern => pattern.test(msg))) {
  let count = 0;
  try {
    count = parseInt(fs.readFileSync(retryFile, 'utf-8'), 10) || 0;
  } catch (e) {}

  if (count < 3) {
    fs.writeFileSync(retryFile, String(count + 1));
    console.log(JSON.stringify({
      decision: 'block',
      reason: `[HARNESS] 检测到任务异常停止，自动重试（第 ${count + 1}/3 次）。`
    }));
  } else {
    try { fs.unlinkSync(retryFile); } catch (e) {}
    console.error('[HARNESS] 已重试 3 次仍失败，请手动检查任务状态。');
  }
} else {
  try { fs.unlinkSync(retryFile); } catch (e) {}
}
