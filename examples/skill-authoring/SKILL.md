---
name: text-stats
description: computes word, line, and character statistics for a text file and reports them as JSON
---

# text-stats

## Overview

computes word, line, and character statistics for a text file and reports them as JSON. Agents use this skill whenever they need reproducible, machine-readable metrics about a piece of text — for example to compare a document before and after rewriting it.

## Usage

1. Locate the text file to analyze. Any plain-text file in the workspace works; `references/sample.txt` is a ready-made fixture.
2. Run the helper with the file path as the only argument:

   ```sh
   sh scripts/text-stats.sh references/sample.txt
   ```

3. Read the single-line JSON object from stdout:

   ```json
   {"input":"references/sample.txt","lines":2,"words":20,"characters":144}
   ```

4. The same object is mirrored to `artifacts/report.json` when a persistent copy is needed.

## Resources

- `references/metrics.md` — how each counter is defined
- `references/sample.txt` — sample input used by the validation cases
- `scripts/` — executable helpers invoked during task execution
