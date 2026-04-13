# Install Namespace Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add namespace/skill search selection to install command when no namespace is specified, reusing resolve command's interactive search flow.

**Architecture:** Modify `installFromRegistry` to check if namespace is "global" (unspecified), then invoke `searchSkills` + `runInteractiveSearch` before version selection. Reuse existing version selection and install flow.

**Tech Stack:** TypeScript, @clack/prompts, ApiClient

---

## File: `skillhub-cli/src/commands/install.ts`

### Task 1: Import search functions

**Files:**
- Modify: `skillhub-cli/src/commands/install.ts:1-25`

- [ ] **Step 1: Add imports for search functions**

Add to the import section (around line 6-7):

```typescript
import { runInteractiveSearch, searchSkills } from "../core/interactive-search.js";
```

- [ ] **Step 2: Run build to verify**

Run: `cd skillhub-cli && npm run build 2>&1 | tail -20`
Expected: Build succeeds without new errors

- [ ] **Step 3: Commit**

```bash
git add skillhub-cli/src/commands/install.ts
git commit -m "feat(install): import search functions for namespace selection"
```

---

### Task 2: Add namespace/skill search logic before version selection

**Files:**
- Modify: `skillhub-cli/src/commands/install.ts:177-190` (after config/client setup, before version fetch)

- [ ] **Step 1: Read current code structure around line 177**

Read lines 175-220 to understand the current structure.

- [ ] **Step 2: Add namespace/skill search block**

Replace the comment `spinner.text = \`Fetching ${ns}/${actualSlug}\`` and the following lines (181-189) with:

```typescript
  // When namespace is not specified (default "global"), search and select namespace/skill
  if (ns === "global") {
    const results = await searchSkills(client, actualSlug, 50);

    // Deduplicate by namespace/name
    const seen = new Set<string>();
    const uniqueResults = results.filter(r => {
      const key = `${r.namespace}/${r.name}`;
      if (!seen.has(key)) {
        seen.add(key);
        return true;
      }
      return false;
    });

    if (uniqueResults.length === 0) {
      spinner.fail(`Skill not found: ${actualSlug}`);
      process.exit(1);
    }

    if (uniqueResults.length === 1) {
      ns = uniqueResults[0].namespace;
      actualSlug = uniqueResults[0].name;
    } else {
      const selected = await runInteractiveSearch(client, actualSlug);
      if (!selected) {
        console.log("Cancelled.");
        return;
      }
      const [selectedNs, selectedName] = selected.split("/", 2);
      ns = selectedNs;
      actualSlug = selectedName;
    }
  }

  spinner.text = `Fetching ${ns}/${actualSlug}`;
```

- [ ] **Step 3: Run type check**

Run: `cd skillhub-cli && npx tsc --noEmit 2>&1 | grep install.ts`
Expected: No new errors (only pre-existing uninstall.ts errors if any)

- [ ] **Step 4: Build**

Run: `cd skillhub-cli && npm run build 2>&1 | tail -10`
Expected: Build succeeds

- [ ] **Step 5: Commit**

```bash
git add skillhub-cli/src/commands/install.ts
git commit -m "feat(install): add namespace/skill search selection for unspecified namespace"
```

---

### Task 3: Update spinner text after namespace resolution

**Files:**
- Modify: `skillhub-cli/src/commands/install.ts` (the spinner.text line after search logic)

- [ ] **Step 1: Verify spinner text uses resolved namespace/slug**

The new code already sets `spinner.text = \`Fetching ${ns}/${actualSlug}\`` after the namespace resolution block. This ensures the spinner shows the correct resolved namespace.

No additional changes needed if Task 2 was completed correctly.

- [ ] **Step 2: Build and verify**

Run: `cd skillhub-cli && npm run build 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git commit -m "chore(install): update spinner text after namespace resolution"
```

---

### Task 4: Test the full flow

**Files:**
- No file changes (testing only)

- [ ] **Step 1: Test with specified namespace (should skip search)**

Run: `cd skillhub-cli && node dist/cli.mjs install global/openspec`
Expected: Shows version selection directly (after spinner fetch)

- [ ] **Step 2: Test with unspecified namespace (if a skill exists in multiple namespaces)**

Run: `cd skillhub-cli && node dist/cli.mjs install openspec`
Expected: Shows interactive search if multiple matches found, or skips to version if only one match

- [ ] **Step 3: Test cancel behavior**

Run search and press Escape
Expected: Prints "Cancelled." and exits

---

## Summary

| Task | Description | Files Modified |
|------|-------------|----------------|
| 1 | Import search functions | install.ts |
| 2 | Add namespace/skill search logic | install.ts |
| 3 | Verify spinner text update | install.ts |
| 4 | Test the flow | - |

**Spec Coverage:**
- [x] Namespace/skill search selection when unspecified - Task 2
- [x] Skip search when namespace specified - Task 2 (conditional `if (ns === "global")`)
- [x] Deduplicate search results - Task 2
- [x] Version selection after namespace resolution - Existing code preserved
- [x] Original install flow preserved - No changes to steps 3-8
- [x] Cancel handling - Task 2
