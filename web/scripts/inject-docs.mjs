#!/usr/bin/env node

import { readFileSync, writeFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { marked } from 'marked';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Paths
const projectRoot = join(__dirname, '../..');
const docPath = join(projectRoot, 'docs/openclaw-integration-en.md');
const distIndexPath = join(__dirname, '../dist/index.html');

console.log('📄 Reading markdown document...');
const markdownContent = readFileSync(docPath, 'utf-8');

console.log('🔄 Converting markdown to HTML...');
const htmlContent = marked.parse(markdownContent);

console.log('📝 Reading dist/index.html...');
const indexHtml = readFileSync(distIndexPath, 'utf-8');

// Create the hidden SEO container
const seoContainer = `
  <!-- SEO: Hidden documentation for web crawlers -->
  <div id="seo-docs" style="position:absolute;left:-9999px;top:-9999px;overflow:hidden;width:1px;height:1px;" aria-hidden="true">
    ${htmlContent}
  </div>
`;

// Inject before <div id="root">
const injectedHtml = indexHtml.replace(
  /<div id="root">/,
  `${seoContainer}\n    <div id="root">`
);

console.log('💾 Writing updated index.html...');
writeFileSync(distIndexPath, injectedHtml, 'utf-8');

console.log('✅ Documentation injected successfully!');
console.log(`   - Source: ${docPath}`);
console.log(`   - Target: ${distIndexPath}`);
console.log(`   - Content size: ~${Math.round(htmlContent.length / 1024)}KB`);
